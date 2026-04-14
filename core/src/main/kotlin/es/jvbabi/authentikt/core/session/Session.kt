package es.jvbabi.authentikt.core.session

import kotlin.uuid.Uuid

typealias SessionId = String

val sessions = mutableMapOf<SessionId, Session>()

class Session {
    val sessionId: SessionId = (1..3).joinToString("") { Uuid.random().toHexString() }
}