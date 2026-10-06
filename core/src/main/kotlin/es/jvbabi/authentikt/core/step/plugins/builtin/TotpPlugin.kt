package es.jvbabi.authentikt.core.step.plugins.builtin

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.HmacOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.HmacOneTimePasswordGenerator
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
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Time-based One-Time Password verification step plugin.
 *
 * Validates a TOTP code either by checking against codes generated from a stored secret,
 * or by delegating to a custom validation callback. With a secret, codes of up to
 * [TotpPluginConfigurationBuilder.allowedDrift] windows before and after the current one are accepted.
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
     * Number of time windows before and after the current one whose codes are also accepted, to tolerate clock drift
     * and codes entered at the end of a window. Defaults to 1. Set to 0 to accept only the current window.
     */
    var allowedDrift: Int = 1

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
     * When this is set, the plugin generates the expected codes internally and compares them against the
     * user-submitted code.
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
        require(digits in 1..9) { "TOTP digits must be between 1 and 9" }
        require(allowedDrift >= 0) { "TOTP allowedDrift must not be negative" }
        require(totpDuration.inWholeMilliseconds > 0) { "TOTP totpDuration must be positive" }
        return TotpPluginConfiguration(
            clock = this.clock,
            checkUser = this.checkOtp,
            digits = this.digits,
            hmacAlgorithm = HmacAlgorithm.valueOf(this.hmacAlgorithm.name),
            totpDuration = this.totpDuration,
            getSecret = this.getSecret,
            rateLimit = this.rateLimit,
            allowedDrift = this.allowedDrift,
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
    val allowedDrift: Int = 1,
) {

    private val config = HmacOneTimePasswordConfig(
        codeDigits = digits,
        hmacAlgorithm = this.hmacAlgorithm,
    )

    suspend fun check(user: USER, totp: String): Boolean {
        val getSecret = this.getSecret ?: return this.checkUser!!(user, totp)

        return matchesCode(getSecret(user).toByteArray(), totp)
    }

    /**
     * Returns whether [totp] equals the code of any time window within [allowedDrift].
     * Every window is compared in constant time.
     */
    private fun matchesCode(secret: ByteArray, totp: String): Boolean {
        if (totp.length != digits || !totp.all { it in '0'..'9' }) return false
        val current = Math.floorDiv(clock.now().toEpochMilliseconds(), totpDuration.inWholeMilliseconds)
        val generator = HmacOneTimePasswordGenerator(secret, config)
        val submitted = totp.toByteArray()
        var match = false
        for (timeStep in (current - allowedDrift)..(current + allowedDrift)) {
            match = MessageDigest.isEqual(generator.generate(timeStep).toByteArray(), submitted) or match
        }
        return match
    }

    @Suppress("unused")
    enum class TotpHmacAlgorithm {
        SHA1, SHA256, SHA512
    }
}
