package es.jvbabi.authentikt.core.config

class ValidateDeviceFlowAuthorizationCallbackScope {
    fun generateUserCode(): String = (1..6).joinToString("") { ((1..9).toList() + ('A'..'Z').toList() + ('a'..'z').toList()).random().toString() }
}

class OAuthConfigurationBuilder<USER> {

    typealias ValidateAuthorizationCallback = (clientId: String, redirectUri: String) -> OAuthAuthorizationResult
    typealias ValidateDeviceFlowAuthorizationCallback = ValidateDeviceFlowAuthorizationCallbackScope.(clientId: String) -> OAuthDeviceFlowAuthorizationResult

    private var onAuthorize: ValidateAuthorizationCallback? = null
    private var onDeviceFlow: ValidateDeviceFlowAuthorizationCallback? = null

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
        return OAuthConfiguration(
            onAuthorize = this.onAuthorize,
            onDeviceFlowAuthorize = this.onDeviceFlow,
        )
    }
}