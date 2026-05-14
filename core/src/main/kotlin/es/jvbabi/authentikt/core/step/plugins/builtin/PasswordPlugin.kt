package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.BaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Password verification step plugin.
 *
 * Validates the user's password against a configurable check function.
 * On success, marks the step as completed and advances the flow.
 *
 * ### Usage
 * ```kotlin
 * install(PasswordPlugin {
 *     checkPassword { user, password -> passwordHasher.verify(user, password) }
 * })
 * ```
 *
 * @param configuration lambda that configures password checking.
 */
class PasswordPlugin<USER>(
    configuration: PasswordPluginConfigurationBuilder<USER>.() -> Unit
) : BasePlugin<PasswordState>(
    namespace = "authentikt-builtin/password",
) {
    private val configuration = PasswordPluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    override suspend fun createState(session: Session<*>): PasswordState {
        return PasswordState(false)
    }

    override fun installRoutes(inRoute: Route) {
        with(inRoute) {
            post {
                val request = call.receive<PasswordRequest>()
                val session = call.attributes[SessionKey]

                val isValid = configuration.checkPassword(session.identifiedUser!!.user as USER, request.password)

                if (isValid) {
                    session.authenticationSteps[session.authenticationSteps.lastIndex] = this@PasswordPlugin to PasswordState(true)
                    session.nextStep()
                }

                call.respond(buildMap { put("success", isValid) })
            }
        }
    }
}

/**
 * State for the password step.
 *
 * @param isValidated whether the password was successfully verified.
 */
data class PasswordState(
    val isValidated: Boolean,
) : BaseState {
    override suspend fun isCompleted(): Boolean = this.isValidated

    override suspend fun createClientState(session: Session<*>): Map<String, Any?> = buildGenericMap {
        put("validated", this@PasswordState.isValidated)
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
        )
    }
}

internal data class PasswordPluginConfiguration<USER>(
    val checkPassword: PasswordPluginConfigurationBuilder.CheckPassword<USER>
)
