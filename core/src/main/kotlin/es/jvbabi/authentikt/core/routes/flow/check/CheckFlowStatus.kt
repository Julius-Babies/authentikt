package es.jvbabi.authentikt.core.routes.flow.check

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionDestination
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.server.routing.*

internal fun <USER> Route.checkFlowStatus() {
    get {
        val session = call.attributes[SessionKey] as Session<USER>
        if (session.authenticationSteps.isEmpty()) session.nextStep()

        val (stepForUser, data) = session.authenticationSteps.last()

        call.respondGson(buildGenericMap {
            put("type", "step")
            put("namespace", stepForUser.namespace)
            put("payload", data.createClientState(session))
            put("attributes", session.getPublicAttributes())
            put("destination", buildGenericMap {
                when (session.destination) {
                    null -> {
                        put("type", "none")
                    }
                    is SessionDestination.DeviceFlow -> {
                        put("type", "device_flow")
                        put("application_id", session.destination.applicationId)
                        put("application_name", session.destination.applicationName)
                    }
                    is SessionDestination.OAuth -> {
                        put("type", "oauth")
                        put("application_id", session.destination.applicationId)
                        put("application_name", session.destination.applicationName)
                        put("redirect_uri", session.destination.redirectUri)
                    }
                }
            })
        })
    }
}

class NotInstalledPluginCalled(val plugin: BasePlugin<*, *>, val session: Session<*>): Exception(buildString {
    append("Plugin ${plugin.namespace} has been selected in session ${session.sessionId} but was not installed in ")
    append("Authentikt. Please call install(yourPlugin) in installAuthentikt { ... } first.")
})
