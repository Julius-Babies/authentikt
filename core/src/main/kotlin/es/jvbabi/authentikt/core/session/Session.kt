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

/**
 * Represents a single authentication session.
 *
 * A session is created when the client calls `POST /authentikt/login`.
 * It tracks:
 * - The identified user ([identifiedUser]) once a user-selection step completes.
 * - A stack of completed authentication steps ([authenticationSteps]).
 *
 * @param configuration the resolved configuration for this session.
 */
class Session<USER>(
    private val configuration: AuthentiktConfiguration<USER>
) {
    val sessionId: SessionId = (1..3).joinToString("") { Uuid.random().toHexString() }

    var identifiedUser: AuthentiktUser<USER>? = null

    val authenticationSteps = mutableListOf<Pair<BasePlugin<*>, BaseState>>()

    /**
     * Checks whether this session has already executed (and optionally completed) the given [plugin].
     *
     * @param plugin the plugin to check.
     * @param needsCompletion when `true`, only returns `true` if the step has completed.
     */
    suspend fun has(plugin: BasePlugin<*>, needsCompletion: Boolean = true): Boolean {
        val stepForPlugin = this.authenticationSteps.firstOrNull { it.first == plugin } ?: return false
        return !needsCompletion || stepForPlugin.second.isCompleted()
    }

    fun pop() {
        if (authenticationSteps.isEmpty()) identifiedUser = null
        else authenticationSteps.removeLast()
    }

    /**
     * Advances the flow to the next step.
     *
 * Calls the configured authorization callback to determine the next plugin,
 * creates its initial state, and pushes it onto the step stack.
 *
 * @throws NotInstalledPluginCalled if the returned plugin was not installed.
     */
    suspend fun nextStep() {
        val user = this.identifiedUser ?: return

        val nextStep = configuration.findNextStepCallback(this, user)

        if (nextStep !in configuration.installedPlugins)
            throw NotInstalledPluginCalled(nextStep, this)

        val data = nextStep.createState(this)
        this.authenticationSteps.add(nextStep to data)
    }

}
