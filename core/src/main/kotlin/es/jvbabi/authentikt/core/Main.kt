package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktConfiguration
import es.jvbabi.authentikt.core.config.AuthentiktPluginConfigurationBuilder
import es.jvbabi.authentikt.core.routes.flow.check.checkFlowStatus
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.session.sessions
import io.ktor.server.application.*
import io.ktor.server.routing.*

internal lateinit var authentiktPluginConfiguration: AuthentiktConfiguration<*>

/**
 * Installs the authentikt authentication flow into the Ktor [Application].
 *
 * This is the main entry point for the library. It registers auth routes under the configured
 * API prefix and sets up the session-based authentication pipeline.
 *
 * ## Getting Started
 *
 * ### 1. Add the dependency
 * ```kotlin
 * repositories { mavenCentral() }
 * dependencies { implementation("es.jvbabi.authentikt:core:1.0-SNAPSHOT") }
 * ```
 *
 * ### 2. Create a user wrapper
 * ```kotlin
 * class MyUser(private val dbUser: DbUser) : AuthentiktUser<DbUser>(dbUser) {
 *     override suspend fun getEmail(): String? = dbUser.email
 *     override suspend fun getUsername(): String? = dbUser.username
 *     override suspend fun getDisplayName(): String? = dbUser.displayName
 * }
 * ```
 *
 * ### 3. Install the auth plugin
 * ```kotlin
 * fun Application.module() {
 *     installAuthentikt {
 *         apiPrefix = "/api/v1"
 *
 *         val emailPlugin = EmailUserSelectionPlugin {
 *             findUserByEmail { email -> userDao.findByEmail(email)?.let { MyUser(it) } }
 *         }
 *         install(emailPlugin)
 *
 *         val passwordPlugin = PasswordPlugin {
 *             checkPassword { user, password -> passwordHasher.verify(user.passwordHash, password) }
 *         }
 *         install(passwordPlugin)
 *
 *         val totpPlugin = TotpPlugin {
 *             getSecret { user -> user.totpSecret }
 *         }
 *         install(totpPlugin)
 *
 *         val donePlugin = DonePlugin {
 *             generateToken { session, user -> jwtService.createToken(user) }
 *             cookie(name = "auth_token", validFor = 7.days)
 *         }
 *         install(donePlugin)
 *
 *         authorization { session, user ->
 *             when {
 *                 !session.has(passwordPlugin) -> passwordPlugin
 *                 user.user.hasTotpEnabled && !session.has(totpPlugin) -> totpPlugin
 *                 else -> donePlugin
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ### 4. Wire login / logout
 * ```kotlin
 * routing {
 *     post("/login") {
 *         val session = authentiktPluginConfiguration.createNewSession()
 *         call.respond(mapOf("session_id" to session.sessionId))
 *     }
 *     post("/logout") {
 *         call.response.cookies.append("auth_token", "", maxAge = 0)
 *         call.respond(mapOf("status" to "ok"))
 *     }
 * }
 * ```
 *
 * ## Frontend (Svelte)
 *
 * Pair this backend with `authentikt-svelte`:
 * ```svelte
 * <Authentikt baseUrl="http://localhost:8080/api/v1/authentikt/">
 *   {@const auth = useAuthentiktContext()}
 *   {#if !auth.currentFlow}
 *     <button onclick={auth.startLoginFlow}>Login</button>
 *   {:else}
 *     <AuthentiktUserSelectionRenderer />
 *     <PasswordRenderer />
 *     <AuthentiktStepRenderer />
 *   {/if}
 * </Authentikt>
 * ```
 *
 * ## Flow lifecycle
 *
 * ```
 * POST /login                         → Creates session
 * GET  /flow/{id}/check               → { type: "user_selection", plugins: [...] }
 * POST /flow/{id}/user-selection/…    → Identifies user
 * GET  /flow/{id}/check               → { type: "step", namespace: "..." }
 * POST /flow/{id}/steps/plugins/…     → Validates step
 * … repeat …
 * GET  /flow/{id}/steps/plugins/done  → Generates token
 * ```
 *
 * @param block configuration DSL that sets up plugins and the authorization flow callback.
 * @return an [AuthentiktInstance] that can be used to manage sessions.
 */
fun <USER> Application.installAuthentikt(
    block: AuthentiktPluginConfigurationBuilder<USER>.() -> Unit
): AuthentiktInstance<USER> {

    val builder = AuthentiktPluginConfigurationBuilder<USER>()
        .apply(block)

    val configuration = builder.build()
    authentiktPluginConfiguration = configuration

    routing {
        route("${configuration.apiPrefix}/authentikt") {
            route("/flow") {
                route("/{sessionId}") sessionScopedRoute@{
                    createRouteScopedPlugin("Get Session from Path") {
                        onCall { call ->
                            val sessionId = call.parameters["sessionId"]
                            val session = sessions[sessionId]!!
                            call.attributes[SessionKey] = session
                        }
                    }.let { this@sessionScopedRoute.install(it) }

                    route("/check") { checkFlowStatus(configuration) }

                    route("/user-selection/plugins") {
                        configuration.installedUserSelectionPlugins.forEach { plugin ->
                            route("/${plugin.namespace}") pluginScopedRoute@{
                                plugin.installRoutes(this@pluginScopedRoute)
                            }
                        }
                    }

                    route("/steps/plugins") {
                        configuration.installedPlugins.forEach { plugin ->
                            route("/${plugin.namespace}") pluginScopedRoute@{
                                plugin.installRoutes(this@pluginScopedRoute)
                            }
                        }
                    }
                }
            }
        }
    }

    return AuthentiktInstance(
        configuration = configuration,
    )
}
