package es.jvbabi.authentikt.core.session

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.routes.flow.check.NotInstalledPluginCalled
import es.jvbabi.authentikt.core.step.BaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import io.ktor.util.AttributeKey
import kotlin.uuid.Uuid

typealias SessionId = String

val SessionKey = AttributeKey<Session<*>>("Session")

val sessions = mutableMapOf<SessionId, Session<*>>()

class Session<USER>(
    private val configuration: AuthentiktConfiguration<USER>
) {
    val sessionId: SessionId = (1..3).joinToString("") { Uuid.random().toHexString() }

    var identifiedUser: AuthentiktUser<USER>? = null

    val authenticationSteps = mutableListOf<Pair<BasePlugin<*>, BaseState>>()

    /**
     * Checks if a session has a step already taken or completed.
     */
    suspend fun has(plugin: BasePlugin<*>, needsCompletion: Boolean = true): Boolean {
        val stepForPlugin = this.authenticationSteps.firstOrNull { it.first == plugin } ?: return false
        return !needsCompletion || stepForPlugin.second.isCompleted()
    }

    fun pop() {
        if (authenticationSteps.isEmpty()) identifiedUser = null
        else authenticationSteps.removeLast()
    }

    suspend fun nextStep() {
        val user = this.identifiedUser ?: return

        val nextStep = configuration.findNextStepCallback(this, user)

        if (nextStep !in configuration.installedPlugins)
            throw NotInstalledPluginCalled(nextStep, this)

        val data = nextStep.createState(this)
        this.authenticationSteps.add(nextStep to data)
    }

}
