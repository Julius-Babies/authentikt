package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.AuthentiktInstance
import es.jvbabi.authentikt.core.AuthentiktUser
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
 * Email-based user identification plugin.
 *
 * Presents an email input field to the user. On submission, looks up the user
 * by email via the configured `findUserByEmail` callback and sets the session's
 * identified user if found.
 *
 * Lookups without a match are limited per session, see [EmailUserSelectionPluginConfigurationBuilder.rateLimit].
 *
 * ### Usage
 * ```kotlin
 * install(EmailUserSelectionPlugin {
 *     rateLimit = 10 triesPer 1.minutes
 *     findUserByEmail { email -> userRepository.findByEmail(email) }
 *     withUsername = true  // optionally request username after email
 * })
 * ```
 *
 * @param configuration lambda that configures user lookup.
 */
class EmailUserSelectionPlugin<USER>(
    configuration: EmailUserSelectionPluginConfigurationBuilder<USER>.() -> Unit,
) : BasePlugin<USER, EmailSelectionPluginState>(
    namespace = "authentikt-builtin/email"
) {
    private val configuration = EmailUserSelectionPluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    private val rateLimiter = this.configuration.rateLimit?.let { RateLimiter.perSession(it) }

    override fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {
        with(inRoute) {
            post {
                val session = call.attributes[SessionKey] as Session<USER>
                if (!session.isActive(this@EmailUserSelectionPlugin)) return@post call.respondStepNotActive()
                val request = call.receive<LoginEmailRequest>()

                val attempt = rateLimiter?.tryAcquire(session)
                if (attempt != null && !attempt.allowed) {
                    return@post call.respondRateLimited(attempt.status, buildGenericMap { put("type", "rate_limited") })
                }

                val user = configuration.findUserByEmail(request.email)

                if (user == null) {
                    call.respondGson(buildGenericMap {
                        put("type", "user_not_found")
                        put("rate_limit", attempt?.status?.toClientState())
                    })

                    return@post
                }

                rateLimiter?.reset(session)
                val completed = session.completeStep(
                    plugin = this@EmailUserSelectionPlugin,
                    state = EmailSelectionPluginState(
                        withUsername = configuration.withUsername,
                        hasUser = true,
                        rateLimiter = rateLimiter,
                    ),
                ) { identifiedUser = user }
                if (!completed) return@post call.respondStepNotActive()

                call.respondGson(buildGenericMap {
                    put("type", "success")
                    put("username", user.getUsername())
                    put("display_name", user.getDisplayName())
                })
            }
        }
    }

    override suspend fun createState(session: Session<*>): EmailSelectionPluginState {
        return EmailSelectionPluginState(
            withUsername = configuration.withUsername,
            hasUser = session.identifiedUser != null,
            rateLimiter = rateLimiter,
        )
    }
}

/**
 * State for the email step.
 *
 * @param withUsername whether the input also accepts a username.
 * @param hasUser whether a user has been identified.
 * @param rateLimiter limits lookups without a match, or `null` if lookups are not limited.
 */
data class EmailSelectionPluginState(
    val withUsername: Boolean,
    var hasUser: Boolean,
    val rateLimiter: RateLimiter? = null,
): BaseState {
    override suspend fun isCompleted(): Boolean {
        return hasUser
    }

    override suspend fun createClientState(session: Session<*>): Map<String, Any?> = buildGenericMap {
        put("with_username", this@EmailSelectionPluginState.withUsername)
        put("rate_limit", rateLimiter?.status(session)?.toClientState())
    }
}

@Serializable
data class LoginEmailRequest(
    @SerialName("email") val email: String,
)

/**
 * DSL builder for [EmailUserSelectionPlugin] configuration.
 */
class EmailUserSelectionPluginConfigurationBuilder<USER> {
    typealias FindUserByEmail<USER> = suspend (email: String) -> AuthentiktUser<USER>?

    /**
     * Whether the server also expects a username after the email (default `false`).
     * Controls the `with_username` flag sent to the frontend.
     */
    var withUsername: Boolean = false
    private var findUserByEmail: FindUserByEmail<USER>? = null

    /**
     * Lookups without a match allowed per session, e.g. `10 triesPer 1.minutes`. Defaults to 10 tries per minute.
     * Set to `null` to disable the limit.
     */
    var rateLimit: RateLimit? = 10 triesPer 1.minutes

    /**
     * Sets the email-to-user lookup callback.
     *
     * @param block suspending function that receives an email address and returns
     *   the matching [AuthentiktUser], or `null` if not found.
     */
    fun findUserByEmail(block: FindUserByEmail<USER>) {
        this.findUserByEmail = block
    }

    internal fun build(): EmailUserSelectionPluginConfiguration<USER> {
        requireNotNull(findUserByEmail) { "findUserByEmail must be configured" }

        return EmailUserSelectionPluginConfiguration(
            withUsername = withUsername,
            findUserByEmail = findUserByEmail!!,
            rateLimit = rateLimit,
        )
    }
}

internal data class EmailUserSelectionPluginConfiguration<USER>(
    val withUsername: Boolean,
    val findUserByEmail: EmailUserSelectionPluginConfigurationBuilder.FindUserByEmail<USER>,
    val rateLimit: RateLimit?,
)
