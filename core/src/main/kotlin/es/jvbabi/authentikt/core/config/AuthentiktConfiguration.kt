package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.userselection.plugins.BaseUserSelectionPlugin

typealias FindNextStepCallback<USER> = suspend (session: Session<*>, user: AuthentiktUser<USER>) -> BasePlugin<*>

class AuthentiktConfiguration<USER>(
    val findNextStepCallback: FindNextStepCallback<USER>,
    val apiPrefix: String,
    val installedPlugins: Set<BasePlugin<*>>,
    val installedUserSelectionPlugins: Set<BaseUserSelectionPlugin<USER>>,
)
