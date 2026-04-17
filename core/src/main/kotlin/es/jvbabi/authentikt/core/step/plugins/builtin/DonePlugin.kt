package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.EmptyBaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import io.ktor.server.response.respondText
import io.ktor.server.routing.*

object DonePlugin: BasePlugin<EmptyBaseState>(
    namespace = "authentikt-builtin/done",
) {
    override suspend fun createState(session: Session<*>): EmptyBaseState {
        return EmptyBaseState()
    }

    override fun installRoutes(inRoute: Route) {
        with(inRoute) {
            get("/test") { call.respondText("Hello from done-plugin: ${this@DonePlugin.namespace}") }
        }
    }
}
