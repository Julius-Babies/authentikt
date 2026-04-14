package es.jvbabi.authentikt.core.routes.flow.check

import es.jvbabi.authentikt.core.authentiktPluginConfiguration
import es.jvbabi.authentikt.core.config.UserSelectionEmailConfig
import es.jvbabi.authentikt.core.config.UserSelectionUsernameConfig
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.checkFlowStatus() {
    get {
        val session = call.attributes[SessionKey]

        if (session.identifiedUser == null) {

            buildGenericMap {
                put("state", buildGenericMap {
                    put("type", "user_selection")
                    put("email", buildGenericMap {
                        val config = authentiktPluginConfiguration.userSelection.emailConfig
                        val isEnabled = config is UserSelectionEmailConfig.Enabled
                        put("enabled", isEnabled)

                        if (isEnabled) {
                            put("with_username", config.withUsername)
                        }
                    })

                    put("username", buildGenericMap {
                        val config = authentiktPluginConfiguration.userSelection.usernameConfig
                        val isEnabled = config is UserSelectionUsernameConfig.Enabled
                        put("enabled", isEnabled)
                        if (config is UserSelectionUsernameConfig.Enabled) {
                            put("with_email", config.withEmail)
                        }
                    })
                })
            }.let { call.respondGson(it) }

            return@get
        }

        println(session.sessionId)
    }
}
