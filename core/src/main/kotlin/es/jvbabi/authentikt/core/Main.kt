package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.*
import es.jvbabi.authentikt.core.routes.flow.alternatives.switchToAlternative
import es.jvbabi.authentikt.core.routes.flow.check.checkFlowStatus
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionDestination.DeviceFlow
import es.jvbabi.authentikt.core.session.SessionDestination.OAuth
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.session.findActiveSession
import es.jvbabi.authentikt.core.session.removeExpiredSessions
import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.DoneState
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Polling state of a device flow session: time of the last poll and the current interval in seconds.
 */
private val DeviceCodePollStateKey = AttributeKey<Pair<Instant, Long>>("DeviceCodePollState")

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
 * <Authentikt baseUrl="https://authentikt-lib.wb.local/api/v1/authentikt/">
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
 * GET  /flow/{id}/check               → { type: "step", namespace: "...", alternatives: [...] }
 * POST /flow/{id}/alternatives        → Switches to an alternative of the active step (optional)
 * POST /flow/{id}/steps/plugins/…     → Validates step
 * … repeat …
 * GET  /flow/{id}/steps/plugins/done  → Generates token, ends the session
 * ```
 *
 * Sessions expire after [AuthentiktPluginConfigurationBuilder.sessionTimeout] without activity. Requests for
 * unknown or expired sessions are answered with `404`.
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

    val authentiktInstance = AuthentiktInstance(configuration)

    launch {
        while (isActive) {
            delay(configuration.sessionCleanupInterval)
            removeExpiredSessions()
        }
    }

    routing {
        if (configuration.oAuthConfiguration != null) {

            val donePlugin = authentiktInstance.configuration.installedPlugins.find { it is DonePlugin<*> } as? DonePlugin<*>
            requireNotNull(donePlugin) { "DonePlugin is required for OAuth flow" }
            requireNotNull(donePlugin.configuration.onOAuthSuccess) { "onOAuthSuccess callback in DonePlugin is required for OAuth flow" }

            route("/oauth") {
                if (configuration.oAuthConfiguration.onAuthorize != null) get("/authorize") {
                    val clientId = call.parameters["client_id"]!!
                    val redirectUri = call.parameters["redirect_uri"]!!
                    when (val result = configuration.oAuthConfiguration.onAuthorize(clientId, redirectUri)) {
                        is OAuthAuthorizationResult.Error -> call.respondText(
                            result.error,
                            status = HttpStatusCode.BadRequest
                        )

                        is OAuthAuthorizationResult.Application -> {
                            val session = authentiktInstance.createNewSession(destination = OAuth(
                                redirectUri = result.redirectUri,
                                applicationId = result.clientId,
                                applicationName = result.name,
                            ))
                            val webUiRedirectUrl = URLBuilder(authentiktInstance.configuration.uiLoginBaseUrl).apply {
                                parameters.append("_authentikt_flow_active", "true")
                                parameters.append("_authentikt_session_id", session.sessionId)
                            }.build()

                            call.respondRedirect(webUiRedirectUrl, permanent = false)
                        }
                    }
                }

                post("/token") {
                    val params = call.receiveParameters()
                    val grantType = params["grant_type"]!!

                    when (grantType) {
                        "urn:ietf:params:oauth:grant-type:device_code" -> {
                            requireNotNull(configuration.oAuthConfiguration.onDeviceFlowAuthorize) { "Device flow is not enabled" }

                            val deviceCode = params["device_code"]!!
                            val clientId = params["client_id"]!!

                            val session = sessions.values.find { session -> session.destination is DeviceFlow && session.destination.deviceCode == deviceCode && session.destination.applicationId == clientId }
                                as? Session<USER>
                            if (session == null || session.isExpired()) {
                                session?.invalidate()
                                call.respondGson(
                                    value = buildGenericMap {
                                        put("error", "expired_token")
                                        put("error_description", "The device code is invalid or has expired.")
                                    },
                                    status = HttpStatusCode.BadRequest,
                                )
                                return@post
                            }

                            // Serialize polls of the same device code, so it can only be redeemed once
                            session.withLock {
                                if (sessions[session.sessionId] !== session) {
                                    call.respondGson(
                                        value = buildGenericMap {
                                            put("error", "expired_token")
                                            put("error_description", "The device code is invalid or has expired.")
                                        },
                                        status = HttpStatusCode.BadRequest,
                                    )
                                    return@withLock
                                }

                                val now = configuration.clock.now()
                                val pollState = session.attributes[DeviceCodePollStateKey]
                                if (pollState != null) {
                                    val (lastPoll, interval) = pollState
                                    if (now - lastPoll < interval.seconds) {
                                        session.attributes[DeviceCodePollStateKey] = now to (interval + 5)
                                        call.respondGson(
                                            value = buildGenericMap {
                                                put("error", "slow_down")
                                                put("error_description", "The client is polling too frequently.")
                                            },
                                            status = HttpStatusCode.BadRequest,
                                        )
                                        return@withLock
                                    }
                                }
                                session.attributes[DeviceCodePollStateKey] = now to (pollState?.second ?: 5L)

                                val lastStep = session.authenticationSteps.lastOrNull()

                                if (lastStep?.first !is DonePlugin) {
                                    call.respondGson(
                                        value = buildGenericMap {
                                            put("error", "authorization_pending")
                                            put("error_description", "The authorization request is pending.")
                                        },
                                        status = HttpStatusCode.BadRequest,
                                    )
                                    return@withLock
                                }

                                val step = lastStep.first as DonePlugin<USER>
                                requireNotNull(step.configuration.onOAuthSuccess) { "onOAuthSuccess callback is required for OAuth flow" }
                                val accessToken = step.configuration.onOAuthSuccess(session, session.identifiedUser!!.user)
                                session.authenticationSteps[session.authenticationSteps.lastIndex] = step to DoneState(completed = true)
                                // Redeemed: later polls find no session and receive expired_token
                                session.invalidate()
                                call.respondGson(
                                    buildGenericMap {
                                        put("access_token", accessToken.accessToken)
                                        put("token_type", "bearer")
                                        put("expires_in", accessToken.expiresIn.inWholeSeconds)
                                        put("refresh_token", accessToken.refreshToken)
                                    }
                                )
                            }
                        }

                        else -> call.respondText(
                            "Unsupported grant type",
                            status = HttpStatusCode.BadRequest
                        )
                    }
                }

                if (configuration.oAuthConfiguration.onDeviceFlowAuthorize != null) {
                    post("/device/code") {
                        val body = call.receiveParameters()
                        val clientId = body["client_id"]!!

                        when (val result = configuration.oAuthConfiguration.onDeviceFlowAuthorize(
                            ValidateDeviceFlowAuthorizationCallbackScope(),
                            clientId
                        )) {
                            is OAuthDeviceFlowAuthorizationResult.Error -> call.respondText(
                                result.error,
                                status = HttpStatusCode.BadRequest
                            )

                            is OAuthDeviceFlowAuthorizationResult.Application -> {
                                val session = authentiktInstance.createNewSession(
                                    DeviceFlow(
                                        deviceCode = result.deviceCode,
                                        userCode = result.userCode,
                                        applicationName = result.name,
                                        applicationId = clientId,
                                    )
                                )

                                call.respondGson(buildGenericMap {
                                    val verificationUri = URLBuilder(authentiktInstance.configuration.uiLoginBaseUrl).apply {
                                        parameters.append("_authentikt_flow_active", "true")
                                        parameters.append("_authentikt_session_id", session.sessionId)
                                    }.buildString()

                                    put("device_code", result.deviceCode)
                                    put("user_code", result.userCode)
                                    put("verification_uri", verificationUri)
                                    put("verification_uri_complete", verificationUri)
                                    put("expires_in", configuration.oAuthConfiguration.deviceCodeLifetime.inWholeSeconds)
                                    put("interval", 5.seconds.inWholeSeconds)
                                })
                            }
                        }
                    }
                }
            }
        }

        route("${configuration.apiPrefix}/authentikt") {
            route("/flow") {
                route("/{sessionId}") sessionScopedRoute@{
                    createRouteScopedPlugin("Get Session from Path") {
                        onCall { call ->
                            val session = findActiveSession(call.parameters["sessionId"])
                            if (session == null) {
                                call.respondGson(
                                    value = buildGenericMap {
                                        put("error", "session_not_found")
                                        put("error_description", "The session does not exist or has expired.")
                                    },
                                    status = HttpStatusCode.NotFound,
                                )
                                return@onCall
                            }
                            call.attributes[SessionKey] = session
                        }
                    }.let { this@sessionScopedRoute.install(it) }

                    route("/check") { checkFlowStatus<USER>() }
                    route("/alternatives") { switchToAlternative<USER>() }

                    route("/steps/plugins") {
                        configuration.installedPlugins.forEach { plugin ->
                            route("/${plugin.namespace}") pluginScopedRoute@{
                                plugin.installRoutes(this@pluginScopedRoute, authentiktInstance)
                            }
                        }
                    }
                }
            }

            route("/static") {
                route("/plugins") {
                    configuration.installedPlugins.forEach { plugin ->
                        route("/${plugin.namespace}") pluginScopedRoute@{
                            plugin.installStaticRoutes(this@pluginScopedRoute, authentiktInstance)
                        }
                    }
                }
            }
        }
    }

    return authentiktInstance
}
