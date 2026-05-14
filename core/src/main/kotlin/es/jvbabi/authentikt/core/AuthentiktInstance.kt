package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.sessions

/**
 * The runtime instance returned by [installAuthentikt].
 *
 * Holds the resolved [configuration] and provides methods to create new auth sessions.
 */
data class AuthentiktInstance<USER>(
    val configuration: AuthentiktConfiguration<USER>,
) {
    /**
     * Creates a new auth session linked to this instance's [configuration].
     * The session is automatically registered in the global session map.
     */
    fun createNewSession(): Session<USER> {
        val session = Session(configuration)
        sessions[session.sessionId] = session
        return session
    }
}
