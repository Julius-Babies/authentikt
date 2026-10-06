package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.AuthentiktInstance
import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.routes.flow.respondStepNotActive
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.findActiveSession
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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

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

                val idTokenVerifier = configuration.jwksUri?.takeIf { "openid" in configuration.scopes }?.let { jwksUri ->
                    OIDCIdTokenVerifier(
                        httpClient = httpClient,
                        jwksUri = jwksUri,
                        clientId = configuration.clientId,
                        clientSecret = configuration.clientSecret,
                        issuer = configuration.issuer,
                    )
                }

                get("/callback") {
                    val params = call.request.queryParameters
                    val stateParam = params["state"]
                    if (stateParam.isNullOrEmpty()) {
                        call.respondText("Missing state parameter.", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    val session = findSessionByState(stateParam)
                    if (session == null) {
                        call.respondText(
                            "The login attempt is unknown, has already been used or has expired. Please start the login again.",
                            status = HttpStatusCode.BadRequest,
                        )
                        return@get
                    }

                    val error = params["error"]
                    if (error != null) {
                        // The user can retry with the same authorization URL, so the state is not consumed.
                        logger.info("OIDC provider returned error '$error' for session ${session.sessionId}: ${params["error_description"]}")
                        call.respondText(
                            "The identity provider did not authorize the login: $error",
                            status = HttpStatusCode.BadRequest,
                        )
                        return@get
                    }

                    val code = params["code"]
                    if (code.isNullOrEmpty()) {
                        call.respondText("Missing code parameter.", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    // Single use: replaces the step state with a fresh one, so the state cannot be replayed and the
                    // user can retry with the new authorization URL if anything below fails.
                    val oidcState = consumeState(session, stateParam)
                    if (oidcState == null) {
                        call.respondText(
                            "The login attempt has already been used. Please start the login again.",
                            status = HttpStatusCode.BadRequest,
                        )
                        return@get
                    }

                    val tokenResponse = httpClient.post(configuration.tokenUrl) {
                        contentType(ContentType.Application.FormUrlEncoded)
                        setBody(
                            listOf(
                                "client_id" to configuration.clientId,
                                "client_secret" to configuration.clientSecret,
                                "code" to code,
                                "code_verifier" to oidcState.codeVerifier,
                                "grant_type" to "authorization_code",
                                "redirect_uri" to callbackUrl.toString(),
                            ).formUrlEncode()
                        )
                    }

                    if (!tokenResponse.status.isSuccess()) {
                        logger.warn("Failed to exchange code for token in session ${session.sessionId}: ${tokenResponse.status} ${tokenResponse.bodyAsText()}")
                        call.respondText(
                            "Failed to exchange code for token",
                            status = HttpStatusCode.InternalServerError
                        )
                        return@get
                    }

                    val tokenResponseBody = tokenResponse.body<OIDCTokenResponse>()

                    if (idTokenVerifier != null) {
                        val idToken = tokenResponseBody.idToken
                        val idTokenError = if (idToken == null) "The token response contains no ID token"
                        else idTokenVerifier.verify(idToken, oidcState.nonce, session.clock)
                        if (idTokenError != null) {
                            logger.warn("Invalid ID token in session ${session.sessionId}: $idTokenError")
                            call.respondText("Invalid ID token", status = HttpStatusCode.Unauthorized)
                            return@get
                        }
                    }

                    val userResponse = httpClient.get(configuration.userInfoEndpoint.toString()) {
                        bearerAuth(tokenResponseBody.accessToken)
                    }
                    if (!userResponse.status.isSuccess()) {
                        logger.warn("Failed to fetch user info in session ${session.sessionId}: ${userResponse.status} ${userResponse.bodyAsText()}")
                        call.respondText("Failed to fetch user info", status = HttpStatusCode.InternalServerError)
                        return@get
                    }


                    val result = configuration.onUserInfo(userResponse, tokenResponseBody.accessToken)
                    when (result) {
                        is UserInfo.Result.Success -> {
                            val activeState = session.authenticationSteps.lastOrNull()?.second as? OIDCPluginState
                                ?: return@get call.respondStepNotActive()
                            val completed = session.completeStep(this@OIDCPlugin, activeState.copy(hasCompleted = true)) {
                                identifiedUser = result.user
                            }
                            if (!completed) return@get call.respondStepNotActive()

                            val webUiRedirectUrl = URLBuilder(authentiktInstance.configuration.uiLoginBaseUrl).apply {
                                parameters.append("_authentikt_flow_active", "true")
                                parameters.append("_authentikt_session_id", session.sessionId)
                            }.build()

                            call.respondRedirect(webUiRedirectUrl, permanent = false)
                        }

                        is UserInfo.Result.Failure -> {
                            logger.warn("Failed to load userinfo in session ${session.sessionId}: ${result.error}")
                            call.respondText(result.error, status = HttpStatusCode.Unauthorized)
                        }
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
        val state = randomUrlSafeString()
        val codeVerifier = randomUrlSafeString()
        val nonce = randomUrlSafeString()
        val url = URLBuilder(configuration.authorizationEndpoint).apply {
            parameters.append("client_id", configuration.clientId)
            parameters.append("response_type", "code")
            parameters.append("scope", configuration.scopes.joinToString(" "))
            parameters.append("redirect_uri", callbackUrl.toString())
            parameters.append("state", state)
            parameters.append("code_challenge", codeChallengeS256(codeVerifier))
            parameters.append("code_challenge_method", "S256")
            parameters.append("nonce", nonce)
        }.build()
        return OIDCPluginState(
            url = url,
            hasCompleted = false,
            state = state,
            codeVerifier = codeVerifier,
            nonce = nonce,
        )
    }

    /**
     * Returns the active session whose active step is this plugin, waiting for a callback with [state].
     */
    @Suppress("UNCHECKED_CAST")
    private fun findSessionByState(state: String): Session<USER>? {
        val match = sessions.values.firstOrNull { session ->
            val (plugin, stepState) = session.authenticationSteps.lastOrNull() ?: return@firstOrNull false
            plugin == this && stepState is OIDCPluginState && !stepState.hasCompleted && stepState.matches(state)
        } ?: return null
        return findActiveSession(match.sessionId) as Session<USER>?
    }

    /**
     * Atomically checks that the active step still waits for [state] and replaces its state with a fresh one.
     *
     * @return the consumed state, or `null` if [state] is no longer valid.
     */
    private suspend fun consumeState(session: Session<USER>, state: String): OIDCPluginState? = session.withLock {
        val (plugin, stepState) = session.authenticationSteps.lastOrNull() ?: return@withLock null
        if (plugin != this || stepState !is OIDCPluginState || stepState.hasCompleted || !stepState.matches(state)) {
            return@withLock null
        }
        session.authenticationSteps[session.authenticationSteps.lastIndex] = plugin to createState(session)
        stepState
    }
}

private val secureRandom = SecureRandom()

/** 32 random bytes, base64url-encoded without padding (43 characters). */
private fun randomUrlSafeString(): String {
    val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** The PKCE `S256` code challenge for [codeVerifier] (RFC 7636, section 4.2). */
internal fun codeChallengeS256(codeVerifier: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
}

internal fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

/**
 * State of the [OIDCPlugin] step.
 *
 * @param state the random, single-use `state` parameter of the authorization request.
 * @param codeVerifier the PKCE code verifier, sent to the token endpoint.
 * @param nonce the `nonce` parameter, checked against the ID token.
 */
data class OIDCPluginState(
    val url: Url,
    var hasCompleted: Boolean,
    internal val state: String,
    internal val codeVerifier: String,
    internal val nonce: String,
) : BaseState {
    internal fun matches(state: String): Boolean = constantTimeEquals(this.state, state)

    override suspend fun isCompleted(): Boolean {
        return hasCompleted
    }

    override suspend fun createClientState(session: Session<*>): Map<String, Any?> {
        return buildMap {
            put("authorize_url", url.toString())
        }
    }
}

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

    /**
     * The expected `iss` claim of the ID token, for example `https://sso.example.com/realms/main`.
     * If `null`, the issuer is not checked.
     */
    var issuer: String? = null

    /**
     * The JSON Web Key Set of the provider, for example `https://sso.example.com/realms/main/protocol/openid-connect/certs`
     * (`jwks_uri` in the provider's `/.well-known/openid-configuration`). Used to verify the signature of the ID token.
     * Required if the `openid` scope is requested.
     */
    var jwksUri: String? = null

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
        require("openid" !in scopes || !jwksUri.isNullOrEmpty()) { "jwksUri must be set if the openid scope is requested" }

        return OIDCPluginConfiguration(
            applicationName = applicationName,
            clientId = _clientId!!,
            clientSecret = _clientSecret!!,
            scopes = scopes,
            authorizationEndpoint = Url(_authorizationEndpoint!!),
            tokenUrl = Url(_tokenEndpoint!!),
            userInfoEndpoint = Url(_userInfoEndpoint!!),
            issuer = issuer,
            jwksUri = jwksUri?.let(::Url),
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
    val issuer: String?,
    val jwksUri: Url?,
    val onUserInfo: OnUserInfo<USER>,
) {
    typealias OnUserInfo<USER> = suspend (response: HttpResponse, accessToken: String) -> UserInfo.Result<USER>
}

@Serializable
private data class OIDCTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String? = null,
)

class UserInfo {
    sealed class Result<out USER> {
        data class Success<USER>(val user: AuthentiktUser<USER>) : Result<USER>()
        data class Failure(val error: String) : Result<Nothing>()
    }
}
