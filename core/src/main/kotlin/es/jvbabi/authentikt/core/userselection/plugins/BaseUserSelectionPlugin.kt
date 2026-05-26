package es.jvbabi.authentikt.core.userselection.plugins

import es.jvbabi.authentikt.core.AuthentiktInstance
import es.jvbabi.authentikt.core.session.Session
import io.ktor.server.routing.Route
import org.slf4j.LoggerFactory

/**
 * Base class for pluggable user-identification strategies.
 *
 * User-selection plugins handle the first stage of authentication where the
 * user identifies themselves (e.g. by email address, username, or phone number).
 *
 * Once the user is identified, the session's `identifiedUser` is set and the
 * flow proceeds to step plugins returned by the authorization callback.
 *
 * @param namespace A unique reference to the plugin. It is recommended to use
 *   a reverse-domain syntax (`com.example.authentikt.email-selection`) or a
 *   group/project syntax like `example/email-selection`.
 */
abstract class BaseUserSelectionPlugin<USER>(
    val namespace: String,
) {
    protected val logger = LoggerFactory.getLogger(namespace)

    /**
     * Installs all Ktor routes required for this user-selection strategy.
     *
     * Routes are mounted under `/authentikt/flow/{sessionId}/user-selection/plugins/{namespace}/`.
     *
     * @param inRoute the Ktor [Route] scoped to this plugin's namespace.
     */
    abstract fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>)

    /**
     * Installs static routes that are not tied to a specific session.
     *
     * These routes are mounted under `/authentikt/static/plugins/{namespace}/`.
     */
    open fun installStaticRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {}

    /**
     * Returns client-discoverable metadata about this user-selection step.
     *
     * The returned map is included in the flow-check response so that the frontend
     * can render the appropriate input fields.
     */
    abstract suspend fun createClientState(session: Session<USER>): Map<String, Any?>
}
