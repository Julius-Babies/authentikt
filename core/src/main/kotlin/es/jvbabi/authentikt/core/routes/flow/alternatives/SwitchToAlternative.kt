package es.jvbabi.authentikt.core.routes.flow.alternatives

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Replaces the active step with one of its alternatives.
 *
 * Answers `409` if the requested step is not an alternative of the active step, for example because a
 * concurrent request has already switched or completed it.
 */
internal fun <USER> Route.switchToAlternative() {
    post {
        val session = call.attributes[SessionKey] as Session<USER>
        val request = call.receive<SwitchToAlternativeRequest>()

        if (!session.switchToAlternative(request.namespace)) {
            call.respondGson(
                value = buildGenericMap {
                    put("error", "alternative_not_available")
                    put("error_description", "The step is not an alternative of the active step.")
                },
                status = HttpStatusCode.Conflict,
            )
            return@post
        }

        call.respondGson(buildGenericMap {
            put("type", "success")
        })
    }
}

@Serializable
internal data class SwitchToAlternativeRequest(
    @SerialName("namespace") val namespace: String,
)
