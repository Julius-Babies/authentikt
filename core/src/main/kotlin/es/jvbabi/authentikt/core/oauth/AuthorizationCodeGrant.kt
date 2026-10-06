package es.jvbabi.authentikt.core.oauth

import es.jvbabi.authentikt.core.AuthentiktInstance
import es.jvbabi.authentikt.core.config.OAuthAccessToken
import es.jvbabi.authentikt.core.config.OAuthAuthorizationResult
import es.jvbabi.authentikt.core.config.OAuthConfiguration
import es.jvbabi.authentikt.core.config.ValidateAuthorizationCallbackScope
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionDestination
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

/**
 * Parameters of the `GET /oauth/authorize` request that are needed when the authorization code is issued and
 * redeemed.
 *
 * @param redirectUri the `redirect_uri` parameter as sent by the client. The token request must repeat it.
 */
internal data class AuthorizationRequest(
    val redirectUri: String,
    val state: String?,
    val codeChallenge: String?,
)

internal val AuthorizationRequestKey = AttributeKey<AuthorizationRequest>("OAuthAuthorizationRequest")

/**
 * The redirect URL of an OAuth session whose authorization code has already been issued.
 */
internal val AuthorizationCodeRedirectKey = AttributeKey<String>("OAuthAuthorizationCodeRedirect")

internal class AuthorizationCode(
    val session: Session<*>,
    val clientId: String,
    val redirectUri: String,
    val codeChallenge: String?,
    val scopes: List<String>,
    val expiresAt: Instant,
)

/**
 * Issued authorization codes that have not been redeemed yet.
 */
internal val authorizationCodes: MutableMap<String, AuthorizationCode> = ConcurrentHashMap()

private val secureRandom = SecureRandom()
private val base64Url = Base64.getUrlEncoder().withoutPadding()
private val pkceValuePattern = Regex("^[A-Za-z0-9\\-._~]{43,128}$")

internal fun removeExpiredAuthorizationCodes(now: Instant) {
    authorizationCodes.values.removeIf { now >= it.expiresAt }
}

/**
 * Issues a single-use authorization code for a completed [session] with an [SessionDestination.OAuth] destination.
 *
 * @return the URL the browser has to be sent to: `redirect_uri?code=...&state=...`.
 */
internal fun issueAuthorizationCode(session: Session<*>, oAuthConfiguration: OAuthConfiguration): String {
    val destination = session.destination as SessionDestination.OAuth
    val request = requireNotNull(session.attributes[AuthorizationRequestKey]) {
        "Session ${session.sessionId} was not started by /oauth/authorize"
    }

    val code = ByteArray(32).also { secureRandom.nextBytes(it) }.let { base64Url.encodeToString(it) }
    authorizationCodes[code] = AuthorizationCode(
        session = session,
        clientId = destination.applicationId,
        redirectUri = request.redirectUri,
        codeChallenge = request.codeChallenge,
        scopes = destination.scopes,
        expiresAt = session.clock.now() + oAuthConfiguration.authorizationCodeLifetime,
    )

    return destination.redirectUri.withQueryParameters(buildList {
        add("code" to code)
        request.state?.let { add("state" to it) }
    })
}

private fun String.withQueryParameters(parameters: List<Pair<String, String>>): String {
    val query = parameters.joinToString("&") { (key, value) -> "${key.encodeURLParameter()}=${value.encodeURLParameter()}" }
    val separator = when {
        !contains('?') -> "?"
        endsWith('?') || endsWith('&') -> ""
        else -> "&"
    }
    return this + separator + query
}

/**
 * Responds with an OAuth 2.0 error response (RFC 6749, section 5.2).
 */
internal suspend fun ApplicationCall.respondOAuthError(
    error: String,
    description: String,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
) {
    response.header(HttpHeaders.CacheControl, "no-store")
    respondGson(
        value = buildGenericMap {
            put("error", error)
            put("error_description", description)
        },
        status = status,
    )
}

/**
 * Responds with an OAuth 2.0 access token response (RFC 6749, section 5.1).
 */
