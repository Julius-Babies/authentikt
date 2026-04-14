package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktPluginConfiguration
import es.jvbabi.authentikt.core.routes.flow.check.checkFlowStatus
import es.jvbabi.authentikt.core.routes.flow.email.loginEmail
import es.jvbabi.authentikt.core.routes.flow.password.password
import es.jvbabi.authentikt.core.routes.flow.start.startFlow
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.session.sessions
import io.ktor.server.application.*
import io.ktor.server.routing.*

internal lateinit var authentiktPluginConfiguration: AuthentiktPluginConfiguration

val Authentikt = createApplicationPlugin(
    name = "Authentikt",
    createConfiguration = ::AuthentiktPluginConfiguration,
) {
    this.pluginConfig.validate()
    authentiktPluginConfiguration = this.pluginConfig

    this.application.routing {
        route("${authentiktPluginConfiguration.apiPrefix}/authentikt") {
            route("/flow") {
                route("/start") {
                    startFlow()
                }

                route("/{sessionId}") sessionScopedRoute@{
                    createRouteScopedPlugin("Get Session from Path") {
                        onCall { call ->
                            val sessionId = call.parameters["sessionId"]
                            val session = sessions[sessionId]!!
                            call.attributes[SessionKey] = session
                        }
                    }.let { this@sessionScopedRoute.install(it) }

                    route("/check") { checkFlowStatus() }

                    route("/email") { loginEmail() }

                    route("/password") { password() }
                }
            }
        }
    }
}
