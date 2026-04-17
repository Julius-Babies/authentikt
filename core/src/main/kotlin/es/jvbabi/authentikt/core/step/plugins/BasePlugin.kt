package es.jvbabi.authentikt.core.step.plugins

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.BaseState
import io.ktor.server.routing.Route

/**
 * @param namespace A unique reference to the plugin that should at least be stable while the application is running.
 * It is recommended to use a reverse-domain syntax (com.example.authentikt.secure-auth) or a group/project syntax like
 * example/secure-auth.
 */
abstract class BasePlugin<STATE: BaseState>(
    val namespace: String,
) {
    /**
     * Create an initial state to push to session stack.
     */
    abstract suspend fun createState(session: Session): STATE

    /**
     * Install all the routes necessary for the plugin. Use `call.attributes[es.jvbabi.authentikt.core.session.SessionKey]`
     * to obtain the current session.
     */
    abstract fun installRoutes(inRoute: Route)
}