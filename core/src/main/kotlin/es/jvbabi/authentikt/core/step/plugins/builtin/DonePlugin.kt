package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.BaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Final authentication step that generates a token and completes the flow.
 *
 * This plugin should be the last step in the auth pipeline. It generates an
 * auth token (JWT, session cookie, etc.), optionally sets a cookie on the response,
 * and signals completion to the frontend.
 *
 * ### Usage
 * ```kotlin
 * install(DonePlugin {
 *     generateToken { session, user -> jwtService.createToken(user) }
 *     cookie(name = "auth_token", validFor = 7.days)
 * })
 * ```
 *
 * @param configuration lambda that configures token generation and cookie options.
 */
class DonePlugin<USER>(
    configuration: DonePluginConfigurationBuilder<USER>.() -> Unit,
) : BasePlugin<DoneState>(
    namespace = "authentikt-builtin/done",
) {
    private val configuration = DonePluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    override suspend fun createState(session: Session<*>): DoneState {
        return DoneState(token = null)
    }

    override fun installRoutes(inRoute: Route) {
        with(inRoute) {
            get {
                val session = call.attributes[SessionKey] as Session<USER>
                val user = session.identifiedUser!!.user

                val step = session.authenticationSteps[session.authenticationSteps.lastIndex].second as DoneState
                val token = if (step.isCompleted()) {
                    step.token!!
                } else {
                    val token = configuration.generateToken(session, user)
                    session.authenticationSteps[session.authenticationSteps.lastIndex] = this@DonePlugin to DoneState(token)
                    token
                }

                if (configuration.tokenCookie != null) {
                    call.response.cookies.append(
                        name = configuration.tokenCookie.name,
                        value = token,
                        maxAge = configuration.tokenCookie.validFor.inWholeSeconds,
                        secure = configuration.tokenCookie.secure,
                        httpOnly = configuration.tokenCookie.httpOnly,
                        path = configuration.tokenCookie.path
                    )
                }


                call.respond(
                    message = mapOf("token" to token),
                    status = HttpStatusCode.OK
                )
            }
        }
    }
}

/**
 * DSL builder for [DonePlugin] configuration.
 */
class DonePluginConfigurationBuilder<USER> {
    private var generateToken: DonePluginConfiguration.GenerateToken<USER>? = null
    private var tokenCookie: DonePluginConfiguration.TokenCookie? = null

    /**
     * Sets the token generation callback.
     *
     * @param block suspending function that receives the session and user object,
     *   returning the auth token string.
     */
    fun generateToken(block: DonePluginConfiguration.GenerateToken<USER>) {
        this.generateToken = block
    }

    /**
     * Configures an auth cookie to be set in the response after token generation.
     *
     * @param name cookie name.
     * @param validFor cookie lifetime.
     * @param httpOnly whether the cookie is HTTP-only (default `true`).
     * @param path cookie path (default `"/"`).
     * @param secure whether the cookie requires HTTPS (default `true`).
     */
    fun cookie(
        name: String,
        validFor: Duration,
        httpOnly: Boolean = true,
        path: String = "/",
        secure: Boolean = true,
    ) {
        this.tokenCookie = DonePluginConfiguration.TokenCookie(
            name = name,
            validFor = validFor,
            httpOnly = httpOnly,
            path = path,
            secure = secure
        )
    }

    internal fun build(): DonePluginConfiguration<USER> {
        requireNotNull(this.generateToken) { "generateToken is required" }
        return DonePluginConfiguration(
            generateToken = this.generateToken!!,
            tokenCookie = this.tokenCookie,
        )
    }
}

/**
 * State for the done / token generation step.
 *
 * @param token the generated auth token, or null initially.
 */
data class DoneState(
    val token: String?,
) : BaseState {
    override suspend fun isCompleted(): Boolean = this.token != null
    override suspend fun createClientState(session: Session<*>): Map<String, Any?> = emptyMap()
}

internal data class DonePluginConfiguration<USER>(
    val generateToken: GenerateToken<USER>,
    val tokenCookie: TokenCookie?
) {
    typealias GenerateToken<USER> = suspend (session: Session<USER>, user: USER) -> String

    data class TokenCookie(
        val name: String,
        val validFor: Duration,
        val httpOnly: Boolean,
        val path: String,
        val secure: Boolean,
    )
}
