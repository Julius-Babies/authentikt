package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.OAuthAccessToken
import es.jvbabi.authentikt.core.config.OAuthDeviceFlowAuthorizationResult
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.EmptyBaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.PasswordPlugin
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

private fun concurrencyTestUser(name: String) = object : AuthentiktUser<String>(name) {
    override suspend fun getEmail(): String = "$name@example.com"
    override suspend fun getUsername(): String = name
    override suspend fun getDisplayName(): String = name
}

/**
 * Step plugin that records how many of its requests run at the same time.
 */
private class ConcurrencyProbePlugin : BasePlugin<String, EmptyBaseState>("test/probe") {
    private val running = AtomicInteger()
    val maxRunning = AtomicInteger()

    override suspend fun createState(session: Session<*>) = EmptyBaseState()

    override fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<String>) {
        inRoute.get {
            maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            delay(50.milliseconds)
            running.decrementAndGet()
            call.respondText("ok")
        }
    }
}

class SessionConcurrencyTest {

    @BeforeTest
    fun clearSessions() {
        sessions.clear()
    }

    private suspend fun <T> parallel(times: Int, block: suspend () -> T): List<T> = coroutineScope {
        (1..times).map { async { block() } }.awaitAll()
    }

    @Test
    fun `requests for the same session are processed one after another`() = testApplication {
        val probe = ConcurrencyProbePlugin()
        lateinit var instance: AuthentiktInstance<String>
        application {
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                install(probe)
                authorization { probe }
            }
        }
        startApplication()
        val session = instance.createNewSession()
        client.get("/authentikt/flow/${session.sessionId}/check")

        parallel(5) { client.get("/authentikt/flow/${session.sessionId}/steps/plugins/${probe.namespace}") }

        assertEquals(1, probe.maxRunning.get())
    }

    @Test
    fun `duplicate step submissions advance the flow only once`() = testApplication {
        val passwordChecks = AtomicInteger()
        val passwordPlugin = PasswordPlugin<String> {
            checkPassword { _, password ->
                passwordChecks.incrementAndGet()
                delay(20.milliseconds)
                password == "secret"
            }
        }
        val donePlugin = DonePlugin<String> { onSuccess { _, _ -> } }
        lateinit var instance: AuthentiktInstance<String>
        application {
            install(ContentNegotiation) { json() }
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                install(passwordPlugin)
                install(donePlugin)
                authorization { session -> if (!session.has(passwordPlugin)) passwordPlugin else donePlugin }
            }
        }
        startApplication()
        val session = instance.createNewSession()
        session.identifiedUser = concurrencyTestUser("alice")
        client.get("/authentikt/flow/${session.sessionId}/check")

        val statuses = parallel(5) {
            client.post("/authentikt/flow/${session.sessionId}/steps/plugins/${passwordPlugin.namespace}") {
                contentType(ContentType.Application.Json)
                setBody("""{"password":"secret"}""")
            }.status
        }

        assertEquals(1, statuses.count { it == HttpStatusCode.OK })
        assertEquals(4, statuses.count { it == HttpStatusCode.Conflict })
        assertEquals(1, passwordChecks.get())
        assertEquals(listOf(passwordPlugin, donePlugin), session.authenticationSteps.map { it.first })
    }

    @Test
    fun `device code can only be redeemed once`() = testApplication {
        val issuedTokens = AtomicInteger()
        val donePlugin = DonePlugin<String> {
            onSuccess { _, _ -> }
            onOAuthSuccess { _, user ->
                delay(20.milliseconds)
                OAuthAccessToken("token-${issuedTokens.incrementAndGet()}-for-$user", null, 1.days)
            }
        }
        application {
            installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                install(donePlugin)
                authorization { donePlugin }
                oauth {
                    onDeviceFlow { clientId ->
                        OAuthDeviceFlowAuthorizationResult.Application(clientId, "Test App", "device-code", "ABC123")
                    }
                }
            }
        }
        client.submitForm("/oauth/device/code", parameters { append("client_id", "tv") })
        @Suppress("UNCHECKED_CAST")
        val session = sessions.values.single() as Session<String>
        session.identifiedUser = concurrencyTestUser("bob")
        client.get("/authentikt/flow/${session.sessionId}/check")

        val statuses = parallel(5) {
            client.submitForm(
                "/oauth/token",
                parameters {
                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    append("device_code", "device-code")
                    append("client_id", "tv")
                },
            ).status
        }

        assertEquals(1, statuses.count { it == HttpStatusCode.OK })
        assertEquals(1, issuedTokens.get())
    }

    @Test
    fun `session lock is re-entrant`() = runBlocking {
        lateinit var session: Session<String>
        testApplication {
            application {
                session = installAuthentikt<String> {
                    baseUrl = "http://localhost"
                    uiLoginBaseUrl = "http://localhost/"
                    val donePlugin = DonePlugin<String> { onSuccess { _, _ -> } }
                    install(donePlugin)
                    authorization { donePlugin }
                }.createNewSession()
            }
            startApplication()
        }

        val result = session.withLock { session.withLock { "nested" } }

        assertEquals("nested", result)
    }
}
