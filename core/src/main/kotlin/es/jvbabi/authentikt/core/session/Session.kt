package es.jvbabi.authentikt.core.session

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.step.StepState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import io.ktor.util.AttributeKey
import kotlin.uuid.Uuid

typealias SessionId = String

val SessionKey = AttributeKey<Session>("Session")

val sessions = mutableMapOf<SessionId, Session>()

class Session {
    val sessionId: SessionId = (1..3).joinToString("") { Uuid.random().toHexString() }

    var identifiedUser: AuthentiktUser<*>? = null

    val authenticationSteps = mutableListOf<Pair<BasePlugin<*>, StepState>>()

    /**
     * Checks if a session has a step already taken or completed.
     */
    suspend fun has(plugin: BasePlugin<*>, needsCompletion: Boolean = true): Boolean {
        val stepForPlugin = this.authenticationSteps.firstOrNull { it.first == plugin } ?: return false
        return !needsCompletion || stepForPlugin.second.isCompleted()
    }
}