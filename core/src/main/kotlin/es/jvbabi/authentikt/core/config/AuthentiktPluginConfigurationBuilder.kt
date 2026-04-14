package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.AuthentiktUserSource

class AuthentiktPluginConfigurationBuilder<USER> {

    internal var userSelection: UserSelectionConfig? = null
    var authentiktUserSource: AuthentiktUserSource<USER>? = null
    var apiPrefix = ""
    var onSuccess: (suspend (user: AuthentiktUser<USER>) -> Unit)? = null

    fun userSelection(block: UserSelectionConfig.() -> Unit) {
        userSelection = UserSelectionConfig().apply(block)
    }

    fun userAuthorization(block: UserAuthorizationConfig.() -> Unit) {

    }

    fun onSuccess(block: suspend (user: AuthentiktUser<USER>) -> Unit) {
        onSuccess = block
    }

    internal fun build(): AuthentiktConfiguration<USER> {
        require(authentiktUserSource != null) { "AuthentiktUserSource must be configured" }
        require(userSelection != null && userSelection!!.validate()) { "UserSelection must be configured" }
        require(onSuccess != null) { "onSuccess callback must be configured" }

        return AuthentiktConfiguration(
            userSelection = userSelection!!,
            authentiktUserSource = authentiktUserSource!!,
            apiPrefix = apiPrefix,
            onSuccess = onSuccess!!
        )
    }
}
