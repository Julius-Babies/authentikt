package es.jvbabi.authentikt.core.step

import es.jvbabi.authentikt.core.session.Session

interface BaseState {
    suspend fun isCompleted(): Boolean

    /**
     * Convert a step-state into something to be sent to the authorization client (e.g. Web-Flow) so that it can
     * display information. The plugin-author is responsible for (de)serialization.
     */
    suspend fun createClientState(session: Session): Map<String, Any?>
}

class EmptyBaseState : BaseState {
    override suspend fun isCompleted(): Boolean = true
    override suspend fun createClientState(session: Session): Map<String, Any?> = emptyMap()
}