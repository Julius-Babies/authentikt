package es.jvbabi.authentikt.core.step.plugins.builtin

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant

class TotpPlugin<USER>(
    configuration: TotpPluginConfigurationBuilder<USER>.() -> Unit,
) : BasePlugin<TotpState>(
    namespace = "authentikt-builtin/totp"
) {
    private val configuration = TotpPluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    override suspend fun createState(session: Session): TotpState {
        return TotpState(isValidated = false)
    }

    override fun installRoutes(inRoute: Route) {
        with(inRoute) {
            post {
                val request = call.receive<TotpRequest>()
                val session = call.attributes[SessionKey]

                val success = configuration.check(session.identifiedUser!!.user as USER, request.totp)

                if (success) {
                    session.authenticationSteps[session.authenticationSteps.lastIndex] = this@TotpPlugin to TotpState(true)
                }

                call.respondGson(buildMap { put("success", success) })
            }
        }
    }
}

@Serializable
data class TotpRequest(
    @SerialName("totp_code") val totp: String
)

data class TotpState(
    val isValidated: Boolean,
) : BaseState {
    override suspend fun createClientState(session: Session): Map<String, Any?> = buildGenericMap {
        put("validated", this@TotpState.isValidated)
    }

    override suspend fun isCompleted(): Boolean = this.isValidated
}

class TotpPluginConfigurationBuilder<USER> {
    typealias TotpCustomCheck<USER> = suspend (user: USER, totp: String) -> Boolean
    typealias TotpGetSecret<USER> = suspend (user: USER) -> String

    var clock: Clock = Clock.System
    private var checkOtp: TotpCustomCheck<USER>? = null
    private var getSecret: TotpGetSecret<USER>? = null

    var totpDuration: Duration = 30.seconds
    var digits: Int = 6
    var hmacAlgorithm: TotpPluginConfiguration.TotpHmacAlgorithm = TotpPluginConfiguration.TotpHmacAlgorithm.SHA1

    fun validate(block: TotpCustomCheck<USER>) {
        this.checkOtp = block
    }

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
        )
    }
}

data class TotpPluginConfiguration<USER>(
    val clock: Clock,
    val checkUser: TotpPluginConfigurationBuilder.TotpCustomCheck<USER>?,
    val digits: Int,
    val hmacAlgorithm: HmacAlgorithm,
    val totpDuration: Duration,
    val getSecret: TotpPluginConfigurationBuilder.TotpGetSecret<USER>?,
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