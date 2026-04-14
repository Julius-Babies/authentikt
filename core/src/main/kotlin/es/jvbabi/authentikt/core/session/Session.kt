package es.jvbabi.authentikt.core.session

import es.jvbabi.authentikt.core.AuthentiktUser
import io.ktor.util.AttributeKey
import kotlin.uuid.Uuid

typealias SessionId = String

val SessionKey = AttributeKey<Session>("Session")

val sessions = mutableMapOf<SessionId, Session>()

class Session {
    val sessionId: SessionId = (1..3).joinToString("") { Uuid.random().toHexString() }

    var identifiedUser: AuthentiktUser? = null

    val authenticationSteps = mutableListOf<AuthenticationStep>()
}

sealed class AuthenticationStep(
    val type: Type
) {
    enum class Type {
        Primary,
        Secondary,
    }
}

class PasswordAuthenticationStep() : AuthenticationStep(
    type = Type.Primary
)