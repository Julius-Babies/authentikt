package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktPluginConfiguration
import es.jvbabi.authentikt.core.routes.flow.start.startFlowRoute
import io.ktor.server.application.*
import io.ktor.server.routing.*

val Authentikt = createApplicationPlugin(
    name = "Authentikt",
    createConfiguration = ::AuthentiktPluginConfiguration,
) {
    this.application.routing {
        route("/authentikt") {
            route("/flow") {
                route("/start") {
                    startFlowRoute()
                }
            }
        }
    }
}
