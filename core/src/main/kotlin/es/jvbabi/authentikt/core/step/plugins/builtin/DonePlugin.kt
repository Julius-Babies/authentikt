package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.EmptyStepState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import io.ktor.server.response.respondText
import io.ktor.server.routing.*

object DonePlugin: BasePlugin<EmptyStepState>(
    namespace = "authentikt-builtin/done",
) {
    override suspend fun createState(session: Session): EmptyStepState {
        return EmptyStepState()
    }

    override fun installRoutes(inRoute: Route) {
        with(inRoute) {
            get("/test") { call.respondText("Hello from done-plugin: ${this@DonePlugin.namespace}") }
        }
    }
}