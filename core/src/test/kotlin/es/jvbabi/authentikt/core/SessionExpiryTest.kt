package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktPluginConfigurationBuilder
import es.jvbabi.authentikt.core.config.OAuthAccessToken
import es.jvbabi.authentikt.core.config.OAuthDeviceFlowAuthorizationResult
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class TestClock : Clock {
    var now: Instant = Instant.fromEpochSeconds(1_700_000_000)
    override fun now(): Instant = now
    fun advance(duration: Duration) {
        now += duration
    }
}

private fun testUser(name: String) = object : AuthentiktUser<String>(name) {
    override suspend fun getEmail(): String = "$name@example.com"
    override suspend fun getUsername(): String = name
    override suspend fun getDisplayName(): String = name
}

class SessionExpiryTest {

    private val clock = TestClock()

    private val donePlugin = DonePlugin<String> {
        onSuccess { _, _ -> }
        onOAuthSuccess { _, user -> OAuthAccessToken("token-for-$user", null, 1.days) }
    }

    @BeforeTest
    fun clearSessions() {
        sessions.clear()
    }

    private fun ApplicationTestBuilder.setup(
        configure: AuthentiktPluginConfigurationBuilder<String>.() -> Unit = {},
    ): () -> AuthentiktInstance<String> {
        lateinit var instance: AuthentiktInstance<String>
        application {
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                clock = this@SessionExpiryTest.clock
                sessionTimeout = 30.minutes
                install(donePlugin)
                authorization { donePlugin }
                oauth {
                    deviceCodeLifetime = 5.minutes
                    onDeviceFlow { clientId ->
                        OAuthDeviceFlowAuthorizationResult.Application(clientId, "Test App", "device-code", "ABC123")
                    }
                }
                configure()
            }
        }
        return { instance }
    }

    private suspend fun HttpClient.check(sessionId: String): HttpResponse =
        get("/authentikt/flow/$sessionId/check")

    private suspend fun HttpClient.requestDeviceCode(): HttpResponse =
        submitForm("/oauth/device/code", parameters { append("client_id", "tv") })

    private suspend fun HttpClient.pollToken(): HttpResponse =
        submitForm(
            "/oauth/token",
            parameters {
                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                append("device_code", "device-code")
                append("client_id", "tv")
            },
        )

    @Test
    fun `unknown session returns 404`() = testApplication {
        setup()

        val response = client.check("does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertContains(response.bodyAsText(), "session_not_found")
    }

    @Test
    fun `session expires after inactivity`() = testApplication {
        val instance = setup()
        startApplication()
        val session = instance().createNewSession()

        assertEquals(HttpStatusCode.OK, client.check(session.sessionId).status)

        clock.advance(31.minutes)

        assertEquals(HttpStatusCode.NotFound, client.check(session.sessionId).status)
        assertFalse(session.sessionId in sessions)
    }

    @Test
    fun `activity extends the session`() = testApplication {
        val instance = setup()
        startApplication()
        val session = instance().createNewSession()

        repeat(3) {
            clock.advance(20.minutes)
            assertEquals(HttpStatusCode.OK, client.check(session.sessionId).status)
        }
    }

    @Test
    fun `session is removed after done plugin completed`() = testApplication {
        val instance = setup()
        startApplication()
        val session = instance().createNewSession()
        session.identifiedUser = testUser("alice")

        assertEquals(HttpStatusCode.OK, client.check(session.sessionId).status)
        val done = client.get("/authentikt/flow/${session.sessionId}/steps/plugins/${donePlugin.namespace}")
        assertEquals(HttpStatusCode.OK, done.status)

        assertFalse(session.sessionId in sessions)
        assertEquals(HttpStatusCode.NotFound, client.check(session.sessionId).status)
    }

    @Test
    fun `expired sessions are removed periodically`() = testApplication {
        val instance = setup { sessionCleanupInterval = 10.milliseconds }
        startApplication()
        val session = instance().createNewSession()

        clock.advance(31.minutes)

        val deadline = System.currentTimeMillis() + 5.seconds.inWholeMilliseconds
        while (session.sessionId in sessions && System.currentTimeMillis() < deadline) delay(10)
        assertFalse(session.sessionId in sessions)
    }

    @Test
    fun `device code announces configured lifetime`() = testApplication {
        setup()

        val response = client.requestDeviceCode()

        assertContains(response.bodyAsText(), "\"expires_in\":300")
    }

    @Test
    fun `device code expires after its lifetime`() = testApplication {
        setup()
        client.requestDeviceCode()

        assertContains(client.pollToken().bodyAsText(), "authorization_pending")

        clock.advance(5.minutes)

        assertContains(client.pollToken().bodyAsText(), "expired_token")
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `device flow session is removed after token exchange`() = testApplication {
        setup()
        client.requestDeviceCode()
        @Suppress("UNCHECKED_CAST")
        val session = sessions.values.single() as Session<String>
        session.identifiedUser = testUser("bob")
        assertEquals(HttpStatusCode.OK, client.check(session.sessionId).status)

        val token = client.pollToken()
        assertEquals(HttpStatusCode.OK, token.status)
        assertContains(token.bodyAsText(), "token-for-bob")
        assertTrue(sessions.isEmpty())

        clock.advance(10.seconds)
        assertContains(client.pollToken().bodyAsText(), "expired_token")
    }

    @Test
    fun `non-positive session timeout is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                setup { sessionTimeout = Duration.ZERO }
                startApplication()
            }
        }
    }
}
