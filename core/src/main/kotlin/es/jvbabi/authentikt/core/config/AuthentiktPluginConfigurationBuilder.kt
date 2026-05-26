package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import io.ktor.http.Url
import java.io.File

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
    var baseUrl = ""
    var uiLoginBaseUrl = ""
    private var findNextStepCallback: FindNextStepCallback<USER>? = null
    private val installedPlugins = mutableSetOf<BasePlugin<USER, *>>()
    private val customSslCerts = mutableListOf<File>()

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
    fun install(plugin: BasePlugin<USER, *>) {
        installedPlugins.add(plugin)
    }

    fun customSslCert(cert: String) {
        customSslCerts.add(File(cert))
    }

    internal fun build(): AuthentiktConfiguration<USER> {
        requireNotNull(this.findNextStepCallback) {
            "findNextStepCallback must be configured via authorization { ... }"
        }
        require(baseUrl.isNotEmpty()) { "baseUrl must be set" }
        require(customSslCerts.all { it.exists() }) { "customSslCerts must exist" }
        require(uiLoginBaseUrl.isNotEmpty()) { "uiLoginBaseUrl must be set" }

        return AuthentiktConfiguration(
            findNextStepCallback = this.findNextStepCallback!!,
            apiPrefix = apiPrefix,
            installedPlugins = installedPlugins,
            baseUrl = baseUrl,
            uiLoginBaseUrl = Url(uiLoginBaseUrl),
            customSslCerts = customSslCerts,
        )
    }
}

