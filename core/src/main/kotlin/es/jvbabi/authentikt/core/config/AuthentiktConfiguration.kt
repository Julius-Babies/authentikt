package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.AuthentiktUserSource
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.plugins.BasePlugin

typealias FindNextStepCallback = suspend (session: Session, user: AuthentiktUser<*>) -> BasePlugin<*>

internal class AuthentiktConfiguration<USER>(
    val userSelection: UserSelectionConfig,
    val authentiktUserSource: AuthentiktUserSource<USER>,
    val findNextStepCallback: FindNextStepCallback,
    val apiPrefix: String,
    val installedPlugins: Set<BasePlugin<*>>
)
