package es.jvbabi.authentikt.samples

import es.jvbabi.authentikt.core.installAuthentikt
import es.jvbabi.authentikt.core.AuthentiktUser
import es.jvbabi.authentikt.core.AuthentiktUserSource
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.PasswordPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPlugin
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
    val password: String,
    val otpSecret: String?,
)

val users = listOf(
    User(
        email = "admin@acme.com",
        displayName = "Admin",
        username = "admin",
        password = "password",
        otpSecret = "X3V3UD62XVWDX7GH",
    ),

    User(
        email = "eric.smith@acme.com",
        displayName = "Eric Smith",
        username = "eric.smith",
        password = "password",
        otpSecret = null,
    )
)

fun User.toAuthentiktUser() = object : AuthentiktUser<User>(this) {
    override suspend fun getEmail(): String = email
    override suspend fun getUsername(): String = username
    override suspend fun getDisplayName(): String = displayName
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

    val passwordPlugin = PasswordPlugin<User> {
        checkPassword { user, password -> user.password == password }
    }

    val otpPlugin = TotpPlugin<User> {}

    installAuthentikt {
        authentiktUserSource = AppUserSource()

        install(passwordPlugin)

        userSelection {
            email(withUsername = true)
        }

        authorization { session, user ->
            if (!session.has(passwordPlugin)) return@authorization passwordPlugin
            if (!session.has(otpPlugin) && user.user.otpSecret != null) return@authorization otpPlugin

            return@authorization DonePlugin
        }
    }
}
