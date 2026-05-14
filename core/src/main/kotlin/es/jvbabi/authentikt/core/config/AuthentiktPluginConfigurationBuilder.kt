package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.userselection.plugins.BaseUserSelectionPlugin

/**
 * DSL builder used inside [es.jvbabi.authentikt.core.installAuthentikt]
 * to configure the auth pipeline.
 *
 * ### Usage
 * ```kotlin
 * installAuthentikt {
 *     apiPrefix = "/api/v1"
 *     install(emailPlugin)
 *     install(passwordPlugin)
 *     install(donePlugin)
 *     authorization { session, user -> /* next step */ }
 * }
 * ```
 */
class AuthentiktPluginConfigurationBuilder<USER> {

    var apiPrefix = ""
    private var findNextStepCallback: FindNextStepCallback<USER>? = null
    private val installedPlugins = mutableSetOf<BasePlugin<*>>()
    private val installedUserSelectionPlugins = mutableSetOf<BaseUserSelectionPlugin<USER>>()

    /**
     * Registers the callback that determines the next authentication step.
     *
 * This function is called after each step completes. It receives the current
 * session and the identified user, and must return the next [BasePlugin]
 * to execute. Return a [BasePlugin] that has been previously installed.
     */
    fun authorization(block: FindNextStepCallback<USER>) {
        findNextStepCallback = block
    }

    /**
     * Registers a step plugin (password, TOTP, done, or custom).
     *
     * The plugin must have a unique [BasePlugin.namespace] that matches the
     * value returned by the authorization callback.
     */
    fun install(plugin: BasePlugin<*>) {
        installedPlugins.add(plugin)
    }

    /**
     * Registers a user-selection plugin (email, username, or custom).
     *
     * At least one user-selection plugin must be installed.
     */
    fun install(plugin: BaseUserSelectionPlugin<USER>) {
        installedUserSelectionPlugins.add(plugin)
    }

    internal fun build(): AuthentiktConfiguration<USER> {
        require(installedUserSelectionPlugins.isNotEmpty()) {
            "At least one user selection plugin must be installed"
        }
        requireNotNull(this.findNextStepCallback) {
            "findNextStepCallback must be configured via authorization { ... }"
        }

        return AuthentiktConfiguration(
            findNextStepCallback = this.findNextStepCallback!!,
            apiPrefix = apiPrefix,
            installedPlugins = installedPlugins,
            installedUserSelectionPlugins = installedUserSelectionPlugins,
        )
    }
}
