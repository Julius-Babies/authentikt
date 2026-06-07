package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.AuthentiktInstance
import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.BaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.utils.customSsl
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OIDCPlugin<USER>(
    configuration: OIDCPluginConfigurationBuilder<USER>.() -> Unit,
) : BasePlugin<USER, OIDCPluginState>(
    namespace = "authentikt-builtin/oidc"
) {

    private lateinit var callbackUrl: Url

    private val configuration = OIDCPluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    override fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {}

    override fun installStaticRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {
        with(inRoute) {
            route("/${configuration.applicationName}") {

                val httpClient = HttpClient(CIO) {
                    install(ContentNegotiation) { json(json) }
                    customSsl(authentiktInstance.configuration.customSslCerts)
                }

                get("/callback") {
                    val state = json.decodeFromString<OIDCState>(call.request.queryParameters["state"]!!)
                    val session = sessions[state.sessionId]!! as Session<USER>
                    val code = call.request.queryParameters["code"]!!
                    session.authenticationSteps[session.authenticationSteps.lastIndex] = this@OIDCPlugin to (session.authenticationSteps[session.authenticationSteps.lastIndex].second as OIDCPluginState).copy(hasCompleted = true)

                    val tokenResponse = httpClient.post(configuration.tokenUrl) {
                        contentType(ContentType.Application.FormUrlEncoded)
                        setBody(
                            listOf(
                                "client_id" to configuration.clientId,
                                "client_secret" to configuration.clientSecret,
                                "code" to code,
                                "grant_type" to "authorization_code",
                                "redirect_uri" to callbackUrl.toString(),
                            ).formUrlEncode()
                        )
                    }

                    if (!tokenResponse.status.isSuccess()) {
                        println("Failed to exchange code for token: ${tokenResponse.status}")
                        println(tokenResponse.bodyAsText())
                        call.respondText(
                            "Failed to exchange code for token",
                            status = HttpStatusCode.InternalServerError
                        )
                        return@get
                    }

                    val tokenResponseBody = tokenResponse.body<OIDCTokenResponse>()

                    val userResponse = httpClient.get(configuration.userInfoEndpoint.toString()) {
                        bearerAuth(tokenResponseBody.accessToken)
                    }
                    if (!userResponse.status.isSuccess()) {
                        println("Failed to fetch user info: ${userResponse.status}")
                        println(userResponse.bodyAsText())
                        call.respondText("Failed to fetch user info", status = HttpStatusCode.InternalServerError)
                        return@get
                    }


                    val result = configuration.onUserInfo(userResponse, tokenResponseBody.accessToken)
                    if (result is UserInfo.Result.Success) {
                        session.identifiedUser = result.user
                        session.nextStep()

                        val webUiRedirectUrl = URLBuilder(authentiktInstance.configuration.uiLoginBaseUrl).apply {
                            parameters.append("_authentikt_flow_active", "true")
                            parameters.append("_authentikt_session_id", session.sessionId)
                        }.build()

                        call.respondRedirect(webUiRedirectUrl, permanent = false)
                    }
                }.also { callbackRoute ->
                    callbackUrl = URLBuilder(authentiktInstance.configuration.baseUrl).apply {
                        appendPathSegments(callbackRoute.path().split("/"))
                    }.build()
                    logger.info("OIDC Callback Route for application ${configuration.applicationName} installed at $callbackUrl")
                }
            }
        }
    }

    private val json = Json { prettyPrint = false; isLenient = true; ignoreUnknownKeys = true }

    override suspend fun createState(session: Session<*>): OIDCPluginState {
        val url = URLBuilder(configuration.authorizationEndpoint).apply {
            parameters.append("client_id", configuration.clientId)
            parameters.append("response_type", "code")
            parameters.append("scope", configuration.scopes.joinToString(" "))
            parameters.append("redirect_uri", callbackUrl.toString())
            parameters.append("state", json.encodeToString(OIDCState(session.sessionId)))
        }.build()
        return OIDCPluginState(url = url, hasCompleted = false)
    }
}

