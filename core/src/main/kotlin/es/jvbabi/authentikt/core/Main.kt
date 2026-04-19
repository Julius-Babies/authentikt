package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.config.AuthentiktPluginConfigurationBuilder
import es.jvbabi.authentikt.core.routes.flow.check.checkFlowStatus
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.session.sessions
import io.ktor.server.application.*
import io.ktor.server.routing.*

internal lateinit var authentiktPluginConfiguration: AuthentiktConfiguration<*>

fun <USER> Application.installAuthentikt(
    block: AuthentiktPluginConfigurationBuilder<USER>.() -> Unit
): AuthentiktInstance<USER> {

    val builder = AuthentiktPluginConfigurationBuilder<USER>()
        .apply(block)

    val configuration = builder.build()
    authentiktPluginConfiguration = configuration

    routing {
        route("${configuration.apiPrefix}/authentikt") {
            route("/flow") {
                route("/{sessionId}") sessionScopedRoute@{
                    createRouteScopedPlugin("Get Session from Path") {
                        onCall { call ->
                            val sessionId = call.parameters["sessionId"]
                            val session = sessions[sessionId]!!
                            call.attributes[SessionKey] = session
                        }
                    }.let { this@sessionScopedRoute.install(it) }

                    route("/check") { checkFlowStatus(configuration) }

                    route("/user-selection/plugins") {
                        configuration.installedUserSelectionPlugins.forEach { plugin ->
                            route("/${plugin.namespace}") pluginScopedRoute@{
                                plugin.installRoutes(this@pluginScopedRoute)
                            }
                        }
                    }

                    route("/steps/plugins") {
                        configuration.installedPlugins.forEach { plugin ->
                            route("/${plugin.namespace}") pluginScopedRoute@{
                                plugin.installRoutes(this@pluginScopedRoute)
                            }
                        }
                    }
                }
            }
        }
    }

    return AuthentiktInstance(
        configuration = configuration,
    )
}
