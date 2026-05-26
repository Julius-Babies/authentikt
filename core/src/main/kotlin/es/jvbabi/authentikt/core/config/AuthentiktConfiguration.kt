package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import io.ktor.http.*
import java.io.File

typealias FindNextStepCallback<USER> = suspend (session: Session<USER>) -> BasePlugin<USER, *>

/**
 * Holds the resolved configuration for an authentikt installation.
 *
 * Created internally by [AuthentiktPluginConfigurationBuilder.build].
 *
 * @param findNextStepCallback determines which step plugin runs next for a given session and user.
 * @param baseUrl the base URL of the application (e.g. `https://example.com`). This does not include the [apiPrefix].
 * @param apiPrefix prefix prepended to all auth routes (e.g. `"/api/v1"`).
 * @param installedPlugins set of registered step plugins (password, TOTP, done, etc.).
 */
class AuthentiktConfiguration<USER>(
    val findNextStepCallback: FindNextStepCallback<USER>,
    val baseUrl: String,
    val uiLoginBaseUrl: Url,
    val apiPrefix: String,
    val installedPlugins: Set<BasePlugin<USER, *>>,
    val customSslCerts: List<File>,
)