data class OIDCPluginState(
    val url: Url,
    var hasCompleted: Boolean,
) : BaseState {
    override suspend fun isCompleted(): Boolean {
        return hasCompleted
    }

    override suspend fun createClientState(session: Session<*>): Map<String, Any?> {
        return buildMap {
            put("authorize_url", url.toString())
        }
    }
}

@Serializable
private data class OIDCState(
    @SerialName("authentikt_oidc_internal_session_id") val sessionId: String,
)

class OIDCPluginConfigurationBuilder<USER> {
    private var _clientId: String? = null
    var clientId: String
        get() = _clientId ?: throw IllegalStateException("clientId must be set")
        set(value) {
            _clientId = value
        }

    private var _clientSecret: String? = null
    var clientSecret: String
        get() = _clientSecret ?: throw IllegalStateException("clientSecret must be set")
        set(value) {
            _clientSecret = value
        }
    private var _authorizationEndpoint: String? = null
    var authorizationEndpoint: String
        get() = _authorizationEndpoint ?: throw IllegalStateException("authorizationEndpoint must be set")
        set(value) {
            _authorizationEndpoint = value
        }

    private var _tokenEndpoint: String? = null
    var tokenEndpoint: String
        get() = _tokenEndpoint ?: throw IllegalStateException("tokenEndpoint must be set")
        set(value) {
            _tokenEndpoint = value
        }

    private var _userInfoEndpoint: String? = null
    var userInfoEndpoint: String
        get() = _userInfoEndpoint ?: throw IllegalStateException("userInfoEndpoint must be set")
        set(value) {
            _userInfoEndpoint = value
        }

    private val scopes = mutableListOf<String>()
    fun scopes(vararg scopes: String) {
        this.scopes.addAll(scopes)
    }

    var applicationName = "default"

    private var onUserInfo: OIDCPluginConfiguration.OnUserInfo<USER>? = null
    fun onUserInfo(block: OIDCPluginConfiguration.OnUserInfo<USER>) {
        onUserInfo = block
    }

    internal fun build(): OIDCPluginConfiguration<USER> {
        require(_clientId.orEmpty().isNotEmpty()) { "clientId must be set" }
        require(_clientSecret.orEmpty().isNotEmpty()) { "clientSecret must be set" }
        require(_authorizationEndpoint.orEmpty().isNotEmpty()) { "authorizationEndpoint must be set" }
        require(scopes.isNotEmpty()) { "At least one scope must be set" }
        require(applicationName.isNotEmpty()) { "applicationName must be set" }
        require(_tokenEndpoint.orEmpty().isNotEmpty()) { "tokenEndpoint must be set" }
        require(_userInfoEndpoint.orEmpty().isNotEmpty()) { "userInfoEndpoint must be set" }
        requireNotNull(onUserInfo) { "onUserInfo callback must be set" }

        return OIDCPluginConfiguration(
            applicationName = applicationName,
            clientId = _clientId!!,
            clientSecret = _clientSecret!!,
            scopes = scopes,
            authorizationEndpoint = Url(_authorizationEndpoint!!),
            tokenUrl = Url(_tokenEndpoint!!),
            userInfoEndpoint = Url(_userInfoEndpoint!!),
            onUserInfo = onUserInfo!!
        )
    }
}

internal data class OIDCPluginConfiguration<USER>(
    val applicationName: String,
    val clientId: String,
    val clientSecret: String,
    val scopes: List<String>,
    val authorizationEndpoint: Url,
    val tokenUrl: Url,
    val userInfoEndpoint: Url,
    val onUserInfo: OnUserInfo<USER>,
) {
    typealias OnUserInfo<USER> = suspend (response: HttpResponse, accessToken: String) -> UserInfo.Result<USER>
}

@Serializable
private data class OIDCTokenResponse(
    @SerialName("access_token") val accessToken: String,
)

class UserInfo {
    sealed class Result<out USER> {
        data class Success<USER>(val user: AuthentiktUser<USER>) : Result<USER>()
        data class Failure(val error: String) : Result<Nothing>()
    }
}
