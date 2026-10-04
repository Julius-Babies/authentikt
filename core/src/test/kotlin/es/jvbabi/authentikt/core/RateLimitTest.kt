package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktPluginConfigurationBuilder
import es.jvbabi.authentikt.core.ratelimit.triesPer
import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.EmailUserSelectionPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.PasswordPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPlugin
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class RateLimitTestClock : Clock {
    var now: Instant = Instant.fromEpochSeconds(1_700_000_000)
    override fun now(): Instant = now
    fun advance(duration: Duration) {
        now += duration
    }
}

private fun rateLimitTestUser(name: String) = object : AuthentiktUser<String>(name) {
    override suspend fun getEmail(): String = "$name@example.com"
    override suspend fun getUsername(): String = name
    override suspend fun getDisplayName(): String = name
}

class RateLimitTest {

    @BeforeTest
    fun clearSessions() {
        sessions.clear()
    }

    private val clock = RateLimitTestClock()

    private val emailPlugin = EmailUserSelectionPlugin<String> {
        rateLimit = 2 triesPer 1.minutes
        findUserByEmail { email -> if (email == "alice@example.com") rateLimitTestUser("alice") else null }
    }
    private val passwordPlugin = PasswordPlugin<String> {
        rateLimit = 3 triesPer 3.minutes
        checkPassword { _, password -> password == "secret" }
    }
    private val totpPlugin = TotpPlugin<String> {
        rateLimit = null
        validate { _, code -> code == "123456" }
    }
    private val donePlugin = DonePlugin<String> { onSuccess { _, _ -> } }

