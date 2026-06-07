package es.jvbabi.authentikt.samples

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.config.OAuthAccessToken
import es.jvbabi.authentikt.core.config.OAuthAuthorizationResult
import es.jvbabi.authentikt.core.config.OAuthDeviceFlowAuthorizationResult
import es.jvbabi.authentikt.core.installAuthentikt
import es.jvbabi.authentikt.core.session.SessionDestination
import es.jvbabi.authentikt.core.step.plugins.builtin.*
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class User(
    val email: String,
    val displayName: String,
    val username: String,
    val password: String,
    val otpSecret: String?,
)

val users = listOf(
    User(
        email = "admin@acme.com",
        displayName = "Admin",
        username = "admin",
        password = "password",
        otpSecret = "X3V3UD62XVWDX7GH",
    ),

    User(
        email = "eric.smith@acme.com",
        displayName = "Eric Smith",
        username = "eric.smith",
        password = "password",
        otpSecret = null,
    )
)

fun User.toAuthentiktUser() = object : AuthentiktUser<User>(this) {
    override suspend fun getEmail(): String = email
    override suspend fun getUsername(): String = username
    override suspend fun getDisplayName(): String = displayName
}

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    install(CORS) {
        anyHost()
        anyMethod()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Cookie)
    }

    install(DefaultHeaders) {
        header("Access-Control-Allow-Credentials", "true")
    }

    val passwordPlugin = PasswordPlugin<User> {
        checkPassword { user, password -> user.password == password }
    }

    val emailUserSelectionPlugin = EmailUserSelectionPlugin {
        withUsername = true
        findUserByEmail { email -> users.find { it.email == email || it.username == email }?.toAuthentiktUser() }
    }

    val oauthPlugin = OIDCPlugin {
        clientId = "authentikt"
        clientSecret = "HaUO49nsYZgT8hjWFE0qwnYw5ZK8IrYp"
        authorizationEndpoint = "https://keycloak.werkbank.studio/realms/authentikt-lib/protocol/openid-connect/auth"
        tokenEndpoint = "https://keycloak.werkbank.studio/realms/authentikt-lib/protocol/openid-connect/token"
        userInfoEndpoint = "https://keycloak.werkbank.studio/realms/authentikt-lib/protocol/openid-connect/userinfo"

        onUserInfo { response, accessToken ->
            val fields = response.body<Map<String, String>>()
            val email = fields["email"]
            val user = users.find { it.email == email }
            if (user != null) return@onUserInfo UserInfo.Result.Success(user.toAuthentiktUser())
            return@onUserInfo UserInfo.Result.Failure("User with email $email not found")
        }

        scopes("openid", "profile", "email")
    }

    val donePlugin = DonePlugin<User> {
        onSuccess { session, user ->
            if (session.destination is SessionDestination.DeviceFlow) return@onSuccess
            cookie(
                name = "SessionToken",
                value = "token-for-${user.email}",
                validFor = 60.days
            )

            redirect("vpp2://google.com/search?q=welcome+${user.displayName.replace(" ", "+")}")
        }

        onOAuthSuccess { session, user ->
            return@onOAuthSuccess OAuthAccessToken(
                "token-for-${user.email}",
                null,
                7.days,
            )
        }
    }

    val totpClock = object : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(1776442771)
    }

    // correct otp = 286133
    val totpPlugin = TotpPlugin<User> {
        clock = totpClock
        getSecret { user -> user.otpSecret!! }
    }

    val instance = installAuthentikt {
        baseUrl = "https://authentikt-lib.werkbank.space"
        apiPrefix = "/api/"
        uiLoginBaseUrl = "https://authentikt-lib.werkbank.space/"
        customSslCert("/Users/julius/.werkbank/certificates/rootCa.crt")

        install(emailUserSelectionPlugin)
        install(oauthPlugin)

        install(passwordPlugin)
        install(totpPlugin)
        install(donePlugin)

        oauth {
            onAuthorize { clientId, redirectUri ->
                OAuthAuthorizationResult.Application(clientId, redirectUri, "Authentikt TV App")
            }

            onDeviceFlow { clientId ->
                if(clientId != "authentikt-tv-app") return@onDeviceFlow OAuthDeviceFlowAuthorizationResult.Error("Invalid client id")
                OAuthDeviceFlowAuthorizationResult.Application(
                    clientId,
                    "Authentikt TV App",
                    Uuid.random().toString(),
                    generateUserCode()
                )
            }
        }

        val testOauth = false

        authorization { session ->
            val user = session.identifiedUser
             if (!session.has(oauthPlugin) && testOauth) {
                if (!session.has(passwordPlugin)) return@authorization passwordPlugin
                if (user != null && !session.has(totpPlugin) && user.user.otpSecret != null) return@authorization totpPlugin
            } else {
                if (user == null) return@authorization emailUserSelectionPlugin
                else if (!session.has(passwordPlugin)) return@authorization passwordPlugin
                else if (!session.has(totpPlugin) && user.user.otpSecret != null) return@authorization totpPlugin
                else return@authorization donePlugin
            }

            return@authorization donePlugin
        }
    }

    install(Authentication) {
        this.register(object : AuthenticationProvider(object : Config("authentikt") {}) {
            override suspend fun onAuthenticate(context: AuthenticationContext) {
                val cookie = context.call.request.cookies["SessionToken"]

                val user = cookie
                    ?.takeIf { it.startsWith("token-for-") }
                    ?.removePrefix("token-for-")
                    ?.let { email -> users.find { it.email == email } }

                if (user != null) {
                    context.principal(user)
                } else {
                    context.challenge("CookieAuth", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                        call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                        challenge.complete()
                    }
                }
            }
        })
    }

    routing {
        route("/api") {
            get("/login") {
                val session = instance.createNewSession()
                session.publicAttributes[AttributeKey<Int>("auth_id")] = Random.nextInt(100000, 999999)
                call.respond(buildMap {
                    put("session_id", session.sessionId)
                })
            }
        }

        authenticate("authentikt") {
            get("/api/user/me") {
                val principal = call.principal<User>()!!
                call.respond(buildMap {
                    put("id", principal.email)
                    put("displayName", principal.displayName)
                })
            }

            post("/logout") {
                call.response.cookies.append(
                    name = "SessionToken",
                    value = "",
                    maxAge = 0,
                    secure = true,
                    httpOnly = true,
                    path = "/"
                )
                call.respondText("Logged out")
            }
        }
    }

    launch {
        delay(2.seconds)
        oauthDeviceFlowTest()
    }
}
