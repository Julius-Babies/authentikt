package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.config.AuthentiktPluginConfigurationBuilder
import es.jvbabi.authentikt.core.routes.flow.check.checkFlowStatus
import es.jvbabi.authentikt.core.routes.flow.email.loginEmail
import es.jvbabi.authentikt.core.routes.flow.password.password
import es.jvbabi.authentikt.core.routes.flow.start.startFlow
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.session.sessions
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

internal lateinit var authentiktPluginConfiguration: AuthentiktConfiguration<*>

fun <USER> Application.installAuthentikt(block: AuthentiktPluginConfigurationBuilder<USER>.() -> Unit) {
    val builder = AuthentiktPluginConfigurationBuilder<USER>().apply(block)
    val configuration = builder.build()
    authentiktPluginConfiguration = configuration

    routing {
        route("${configuration.apiPrefix}/authentikt") {
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

                    route("/check") { checkFlowStatus(configuration) }

                    route("/email") { loginEmail(configuration) }

                    route("/password") { password() }
                }
            }
        }
    }
}
