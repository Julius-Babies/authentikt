package es.jvbabi.authentikt.core.session

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.routes.flow.check.NotInstalledPluginCalled
import es.jvbabi.authentikt.core.step.BaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import io.ktor.util.AttributeKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant
import kotlin.uuid.Uuid

typealias SessionId = String

class SessionAttributeScope(
    private val storage: MutableMap<AttributeKey<*>, Any?>
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: AttributeKey<T>): T? = storage[key] as? T

    operator fun <T : Any> set(key: AttributeKey<T>, value: T) {
        storage[key] = value
    }
}

class PublicSessionAttributeScope(
    private val storage: MutableMap<AttributeKey<*>, Any?>
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: AttributeKey<T>): T? = storage[key] as? T

    operator fun <T : Any> set(key: AttributeKey<T>, value: T) {
        storage[key] = value
    }
}

val SessionKey = AttributeKey<Session<*>>("Session")

val sessions: MutableMap<SessionId, Session<*>> = ConcurrentHashMap()

/**
 * Returns the session with the given [sessionId] and records activity on it.
 *
 * Expired sessions are removed from [sessions] and `null` is returned.
 */
internal fun findActiveSession(sessionId: SessionId?): Session<*>? {
    if (sessionId == null) return null
    val session = sessions[sessionId] ?: return null
    if (session.isExpired()) {
        session.invalidate()
        return null
    }
    session.touch()
    return session
}

/**
 * Removes all expired sessions from [sessions].
 */
internal fun removeExpiredSessions() {
    sessions.values.removeIf { it.isExpired() }
}

/**
 * Represents a single authentication session.
 *
 * A session is created when the client calls `POST /authentikt/login`.
 * It tracks:
 * - The identified user ([identifiedUser]) once a user-selection step completes.
 * - A stack of completed authentication steps ([authenticationSteps]).
 * - Its lifetime ([createdAt], [lastActivityAt], [expiresAt]).
 *
 * Regular sessions expire after [AuthentiktConfiguration.sessionTimeout] without activity.
 * Device flow sessions expire [es.jvbabi.authentikt.core.config.OAuthConfiguration.deviceCodeLifetime]
 * after creation. Expired sessions are removed lazily on access and periodically in the background.
 *
 * @param configuration the resolved configuration for this session.
 */
class Session<USER>(
    private val configuration: AuthentiktConfiguration<USER>,
    val destination: SessionDestination?,
) {
    val sessionId: SessionId = (1..3).joinToString("") { Uuid.random().toHexString() }

    var identifiedUser: AuthentiktUser<USER>? = null

    val createdAt: Instant = configuration.clock.now()

    @Volatile
    var lastActivityAt: Instant = createdAt
        private set

    /**
     * The point in time after which this session is no longer usable.
     */
    val expiresAt: Instant
        get() = when (destination) {
            is SessionDestination.DeviceFlow -> createdAt + configuration.oAuthConfiguration!!.deviceCodeLifetime
            else -> lastActivityAt + configuration.sessionTimeout
        }

    fun isExpired(): Boolean = configuration.clock.now() >= expiresAt

    internal fun touch() {
        lastActivityAt = configuration.clock.now()
    }

    /**
     * Removes this session from the session store. Subsequent requests for it are answered with `404`.
     */
    fun invalidate() {
        sessions.remove(sessionId, this)
    }

    val authenticationSteps = mutableListOf<Pair<BasePlugin<USER, *>, BaseState>>()

    private val _privateAttributes = mutableMapOf<AttributeKey<*>, Any?>()
    private val _publicAttributes = mutableMapOf<AttributeKey<*>, Any?>()

    val attributes = SessionAttributeScope(_privateAttributes)
    val publicAttributes = PublicSessionAttributeScope(_publicAttributes)

    /**
     * Returns all public attributes currently stored on this session, keyed by the attribute name.
     */
    fun getPublicAttributes(): Map<String, Any?> = _publicAttributes.mapKeys { it.key.name }

    /**
     * Checks whether this session has already executed (and optionally completed) the given [plugin].
     *
     * @param plugin the plugin to check.
     * @param needsCompletion when `true`, only returns `true` if the step has completed.
     */
    suspend fun has(plugin: BasePlugin<USER, *>, needsCompletion: Boolean = true): Boolean {
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
        val nextStep = configuration.findNextStepCallback(this)

        if (nextStep !in configuration.installedPlugins)
            throw NotInstalledPluginCalled(nextStep, this)

        val data = nextStep.createState(this)
        this.authenticationSteps.add(nextStep to data)
    }

}
