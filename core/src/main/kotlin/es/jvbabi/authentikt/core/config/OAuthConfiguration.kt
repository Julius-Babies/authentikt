package es.jvbabi.authentikt.core.config

import kotlin.time.Duration

class OAuthConfiguration(
    val onAuthorize: OAuthConfigurationBuilder.ValidateAuthorizationCallback?,
    val onDeviceFlowAuthorize: OAuthConfigurationBuilder.ValidateDeviceFlowAuthorizationCallback?,
    val authenticateClient: OAuthConfigurationBuilder.AuthenticateClientCallback?,
    val deviceCodeLifetime: Duration,
    val authorizationCodeLifetime: Duration,
)

sealed class OAuthAuthorizationResult {
    /**
     * The client may start an authorization code flow.
     *
     * @param clientId the client the authorization code is issued to.
     * @param redirectUri where the browser is sent with the authorization code. Only return URIs that are registered
     * for [clientId].
     * @param name the application name shown on the login page.
     * @param scopes the granted scopes. `null` grants all requested scopes. Return a subset to grant fewer scopes.
     */
    data class Application(
        val clientId: String,
        val redirectUri: String,
        val name: String,
        val scopes: List<String>? = null,
    ) : OAuthAuthorizationResult()

    /**
     * The client or redirect URI is not valid. The browser is not redirected and receives `400` with [error] as
     * `error_description`.
     */
    data class Error(val error: String) : OAuthAuthorizationResult()
}

sealed class OAuthDeviceFlowAuthorizationResult {
    data class Application(
        val clientId: String,
        val name: String,
        val deviceCode: String,
        val userCode: String,
    ) : OAuthDeviceFlowAuthorizationResult()

    data class Error(val error: String) : OAuthDeviceFlowAuthorizationResult()
}

data class OAuthAccessToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Duration,
)
