package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.AuthentiktUserSource
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin

class AuthentiktPluginConfigurationBuilder<USER> {

    internal var userSelection: UserSelectionConfig? = null
    var authentiktUserSource: AuthentiktUserSource<USER>? = null
    var apiPrefix = ""
    var onSuccess: (suspend (user: AuthentiktUser<USER>) -> Unit)? = null
    private var findNextStepCallback: FindNextStepCallback? = null
    private val installedPlugins = mutableSetOf<BasePlugin<*>>(DonePlugin)

    fun userSelection(block: UserSelectionConfig.() -> Unit) {
        userSelection = UserSelectionConfig().apply(block)
    }

    fun authorization(block: FindNextStepCallback) {
        findNextStepCallback = block
    }

    fun install(plugin: BasePlugin<*>) {
        installedPlugins.add(plugin)
    }

    fun onSuccess(block: suspend (user: AuthentiktUser<USER>) -> Unit) {
        onSuccess = block
    }

    internal fun build(): AuthentiktConfiguration<USER> {
        require(authentiktUserSource != null) { "AuthentiktUserSource must be configured" }
        require(userSelection != null && userSelection!!.validate()) { "UserSelection must be configured" }
        require(onSuccess != null) { "onSuccess callback must be configured" }
        requireNotNull(this.findNextStepCallback) { "findNextStepCallback must be configured" }

        return AuthentiktConfiguration(
            userSelection = userSelection!!,
            authentiktUserSource = authentiktUserSource!!,
            findNextStepCallback = this.findNextStepCallback!!,
            apiPrefix = apiPrefix,
            onSuccess = onSuccess!!,
            installedPlugins = installedPlugins
        )
    }
}
