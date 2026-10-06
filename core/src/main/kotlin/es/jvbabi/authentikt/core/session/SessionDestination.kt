package es.jvbabi.authentikt.core.session

sealed class SessionDestination {
    abstract val applicationId: String
    abstract val applicationName: String

    /**
     * The session was started by `GET /oauth/authorize` (authorization code grant).
     *
     * @param redirectUri where the browser is sent with the authorization code.
     * @param scopes the scopes granted to the client.
     */
    data class OAuth(
        val redirectUri: String,
        override val applicationId: String,
        override val applicationName: String,
        val scopes: List<String> = emptyList(),
    ) : SessionDestination()

    data class DeviceFlow(
        val deviceCode: String,
        val userCode: String,
        override val applicationId: String,
        override val applicationName: String,
    ) : SessionDestination()
}
