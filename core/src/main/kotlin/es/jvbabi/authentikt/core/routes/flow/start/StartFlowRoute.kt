package es.jvbabi.authentikt.core.routes.flow.start

import es.jvbabi.authentikt.core.session.Session
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.startFlowRoute() {
    get {
        val session = Session()

        call.respond(
            mapOf("session_id" to session.sessionId),
        )
    }
}