    private fun ApplicationTestBuilder.setup(
        configure: AuthentiktPluginConfigurationBuilder<String>.() -> Unit,
    ): () -> AuthentiktInstance<String> {
        lateinit var instance: AuthentiktInstance<String>
        application {
            install(ContentNegotiation) { json() }
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                clock = this@RateLimitTest.clock
                sessionTimeout = 1000.minutes
                install(donePlugin)
                configure()
            }
        }
        return { instance }
    }

    private suspend fun ApplicationTestBuilder.passwordSession(instance: AuthentiktInstance<String>, user: String): String {
        val session = instance.createNewSession()
        session.identifiedUser = rateLimitTestUser(user)
        check(session.sessionId)
        return session.sessionId
    }

    private suspend fun ApplicationTestBuilder.check(sessionId: String): JsonObject =
        Json.parseToJsonElement(client.get("/authentikt/flow/$sessionId/check").bodyAsText()).jsonObject

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) = client.post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun ApplicationTestBuilder.submitPassword(sessionId: String, password: String) =
        postJson("/authentikt/flow/$sessionId/steps/plugins/${passwordPlugin.namespace}", """{"password":"$password"}""")

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun JsonObject.rateLimit(): JsonObject = getValue("rate_limit").jsonObject
    private fun JsonObject.remainingTries() = getValue("remaining_tries").jsonPrimitive.int
    private fun JsonObject.retryAfterSeconds() = get("retry_after_seconds")?.jsonPrimitive?.long

    private fun AuthentiktPluginConfigurationBuilder<String>.passwordFlow() {
        install(passwordPlugin)
        authorization { session -> if (!session.has(passwordPlugin)) passwordPlugin else donePlugin }
    }

    @Test
    fun `failed password attempts lock the step until the oldest attempt leaves the window`() = testApplication {
        val instance = setup { passwordFlow() }
        startApplication()
        val sessionId = passwordSession(instance(), "alice")

        val initial = check(sessionId).getValue("payload").jsonObject.rateLimit()
        assertEquals(3, initial.getValue("max_tries").jsonPrimitive.int)
        assertEquals(180, initial.getValue("period_seconds").jsonPrimitive.int)
        assertEquals(3, initial.remainingTries())
        assertNull(initial.retryAfterSeconds())

        val first = submitPassword(sessionId, "wrong").json()
        assertEquals(2, first.rateLimit().remainingTries())
        clock.advance(1.minutes)
        submitPassword(sessionId, "wrong")
        clock.advance(1.minutes)
        val third = submitPassword(sessionId, "wrong").json()
        assertEquals(0, third.rateLimit().remainingTries())
        assertEquals(60, third.rateLimit().retryAfterSeconds())

        // Locked: even the correct password is not checked
        val locked = submitPassword(sessionId, "secret")
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals("60", locked.headers[HttpHeaders.RetryAfter])
        assertEquals("rate_limited", locked.json().getValue("error").jsonPrimitive.content)
        assertEquals(60, check(sessionId).getValue("payload").jsonObject.rateLimit().retryAfterSeconds())

        clock.advance(30.seconds)
        assertEquals(30, check(sessionId).getValue("payload").jsonObject.rateLimit().retryAfterSeconds())

        // The first attempt has left the window, one more attempt is allowed
        clock.advance(30.seconds)
        val unlocked = check(sessionId).getValue("payload").jsonObject.rateLimit()
        assertEquals(1, unlocked.remainingTries())
        assertNull(unlocked.retryAfterSeconds())

        assertEquals(HttpStatusCode.OK, submitPassword(sessionId, "secret").status)
        assertEquals(donePlugin.namespace, check(sessionId).getValue("namespace").jsonPrimitive.content)
    }

    @Test
    fun `password attempts are limited per user across sessions`() = testApplication {
        val instance = setup { passwordFlow() }
        startApplication()

        repeat(3) { submitPassword(passwordSession(instance(), "alice"), "wrong") }

        val newSession = passwordSession(instance(), "alice")
        assertEquals(0, check(newSession).getValue("payload").jsonObject.rateLimit().remainingTries())
        assertEquals(HttpStatusCode.TooManyRequests, submitPassword(newSession, "secret").status)

        // Other users are not affected
        val otherUser = passwordSession(instance(), "bob")
        assertEquals(HttpStatusCode.OK, submitPassword(otherUser, "secret").status)
    }

    @Test
    fun `a successful attempt resets the failed attempts`() = testApplication {
        val instance = setup { passwordFlow() }
        startApplication()

        repeat(2) { submitPassword(passwordSession(instance(), "alice"), "wrong") }
        submitPassword(passwordSession(instance(), "alice"), "secret")

        assertEquals(3, check(passwordSession(instance(), "alice")).getValue("payload").jsonObject.rateLimit().remainingTries())
    }

    @Test
    fun `concurrent attempts cannot exceed the limit`() = testApplication {
        val checkedPasswords = java.util.concurrent.atomic.AtomicInteger()
        val countingPlugin = PasswordPlugin<String> {
            rateLimit = 3 triesPer 3.minutes
            checkPassword { _, _ -> checkedPasswords.incrementAndGet(); false }
        }
        val instance = setup {
            install(countingPlugin)
            authorization { countingPlugin }
        }
        startApplication()
        val sessionId = passwordSession(instance(), "alice")

        val statuses = coroutineScope {
            (1..10).map {
                async {
                    postJson("/authentikt/flow/$sessionId/steps/plugins/${countingPlugin.namespace}", """{"password":"wrong"}""").status
                }
            }.awaitAll()
        }

        assertEquals(3, checkedPasswords.get())
        assertEquals(7, statuses.count { it == HttpStatusCode.TooManyRequests })
    }

    @Test
    fun `email lookups without a match are limited per session`() = testApplication {
        val instance = setup {
            install(emailPlugin)
            authorization { session -> if (!session.has(emailPlugin)) emailPlugin else donePlugin }
        }
        startApplication()
        val sessionId = instance().createNewSession().sessionId
        check(sessionId)
        val path = "/authentikt/flow/$sessionId/steps/plugins/${emailPlugin.namespace}"

        val first = postJson(path, """{"email":"nobody@example.com"}""").json()
        assertEquals("user_not_found", first.getValue("type").jsonPrimitive.content)
        assertEquals(1, first.rateLimit().remainingTries())
        postJson(path, """{"email":"nobody@example.com"}""")

        val locked = postJson(path, """{"email":"alice@example.com"}""")
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals("rate_limited", locked.json().getValue("type").jsonPrimitive.content)

        // A new session starts with a fresh limit
        val otherSession = instance().createNewSession().sessionId
        check(otherSession)
        val success = postJson("/authentikt/flow/$otherSession/steps/plugins/${emailPlugin.namespace}", """{"email":"alice@example.com"}""")
        assertEquals("success", success.json().getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `a disabled rate limit is not sent to the client`() = testApplication {
        val instance = setup {
            install(totpPlugin)
            authorization { session -> if (!session.has(totpPlugin)) totpPlugin else donePlugin }
        }
        startApplication()
        val sessionId = passwordSession(instance(), "alice")

        assertFalse("rate_limit" in check(sessionId).getValue("payload").jsonObject)
        repeat(10) {
            val response = postJson("/authentikt/flow/$sessionId/steps/plugins/${totpPlugin.namespace}", """{"totp_code":"000000"}""")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
