package es.jvbabi.authentikt.samples

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.installAuthentikt
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.PasswordPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPlugin
import es.jvbabi.authentikt.core.userselection.plugins.builtin.EmailUserSelectionPlugin
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
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

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
        anyMethod()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Cookie)
        allowHost("localhost:5173")
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

    val donePlugin = DonePlugin<User> {
        generateToken { _, user ->
            return@generateToken "token-for-${user.email}"
        }

        cookie(
            name = "SessionToken",
            validFor = 60.days
        )
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
        install(emailUserSelectionPlugin)

        install(passwordPlugin)
        install(totpPlugin)
        install(donePlugin)

        authorization { session: Session<*>, user: AuthentiktUser<User> ->
            if (!session.has(passwordPlugin)) return@authorization passwordPlugin
            if (!session.has(totpPlugin) && user.user.otpSecret != null) return@authorization totpPlugin

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
        get("/login") {
            val session = instance.createNewSession()
            call.respond(buildMap {
                put("session_id", session.sessionId)
            })
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
}
