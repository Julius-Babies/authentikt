package es.jvbabi.authentikt.core.step

import es.jvbabi.authentikt.core.session.Session

/**
 * Represents the state of a single authentication step within a session.
 *
 * Each [es.jvbabi.authentikt.core.step.plugins.BasePlugin] implementation pairs
 * with a concrete [BaseState] subclass
 * that tracks whether the step has been completed and what data to expose to
 * the client (frontend).
 */
interface BaseState {
    /**
     * Whether this step has been successfully completed.
     * When `true`, the flow will proceed to the next step via the find-next-step callback.
     */
    suspend fun isCompleted(): Boolean

    /**
     * Serialises this step's state into a map that is sent to the frontend client.
     *
     * The returned map is included in the flow-check response and can be read
     * by the client-side plugin to display relevant UI (e.g. prompting for TOTP or password).
     */
    suspend fun createClientState(session: Session<*>): Map<String, Any?>
}

/**
 * A trivial [BaseState] that is already completed and produces no client-facing data.
 */
class EmptyBaseState : BaseState {
    override suspend fun isCompleted(): Boolean = true
    override suspend fun createClientState(session: Session<*>): Map<String, Any?> = emptyMap()
}
