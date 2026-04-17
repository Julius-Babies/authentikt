package es.jvbabi.authentikt.core.routes.flow.start

import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.sessions
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun <USER> Route.startFlow(configuration: AuthentiktConfiguration<USER>) {
    get {
        val session = Session(configuration)
        sessions[session.sessionId] = session

        call.respond(
            mapOf("session_id" to session.sessionId),
        )
    }
}
