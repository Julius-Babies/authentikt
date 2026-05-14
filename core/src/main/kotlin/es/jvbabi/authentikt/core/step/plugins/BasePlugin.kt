package es.jvbabi.authentikt.core.step.plugins

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.BaseState
import io.ktor.server.routing.Route

/**
 * Base class for pluggable authentication steps.
 *
 * Step plugins represent individual stages in the auth flow such as
 * password verification, TOTP validation, or token generation ("done").
 *
 * Each plugin:
 * - Has a unique [namespace] that identifies it in the flow.
 * - Creates initial [BaseState] per session via [createState].
 * - Installs Ktor routes via [installRoutes] that handle step-specific
 *   API calls (e.g. POST to validate credentials).
 *
 * @param namespace A unique reference to the plugin that should at least be stable
 *   while the application is running. It is recommended to use a reverse-domain
 *   syntax (`com.example.authentikt.secure-auth`) or a group/project syntax
 *   like `example/secure-auth`.
 */
abstract class BasePlugin<STATE : BaseState>(
    val namespace: String,
) {
    /**
     * Creates the initial state for this step when it is first entered.
     *
     * Called by the session when the authorization callback returns this plugin
 * as the next step.
     *
     * @param session the active auth session.
     * @return a fresh [STATE] instance for this auth flow.
     */
    abstract suspend fun createState(session: Session<*>): STATE

    /**
     * Installs all Ktor routes required by this plugin.
     *
     * Routes are mounted under `/authentikt/flow/{sessionId}/steps/plugins/{namespace}/`.
     * Use `call.attributes[SessionKey]` to obtain the current [Session].
     *
     * @param inRoute the Ktor [Route] scoped to this plugin's namespace.
     */
    abstract fun installRoutes(inRoute: Route)
}
