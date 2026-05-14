package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.userselection.plugins.BaseUserSelectionPlugin

typealias FindNextStepCallback<USER> = suspend (session: Session<*>, user: AuthentiktUser<USER>) -> BasePlugin<*>

/**
 * Holds the resolved configuration for an authentikt installation.
 *
 * Created internally by [AuthentiktPluginConfigurationBuilder.build].
 *
 * @param findNextStepCallback determines which step plugin runs next for a given session and user.
 * @param apiPrefix prefix prepended to all auth routes (e.g. `"/api/v1"`).
 * @param installedPlugins set of registered step plugins (password, TOTP, done, etc.).
 * @param installedUserSelectionPlugins set of registered user-selection plugins (email, username, etc.).
 */
class AuthentiktConfiguration<USER>(
    val findNextStepCallback: FindNextStepCallback<USER>,
    val apiPrefix: String,
    val installedPlugins: Set<BasePlugin<*>>,
    val installedUserSelectionPlugins: Set<BaseUserSelectionPlugin<USER>>,
)
