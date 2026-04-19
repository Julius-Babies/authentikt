package es.jvbabi.authentikt.core.routes.flow.check

import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.server.routing.*

internal fun <USER> Route.checkFlowStatus(configuration: AuthentiktConfiguration<USER>) {
    get {
        val session = call.attributes[SessionKey] as Session<USER>

        val user = session.identifiedUser

        if (user == null) {
            call.respondGson(buildGenericMap {
                put("type", "user_selection")
                put("plugins", configuration.installedUserSelectionPlugins.map { plugin ->
                    buildGenericMap {
                        put("namespace", plugin.namespace)
                        put("payload", plugin.createClientState())
                    }
                })
            })

            return@get
        }

        val (stepForUser, data) = session.authenticationSteps.last()

        call.respondGson(buildGenericMap {
            put("type", "step")
            put("namespace", stepForUser.namespace)
            put("payload", data.createClientState(session))
        })
    }
}

class NotInstalledPluginCalled(val plugin: BasePlugin<*>, val session: Session<*>): Exception(buildString {
    append("Plugin ${plugin.namespace} has been selected in session ${session.sessionId} but was not installed in ")
    append("Authentikt. Please call install(yourPlugin) in installAuthentikt { ... } first.")
})
