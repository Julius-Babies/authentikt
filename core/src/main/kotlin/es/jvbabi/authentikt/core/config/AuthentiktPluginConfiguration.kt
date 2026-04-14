package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.AuthentiktUserSource

class AuthentiktPluginConfiguration {

    internal var userSelection: UserSelectionConfig? = null
    var authentiktUserSource: AuthentiktUserSource? = null
    var apiPrefix = ""
    var onSuccess: (suspend (user: AuthentiktUser) -> Unit)? = null

    fun userSelection(block: UserSelectionConfig.() -> Unit) {
        userSelection = UserSelectionConfig().apply(block)
    }

    fun userAuthorization(block: UserAuthorizationConfig.() -> Unit) {

    }

    fun onSuccess(block: suspend (user: AuthentiktUser) -> Unit) {
        onSuccess = block
    }

    internal fun validate() {
        require(authentiktUserSource != null) { "AuthentiktUserSource must be configured" }
        require(userSelection != null && userSelection!!.validate()) { "UserSelection must be configured" }
    }
}
