package es.jvbabi.authentikt.core.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ValidateDeviceFlowAuthorizationCallbackScope {
    fun generateUserCode(): String = (1..6).joinToString("") { ((1..9).toList() + ('A'..'Z').toList() + ('a'..'z').toList()).random().toString() }
}

class OAuthConfigurationBuilder<USER> {

    typealias ValidateAuthorizationCallback = (clientId: String, redirectUri: String) -> OAuthAuthorizationResult
    typealias ValidateDeviceFlowAuthorizationCallback = ValidateDeviceFlowAuthorizationCallbackScope.(clientId: String) -> OAuthDeviceFlowAuthorizationResult

    private var onAuthorize: ValidateAuthorizationCallback? = null
    private var onDeviceFlow: ValidateDeviceFlowAuthorizationCallback? = null

    /**
     * How long a device code can be redeemed after it was issued. Sent to the device as `expires_in`.
     */
    var deviceCodeLifetime: Duration = 10.minutes

    fun onAuthorize(
        block: ValidateAuthorizationCallback
    ) {
        this.onAuthorize = block
    }

    fun onDeviceFlow(
        block: ValidateDeviceFlowAuthorizationCallback
    ) {
        this.onDeviceFlow = block
    }

    internal fun build(): OAuthConfiguration {
        require(deviceCodeLifetime.isPositive()) { "deviceCodeLifetime must be positive" }
        return OAuthConfiguration(
            onAuthorize = this.onAuthorize,
            onDeviceFlowAuthorize = this.onDeviceFlow,
            deviceCodeLifetime = deviceCodeLifetime,
        )
    }
}