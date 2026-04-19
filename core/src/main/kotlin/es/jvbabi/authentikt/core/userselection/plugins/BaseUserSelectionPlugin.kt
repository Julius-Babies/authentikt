package es.jvbabi.authentikt.core.userselection.plugins

import io.ktor.server.routing.Route

/**
 * @param namespace A unique reference to the plugin that should at least be stable while the application is running.
 * It is recommended to use a reverse-domain syntax (com.example.authentikt.email-selection) or a group/project syntax
 * like example/email-selection.
 */
abstract class BaseUserSelectionPlugin<USER>(
    val namespace: String,
) {
    /**
     * Install all the routes necessary for this user-selection strategy.
     */
    abstract fun installRoutes(inRoute: Route)

    /**
     * Convert user-selection request payload state into frontend discoverable metadata.
     */
    abstract suspend fun createClientState(): Map<String, Any?>
}
