package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.AuthentiktInstance
import es.jvbabi.authentikt.core.ratelimit.RateLimit
import es.jvbabi.authentikt.core.ratelimit.RateLimiter
import es.jvbabi.authentikt.core.ratelimit.respondRateLimited
import es.jvbabi.authentikt.core.ratelimit.triesPer
import es.jvbabi.authentikt.core.routes.flow.respondStepNotActive
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.BaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

/**
 * Password verification step plugin.
 *
 * Validates the user's password against a configurable check function.
 * On success, marks the step as completed and advances the flow.
 *
 * Failed attempts are limited per user, see [PasswordPluginConfigurationBuilder.rateLimit].
 *
 * ### Usage
 * ```kotlin
 * install(PasswordPlugin {
 *     rateLimit = 3 triesPer 3.minutes
 *     checkPassword { user, password -> passwordHasher.verify(user, password) }
 * })
 * ```
 *
 * @param configuration lambda that configures password checking.
 */
class PasswordPlugin<USER>(
    configuration: PasswordPluginConfigurationBuilder<USER>.() -> Unit
) : BasePlugin<USER, PasswordState>(
    namespace = "authentikt-builtin/password",
) {
    private val configuration = PasswordPluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    private val rateLimiter = this.configuration.rateLimit?.let { RateLimiter.perUser(it) }

    override suspend fun createState(session: Session<*>): PasswordState {
        return PasswordState(isValidated = false, rateLimiter = rateLimiter)
    }

    override fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {
        with(inRoute) {
            post {
                val session = call.attributes[SessionKey] as Session<USER>
                if (!session.isActive(this@PasswordPlugin)) return@post call.respondStepNotActive()
                val request = call.receive<PasswordRequest>()

                val attempt = rateLimiter?.tryAcquire(session)
                if (attempt != null && !attempt.allowed) {
                    return@post call.respondRateLimited(attempt.status, buildGenericMap { put("success", false) })
                }

                val isValid = configuration.checkPassword(session.identifiedUser!!.user, request.password)

                if (isValid) {
                    rateLimiter?.reset(session)
                    if (!session.completeStep(this@PasswordPlugin, PasswordState(true, rateLimiter))) {
                        return@post call.respondStepNotActive()
                    }
                }

                call.respondGson(buildGenericMap {
                    put("success", isValid)
                    if (!isValid) put("rate_limit", attempt?.status?.toClientState())
                })
            }
        }
    }
}

/**
 * State for the password step.
 *
 * @param isValidated whether the password was successfully verified.
 * @param rateLimiter limits failed attempts, or `null` if attempts are not limited.
 */
data class PasswordState(
    val isValidated: Boolean,
    val rateLimiter: RateLimiter? = null,
) : BaseState {
    override suspend fun isCompleted(): Boolean = this.isValidated

    override suspend fun createClientState(session: Session<*>): Map<String, Any?> = buildGenericMap {
        put("validated", this@PasswordState.isValidated)
        put("rate_limit", rateLimiter?.status(session)?.toClientState())
    }
}

@Serializable
internal data class PasswordRequest(
    @SerialName("password") val password: String
)

/**
 * DSL builder for [PasswordPlugin] configuration.
 */
class PasswordPluginConfigurationBuilder<USER> {
    typealias CheckPassword<USER> = suspend (user: USER, password: String) -> Boolean

    private var checkPassword: CheckPassword<USER>? = null

    /**
     * Failed attempts allowed per user, e.g. `3 triesPer 3.minutes`. Defaults to 5 tries per 5 minutes.
     * Set to `null` to disable the limit.
     */
    var rateLimit: RateLimit? = 5 triesPer 5.minutes

    /**
     * Sets the password verification callback.
     *
     * @param checkPassword suspending function that receives the user object and
     *   the submitted password, returning `true` if the password is valid.
     */
    fun checkPassword(checkPassword: CheckPassword<USER>) {
        this.checkPassword = checkPassword
    }

    internal fun build(): PasswordPluginConfiguration<USER> {
        requireNotNull(checkPassword) { "checkPassword must be provided" }

        return PasswordPluginConfiguration(
            checkPassword = this.checkPassword!!,
            rateLimit = rateLimit,
        )
    }
}

internal data class PasswordPluginConfiguration<USER>(
    val checkPassword: PasswordPluginConfigurationBuilder.CheckPassword<USER>,
    val rateLimit: RateLimit?,
)
