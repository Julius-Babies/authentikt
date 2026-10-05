package es.jvbabi.authentikt.core.step.plugins.builtin

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
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
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant

/**
 * Time-based One-Time Password verification step plugin.
 *
 * Validates a TOTP code either by checking against a generated code from a stored secret,
 * or by delegating to a custom validation callback.
 *
 * Failed attempts are limited per user, see [TotpPluginConfigurationBuilder.rateLimit].
 *
 * ### Usage
 * ```kotlin
 * install(TotpPlugin {
 *     rateLimit = 3 triesPer 3.minutes
 *     getSecret { user -> user.totpSecret }
 *     // OR
 *     validate { user, code -> myTotpService.isValid(user, code) }
 * })
 * ```
 *
 * @param configuration lambda that configures TOTP validation.
 */
class TotpPlugin<USER>(
    configuration: TotpPluginConfigurationBuilder<USER>.() -> Unit,
) : BasePlugin<USER, TotpState>(
    namespace = "authentikt-builtin/totp"
) {
    private val configuration = TotpPluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    private val rateLimiter = this.configuration.rateLimit?.let { RateLimiter.perUser(it) }

    override suspend fun createState(session: Session<*>): TotpState {
        return TotpState(isValidated = false, rateLimiter = rateLimiter)
    }

    override fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {
        with(inRoute) {
            post {
                val session = call.attributes[SessionKey] as Session<USER>
                if (!session.isActive(this@TotpPlugin)) return@post call.respondStepNotActive()
                val request = call.receive<TotpRequest>()

                val attempt = rateLimiter?.tryAcquire(session)
                if (attempt != null && !attempt.allowed) {
                    return@post call.respondRateLimited(attempt.status, buildGenericMap { put("success", false) })
                }

                val success = configuration.check(session.identifiedUser!!.user, request.totp)

                if (success) {
                    rateLimiter?.reset(session)
                    if (!session.completeStep(this@TotpPlugin, TotpState(true, rateLimiter))) {
                        return@post call.respondStepNotActive()
                    }
                }

                call.respondGson(buildGenericMap {
                    put("success", success)
                    if (!success) put("rate_limit", attempt?.status?.toClientState())
                })
            }
        }
    }
}

@Serializable
data class TotpRequest(
    @SerialName("totp_code") val totp: String
)

/**
 * State for the TOTP step.
 *
 * @param isValidated whether the TOTP code was successfully verified.
 * @param rateLimiter limits failed attempts, or `null` if attempts are not limited.
 */
data class TotpState(
    val isValidated: Boolean,
    val rateLimiter: RateLimiter? = null,
) : BaseState {
    override suspend fun createClientState(session: Session<*>): Map<String, Any?> = buildGenericMap {
        put("validated", this@TotpState.isValidated)
        put("rate_limit", rateLimiter?.status(session)?.toClientState())
    }

    override suspend fun isCompleted(): Boolean = this.isValidated
}

/**
 * DSL builder for [TotpPlugin] configuration.
 */
class TotpPluginConfigurationBuilder<USER> {
    typealias TotpCustomCheck<USER> = suspend (user: USER, totp: String) -> Boolean
    typealias TotpGetSecret<USER> = suspend (user: USER) -> String

    /**
     * Clock instance used for TOTP time-window generation. Defaults to [Clock.System].
     */
    var clock: Clock = Clock.System
    private var checkOtp: TotpCustomCheck<USER>? = null
    private var getSecret: TotpGetSecret<USER>? = null

    /**
     * Length of each TOTP time window. Defaults to 30 seconds.
     */
    var totpDuration: Duration = 30.seconds

    /**
     * Number of digits in the generated code. Defaults to 6.
     */
    var digits: Int = 6

    /**
     * HMAC algorithm used for code generation. Defaults to SHA1.
     */
    var hmacAlgorithm: TotpPluginConfiguration.TotpHmacAlgorithm = TotpPluginConfiguration.TotpHmacAlgorithm.SHA1

    /**
     * Failed attempts allowed per user, e.g. `3 triesPer 3.minutes`. Defaults to 5 tries per 5 minutes.
     * Set to `null` to disable the limit.
     */
    var rateLimit: RateLimit? = 5 triesPer 5.minutes

    /**
     * Sets a custom TOTP validation callback.
     *
     * Use this when you have your own TOTP verification logic.
     *
     * @param block suspending function that receives the user object and the
     *   submitted code, returning `true` if valid.
     */
    fun validate(block: TotpCustomCheck<USER>) {
        this.checkOtp = block
    }

    /**
     * Sets the secret retrieval callback for server-side TOTP generation.
     *
     * When this is set, the plugin generates the expected code internally and
     * compares it against the user-submitted code.
     *
     * @param block suspending function that returns the TOTP secret for the given user.
     */
    fun getSecret(block: TotpGetSecret<USER>) {
        getSecret = block
    }

    internal fun build(): TotpPluginConfiguration<USER> {
        if (this.checkOtp == null && this.getSecret == null) {
            throw RuntimeException("At least one method of TOTP validation is required. Either provide the secret or a validation function.")
        }
        return TotpPluginConfiguration(
            clock = this.clock,
            checkUser = this.checkOtp,
            digits = this.digits,
            hmacAlgorithm = HmacAlgorithm.valueOf(this.hmacAlgorithm.name),
            totpDuration = this.totpDuration,
            getSecret = this.getSecret,
            rateLimit = this.rateLimit,
        )
    }
}

/**
 * Resolved configuration for the TOTP plugin.
 */
data class TotpPluginConfiguration<USER>(
    val clock: Clock,
    val checkUser: TotpPluginConfigurationBuilder.TotpCustomCheck<USER>?,
    val digits: Int,
    val hmacAlgorithm: HmacAlgorithm,
    val totpDuration: Duration,
    val getSecret: TotpPluginConfigurationBuilder.TotpGetSecret<USER>?,
    val rateLimit: RateLimit?,
) {

    private val config = TimeBasedOneTimePasswordConfig(
        timeStep = totpDuration.inWholeSeconds,
        timeStepUnit = TimeUnit.SECONDS,
        hmacAlgorithm = this.hmacAlgorithm,
        codeDigits = digits,
    )

    suspend fun check(user: USER, totp: String): Boolean {
        if (this.getSecret != null) {
            val secret = this.getSecret(user)
            val isValid = TimeBasedOneTimePasswordGenerator(
                secret = secret.toByteArray(),
                config = this.config
            ).generate(clock.now().toJavaInstant()) == totp

            return isValid
        }

        return this.checkUser!!(user, totp)
    }

    @Suppress("unused")
    enum class TotpHmacAlgorithm {
        SHA1, SHA256, SHA512
    }
}