internal suspend fun ApplicationCall.respondAccessToken(accessToken: OAuthAccessToken, scopes: List<String> = emptyList()) {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header(HttpHeaders.Pragma, "no-cache")
    respondGson(
        buildGenericMap {
            put("access_token", accessToken.accessToken)
            put("token_type", "bearer")
            put("expires_in", accessToken.expiresIn.inWholeSeconds)
            put("refresh_token", accessToken.refreshToken)
            if (scopes.isNotEmpty()) put("scope", scopes.joinToString(" "))
        }
    )
}

/**
 * Handles `GET /oauth/authorize` (RFC 6749, section 4.1.1).
 *
 * Errors concerning the client or the redirect URI are answered with `400`. All other errors are sent to the
 * validated redirect URI, as required by RFC 6749, section 4.1.2.1.
 */
internal suspend fun <USER> ApplicationCall.handleAuthorizeRequest(authentiktInstance: AuthentiktInstance<USER>) {
    val oAuthConfiguration = authentiktInstance.configuration.oAuthConfiguration!!
    val onAuthorize = oAuthConfiguration.onAuthorize!!

    val clientId = parameters["client_id"]
    val requestedRedirectUri = parameters["redirect_uri"]
    if (clientId.isNullOrEmpty()) return respondOAuthError("invalid_request", "Missing client_id parameter.")
    if (requestedRedirectUri.isNullOrEmpty()) return respondOAuthError("invalid_request", "Missing redirect_uri parameter.")

    val requestedScopes = parameters["scope"].orEmpty().split(' ').filter { it.isNotEmpty() }.distinct()

    val result = ValidateAuthorizationCallbackScope(requestedScopes).onAuthorize(clientId, requestedRedirectUri)
    val application = when (result) {
        is OAuthAuthorizationResult.Error -> return respondOAuthError("invalid_request", result.error)
        is OAuthAuthorizationResult.Application -> result
    }

    val state = parameters["state"]

    suspend fun redirectWithError(error: String, description: String) {
        respondRedirect(
            application.redirectUri.withQueryParameters(buildList {
                add("error" to error)
                add("error_description" to description)
                state?.let { add("state" to it) }
            }),
            permanent = false,
        )
    }

    val responseType = parameters["response_type"]
    if (responseType.isNullOrEmpty()) return redirectWithError("invalid_request", "Missing response_type parameter.")
    if (responseType != "code") return redirectWithError("unsupported_response_type", "Only response_type=code is supported.")

    val codeChallenge = parameters["code_challenge"]
    val codeChallengeMethod = parameters["code_challenge_method"]
    if (codeChallenge == null) {
        if (codeChallengeMethod != null) return redirectWithError("invalid_request", "code_challenge_method requires a code_challenge.")
        if (oAuthConfiguration.authenticateClient == null) {
            return redirectWithError("invalid_request", "PKCE is required: provide a code_challenge.")
        }
    } else {
        if (codeChallengeMethod != "S256") return redirectWithError("invalid_request", "Only code_challenge_method=S256 is supported.")
        if (!pkceValuePattern.matches(codeChallenge)) return redirectWithError("invalid_request", "Malformed code_challenge.")
    }

    val session = authentiktInstance.createNewSession(
        destination = SessionDestination.OAuth(
            redirectUri = application.redirectUri,
            applicationId = application.clientId,
            applicationName = application.name,
            scopes = application.scopes ?: requestedScopes,
        )
    )
    session.attributes[AuthorizationRequestKey] = AuthorizationRequest(
        redirectUri = requestedRedirectUri,
        state = state,
        codeChallenge = codeChallenge,
    )

    val webUiRedirectUrl = URLBuilder(authentiktInstance.configuration.uiLoginBaseUrl).apply {
        parameters.append("_authentikt_flow_active", "true")
        parameters.append("_authentikt_session_id", session.sessionId)
    }.build()

    respondRedirect(webUiRedirectUrl, permanent = false)
}

/**
 * Client credentials sent with HTTP Basic authentication (RFC 6749, section 2.3.1), or `null` if the request does
 * not use HTTP Basic. Returns an empty pair if the header is malformed.
 */
