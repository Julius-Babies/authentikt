package es.jvbabi.authentikt.core.routes.flow.email

import es.jvbabi.authentikt.core.authentiktPluginConfiguration
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun Route.loginEmail() {
    post {
        val request = call.receive<LoginEmailRequest>()

        val user = authentiktPluginConfiguration.authentiktUserSource!!.findUserByEmail(request.email)

        if (user == null) {
            call.respondGson(buildGenericMap {
                put("type", "user_not_found")
            })

            return@post
        }

        call.attributes[SessionKey].identifiedUser = user

        call.respondGson(buildGenericMap {
            put("type", "success")
            put("username", user.getUsername())
            put("display_name", user.getDisplayName())
        })
    }
}

@Serializable
data class LoginEmailRequest(
    @SerialName("email") val email: String,
)
