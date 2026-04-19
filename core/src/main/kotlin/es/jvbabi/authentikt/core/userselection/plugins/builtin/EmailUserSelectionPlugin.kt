package es.jvbabi.authentikt.core.userselection.plugins.builtin

import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.userselection.plugins.BaseUserSelectionPlugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class EmailUserSelectionPlugin<USER>(
    configuration: EmailUserSelectionPluginConfigurationBuilder<USER>.() -> Unit,
) : BaseUserSelectionPlugin<USER>(
    namespace = "authentikt-builtin/email"
) {
    private val configuration = EmailUserSelectionPluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    override fun installRoutes(inRoute: Route) {
        with(inRoute) {
            post {
                val request = call.receive<LoginEmailRequest>()
                val user = configuration.findUserByEmail(request.email)

                if (user == null) {
                    call.respondGson(buildGenericMap {
                        put("type", "user_not_found")
                    })

                    return@post
                }

                val session = call.attributes[SessionKey] as Session<USER>
                session.identifiedUser = user
                session.nextStep()

                call.respondGson(buildGenericMap {
                    put("type", "success")
                    put("username", user.getUsername())
                    put("display_name", user.getDisplayName())
                })
            }
        }
    }

    override suspend fun createClientState(): Map<String, Any?> = buildGenericMap {
        put("with_username", configuration.withUsername)
    }
}

@Serializable
data class LoginEmailRequest(
    @SerialName("email") val email: String,
)

class EmailUserSelectionPluginConfigurationBuilder<USER> {
    typealias FindUserByEmail<USER> = suspend (email: String) -> AuthentiktUser<USER>?

    var withUsername: Boolean = false
    private var findUserByEmail: FindUserByEmail<USER>? = null

    fun findUserByEmail(block: FindUserByEmail<USER>) {
        this.findUserByEmail = block
    }

    internal fun build(): EmailUserSelectionPluginConfiguration<USER> {
        requireNotNull(findUserByEmail) { "findUserByEmail must be configured" }

        return EmailUserSelectionPluginConfiguration(
            withUsername = withUsername,
            findUserByEmail = findUserByEmail!!,
        )
    }
}

internal data class EmailUserSelectionPluginConfiguration<USER>(
    val withUsername: Boolean,
    val findUserByEmail: EmailUserSelectionPluginConfigurationBuilder.FindUserByEmail<USER>,
)
