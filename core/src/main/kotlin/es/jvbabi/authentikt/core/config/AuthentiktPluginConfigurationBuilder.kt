package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.userselection.plugins.BaseUserSelectionPlugin

class AuthentiktPluginConfigurationBuilder<USER> {

    var apiPrefix = ""
    private var findNextStepCallback: FindNextStepCallback<USER>? = null
    private val installedPlugins = mutableSetOf<BasePlugin<*>>()
    private val installedUserSelectionPlugins = mutableSetOf<BaseUserSelectionPlugin<USER>>()

    fun authorization(block: FindNextStepCallback<USER>) {
        findNextStepCallback = block
    }

    fun install(plugin: BasePlugin<*>) {
        installedPlugins.add(plugin)
    }

    fun install(plugin: BaseUserSelectionPlugin<USER>) {
        installedUserSelectionPlugins.add(plugin)
    }

    internal fun build(): AuthentiktConfiguration<USER> {
        require(installedUserSelectionPlugins.isNotEmpty()) { "At least one user selection plugin must be installed" }
        requireNotNull(this.findNextStepCallback) { "findNextStepCallback must be configured" }

        return AuthentiktConfiguration(
            findNextStepCallback = this.findNextStepCallback!!,
            apiPrefix = apiPrefix,
            installedPlugins = installedPlugins,
            installedUserSelectionPlugins = installedUserSelectionPlugins,
        )
    }
}
