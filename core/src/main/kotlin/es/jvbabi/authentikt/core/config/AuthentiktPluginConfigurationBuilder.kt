package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.AuthentiktUserSource
import es.jvbabi.authentikt.core.step.plugins.BasePlugin

class AuthentiktPluginConfigurationBuilder<USER> {

    internal var userSelection: UserSelectionConfig? = null
    var authentiktUserSource: AuthentiktUserSource<USER>? = null
    var apiPrefix = ""
    private var findNextStepCallback: FindNextStepCallback<USER>? = null
    private val installedPlugins = mutableSetOf<BasePlugin<*>>()

    fun userSelection(block: UserSelectionConfig.() -> Unit) {
        userSelection = UserSelectionConfig().apply(block)
    }

    fun authorization(block: FindNextStepCallback<USER>) {
        findNextStepCallback = block
    }

    fun install(plugin: BasePlugin<*>) {
        installedPlugins.add(plugin)
    }

    internal fun build(): AuthentiktConfiguration<USER> {
        require(userSelection != null && userSelection!!.validate()) { "UserSelection must be configured" }
        requireNotNull(authentiktUserSource) { "AuthentiktUserSource must be configured" }
        requireNotNull(this.findNextStepCallback) { "findNextStepCallback must be configured" }

        return AuthentiktConfiguration(
            userSelection = userSelection!!,
            authentiktUserSource = authentiktUserSource!!,
            findNextStepCallback = this.findNextStepCallback!!,
            apiPrefix = apiPrefix,
            installedPlugins = installedPlugins
        )
    }
}
