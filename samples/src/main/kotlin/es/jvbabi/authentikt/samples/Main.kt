package es.jvbabi.authentikt.samples

import es.jvbabi.authentikt.core.installAuthentikt
import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.AuthentiktUserSource
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

data class User(
    val email: String,
    val displayName: String,
    val username: String,
    val password: String
)

val users = listOf(
    User(
        email = "admin@acme.com",
        displayName = "Admin",
        username = "admin",
        password = "password",
    ),
)

fun User.toAuthentiktUser() = object : AuthentiktUser<User>(this) {
    override suspend fun getEmail(): String = email
    override suspend fun getUsername(): String = username
    override suspend fun getDisplayName(): String = displayName
    override suspend fun checkPassword(password: String): Boolean = this@toAuthentiktUser.password == password
}

class AppUserSource: AuthentiktUserSource<User> {
    override suspend fun findUserByEmail(email: String): AuthentiktUser<User>? {
        return users.find { it.email == email }?.toAuthentiktUser()
    }
}

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    installAuthentikt {
        authentiktUserSource = AppUserSource()

        userSelection {
            email(withUsername = true)
        }

        userAuthorization {
            initialValidation()
        }

        onSuccess { user ->
            println(user.getEmail() ?: "unknown")
        }
    }
}
