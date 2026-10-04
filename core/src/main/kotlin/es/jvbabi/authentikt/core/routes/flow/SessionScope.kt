package es.jvbabi.authentikt.core.routes.flow

import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.session.findActiveSession
import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.http.*
import io.ktor.server.application.*

/**
 * Hook that wraps the remaining call pipeline, so the handler can run code before and after it.
 */
private object AroundCall : Hook<suspend (call: ApplicationCall, proceed: suspend () -> Unit) -> Unit> {
    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (call: ApplicationCall, proceed: suspend () -> Unit) -> Unit,
    ) {
        pipeline.intercept(ApplicationCallPipeline.Plugins) {
            handler(call) { proceed() }
            if (call.isHandled) finish()
        }
    }
}

/**
 * Resolves the session from the `sessionId` path parameter and stores it in [SessionKey].
 *
 * The rest of the call runs while holding the session lock, so requests for the same session are processed
 * one after another. Unknown or expired sessions are answered with `404`.
 */
internal val SessionScope = createRouteScopedPlugin("Authentikt Session Scope") {
    on(AroundCall) { call, proceed ->
        val session = findActiveSession(call.parameters["sessionId"])
            ?: return@on call.respondSessionNotFound()

        session.withLock {
            // The session may have been completed or invalidated while this request waited for the lock
            if (sessions[session.sessionId] !== session) return@withLock call.respondSessionNotFound()

            call.attributes[SessionKey] = session
            proceed()
        }
    }
}

private suspend fun ApplicationCall.respondSessionNotFound() = respondGson(
    value = buildGenericMap {
        put("error", "session_not_found")
        put("error_description", "The session does not exist or has expired.")
    },
    status = HttpStatusCode.NotFound,
)

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
