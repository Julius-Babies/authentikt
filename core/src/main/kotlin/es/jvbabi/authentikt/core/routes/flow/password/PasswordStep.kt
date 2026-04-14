package es.jvbabi.authentikt.core.routes.flow.password

import es.jvbabi.authentikt.core.session.AuthenticationStep
import es.jvbabi.authentikt.core.session.PasswordAuthenticationStep
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun Route.password() {
    post {
        val request = call.receive<PasswordRequest>()

        val session = call.attributes[SessionKey]
        val user = session.identifiedUser!!
        val passwordMatches = user.checkPassword(request.password)

        if (!passwordMatches) {
            call.respondGson(buildGenericMap {
                put("type", "invalid_password")
            })

            return@post
        }

        session.authenticationSteps.add(PasswordAuthenticationStep())

        call.respond(HttpStatusCode.OK)
    }
}

@Serializable
data class PasswordRequest(
    @SerialName("password") val password: String,
)
