package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.AuthentiktUserSource

class AuthentiktConfiguration<USER>(
    val userSelection: UserSelectionConfig,
    val authentiktUserSource: AuthentiktUserSource<USER>,
    val apiPrefix: String,
    val onSuccess: suspend (user: AuthentiktUser<USER>) -> Unit,
)
