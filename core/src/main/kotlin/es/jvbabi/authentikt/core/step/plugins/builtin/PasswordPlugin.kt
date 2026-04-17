package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.StepState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PasswordPlugin<USER>(
    configuration: PasswordPluginConfigurationBuilder<USER>.() -> Unit
): BasePlugin<PasswordState>(
    namespace = "authentikt-builtin/password",
) {
    private val configuration = PasswordPluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    override suspend fun createState(session: Session): PasswordState {
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
                }

                call.respond(buildMap { put("success", isValid) })
            }
        }
    }
}

data class PasswordState(
    val isValidated: Boolean,
): StepState {
    override suspend fun isCompleted(): Boolean = this.isValidated
    override suspend fun createClientState(session: Session): String = this.isValidated.toString()
}

@Serializable
internal data class PasswordRequest(
    @SerialName("password") val password: String
)

class PasswordPluginConfigurationBuilder<USER> {
    typealias CheckPassword<USER> = suspend (user: USER, password: String) -> Boolean

    private var checkPassword: CheckPassword<USER>? = null

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