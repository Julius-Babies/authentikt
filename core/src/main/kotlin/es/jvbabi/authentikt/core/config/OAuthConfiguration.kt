package es.jvbabi.authentikt.core.config

import kotlin.time.Duration

class OAuthConfiguration(
    val onAuthorize: OAuthConfigurationBuilder.ValidateAuthorizationCallback,
    val onDeviceFlowAuthorize: OAuthConfigurationBuilder.ValidateDeviceFlowAuthorizationCallback?,
)

sealed class OAuthAuthorizationResult {
    data class Application(
        val clientId: String,
        val redirectUri: String,
        val name: String,
    ) : OAuthAuthorizationResult()

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