private fun ApplicationCall.basicClientCredentials(): Pair<String, String>? {
    val header = request.header(HttpHeaders.Authorization) ?: return null
    if (!header.startsWith("Basic ", ignoreCase = true)) return null
    val decoded = runCatching { String(Base64.getDecoder().decode(header.substring(6).trim())) }.getOrNull()
    val separator = decoded?.indexOf(':') ?: -1
    if (decoded == null || separator < 0) return "" to ""
    return decoded.substring(0, separator).decodeURLQueryComponent(plusIsSpace = true) to
        decoded.substring(separator + 1).decodeURLQueryComponent(plusIsSpace = true)
}

/**
 * Handles `POST /oauth/token` with `grant_type=authorization_code` (RFC 6749, section 4.1.3, and RFC 7636).
 */
internal suspend fun <USER> ApplicationCall.handleAuthorizationCodeTokenRequest(
    params: Parameters,
    oAuthConfiguration: OAuthConfiguration,
    donePlugin: DonePlugin<USER>,
) {
    val basicCredentials = basicClientCredentials()
    val usesBasic = basicCredentials != null

    suspend fun invalidClient(description: String) {
        if (usesBasic) response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"oauth\"")
        respondOAuthError("invalid_client", description, HttpStatusCode.Unauthorized)
    }

    if (basicCredentials != null && params["client_secret"] != null) {
        return respondOAuthError("invalid_request", "Use only one client authentication method.")
    }
    if (basicCredentials != null && basicCredentials.first.isEmpty()) return invalidClient("Malformed client credentials.")

    val clientId = basicCredentials?.first ?: params["client_id"]
    val clientSecret = basicCredentials?.second ?: params["client_secret"]
    if (clientId.isNullOrEmpty()) return invalidClient("Missing client_id parameter.")
    if (basicCredentials != null && params["client_id"] != null && params["client_id"] != clientId) {
        return respondOAuthError("invalid_request", "client_id does not match the authenticated client.")
    }

    val code = params["code"]
    if (code.isNullOrEmpty()) return respondOAuthError("invalid_request", "Missing code parameter.")
    val redirectUri = params["redirect_uri"]
    if (redirectUri.isNullOrEmpty()) return respondOAuthError("invalid_request", "Missing redirect_uri parameter.")

    val clientAuthenticated = if (clientSecret != null) {
        val authenticateClient = oAuthConfiguration.authenticateClient
            ?: return invalidClient("Client authentication is not supported.")
        if (!authenticateClient(clientId, clientSecret)) return invalidClient("Client authentication failed.")
        true
    } else false

    // Single use: the code is removed before it is checked, so every code can only be presented once
    val grant = authorizationCodes.remove(code)
    if (grant == null || grant.session.clock.now() >= grant.expiresAt) {
        return respondOAuthError("invalid_grant", "The authorization code is invalid or has expired.")
    }
    if (grant.clientId != clientId) return respondOAuthError("invalid_grant", "The authorization code was issued to another client.")
    if (grant.redirectUri != redirectUri) return respondOAuthError("invalid_grant", "redirect_uri does not match the authorization request.")

    val codeVerifier = params["code_verifier"]
    if (grant.codeChallenge != null) {
        if (codeVerifier == null || !pkceValuePattern.matches(codeVerifier)) {
            return respondOAuthError("invalid_grant", "Missing or malformed code_verifier.")
        }
        val expected = base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII)))
        if (!MessageDigest.isEqual(expected.toByteArray(), grant.codeChallenge.toByteArray())) {
            return respondOAuthError("invalid_grant", "code_verifier does not match the code_challenge.")
        }
    } else {
        if (codeVerifier != null) return respondOAuthError("invalid_grant", "The authorization request did not use PKCE.")
        if (!clientAuthenticated) return invalidClient("Client authentication is required without PKCE.")
    }

    @Suppress("UNCHECKED_CAST")
    val session = grant.session as Session<USER>
    val onOAuthSuccess = requireNotNull(donePlugin.configuration.onOAuthSuccess) { "onOAuthSuccess callback is required for OAuth flow" }
    val accessToken = onOAuthSuccess(session, session.identifiedUser!!.user)
    respondAccessToken(accessToken, grant.scopes)
}
