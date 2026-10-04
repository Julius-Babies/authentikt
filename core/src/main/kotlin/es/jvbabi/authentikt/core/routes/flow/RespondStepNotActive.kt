package es.jvbabi.authentikt.core.routes.flow

import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.http.*
import io.ktor.server.application.*

/**
 * Answers a request for a step that is not the session's active step, for example a duplicate submission.
 */
suspend fun ApplicationCall.respondStepNotActive() = respondGson(
    value = buildGenericMap {
        put("error", "step_not_active")
        put("error_description", "This step is not the active step of the session.")
    },
    status = HttpStatusCode.Conflict,
)
