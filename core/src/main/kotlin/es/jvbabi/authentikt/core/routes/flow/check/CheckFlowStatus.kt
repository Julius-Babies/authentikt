package es.jvbabi.authentikt.core.routes.flow.check

import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.config.UserSelectionEmailConfig
import es.jvbabi.authentikt.core.config.UserSelectionUsernameConfig
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.server.routing.*

internal fun Route.checkFlowStatus(configuration: AuthentiktConfiguration<*>) {
    get {
        val session = call.attributes[SessionKey]

        val user = session.identifiedUser

        if (user == null) {
            buildGenericMap {
                put("state", buildGenericMap {
                    put("type", "user_selection")
                    put("email", buildGenericMap {
                        val config = configuration.userSelection.emailConfig
                        val isEnabled = config is UserSelectionEmailConfig.Enabled
                        put("enabled", isEnabled)

                        if (isEnabled) {
                            put("with_username", config.withUsername)
                        }
                    })

                    put("username", buildGenericMap {
                        val config = configuration.userSelection.usernameConfig
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

        val currentStep = session.authenticationSteps.lastOrNull()

        val (stepForUser, data) = if (currentStep == null || currentStep.second.isCompleted()) {
            val nextStep = configuration.findNextStepCallback(session, user)

            if (nextStep !in configuration.installedPlugins)
                throw NotInstalledPluginCalled(nextStep, session)

            val data = nextStep.createState(session)
            session.authenticationSteps.add(nextStep to data)

            nextStep to data
        } else currentStep

        call.respondGson(buildGenericMap {
            put("type", "step")
            put("namespace", stepForUser.namespace)
            put("payload", data.createClientState(session))
        })
    }
}

class NotInstalledPluginCalled(val plugin: BasePlugin<*>, val session: Session): Exception(buildString {
    append("Plugin ${plugin.namespace} has been selected in session ${session.sessionId} but was not installed in ")
    append("Authentikt. Please call install(yourPlugin) in installAuthentikt { ... } first.")
})