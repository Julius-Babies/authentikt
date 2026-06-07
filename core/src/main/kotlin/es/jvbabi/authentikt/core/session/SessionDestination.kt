package es.jvbabi.authentikt.core.session

sealed class SessionDestination {
    abstract val applicationId: String
    abstract val applicationName: String

    data class OAuth(
        val redirectUri: String,
        override val applicationId: String,
        override val applicationName: String,
    ) : SessionDestination()

    data class DeviceFlow(
        val deviceCode: String,
        val userCode: String,
        override val applicationId: String,
        override val applicationName: String,
    ) : SessionDestination()
}