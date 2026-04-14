package es.jvbabi.authentikt.core.config

class AuthentiktPluginConfiguration {

    internal lateinit var userSelection: UserSelectionConfig
    var apiPrefix = ""

    fun userSelection(block: UserSelectionConfig.() -> Unit) {
        userSelection = UserSelectionConfig().apply(block)
    }

    fun userAuthorization(block: UserAuthorizationConfig.() -> Unit) {

    }
}
