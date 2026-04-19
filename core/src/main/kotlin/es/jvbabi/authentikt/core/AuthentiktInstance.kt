package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.sessions

data class AuthentiktInstance<USER>(
    val configuration: AuthentiktConfiguration<USER>,
) {
    fun createNewSession(): Session<USER> {
        val session = Session(configuration)
        sessions[session.sessionId] = session
        return session
    }
}
