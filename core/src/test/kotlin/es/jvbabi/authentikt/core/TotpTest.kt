package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.plugins.builtin.Base32
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPluginConfiguration
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPluginConfigurationBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class TotpTestClock : Clock {
    var now: Instant = Instant.fromEpochSeconds(0)
    override fun now(): Instant = now
    fun advance(duration: Duration) {
        now += duration
    }
}

/**
 * Uses the SHA1 test vectors of RFC 6238, Appendix B (secret "12345678901234567890", last six digits).
 */
class TotpTest {

    private companion object {
        const val RFC_SECRET_RAW = "12345678901234567890"
        const val RFC_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        // Time step 37037036 (T = 1111111109)
        const val CODE_STEP_36 = "081804"

        // Time step 37037037 (T = 1111111111)
        const val CODE_STEP_37 = "050471"
        val STEP_37_START: Instant = Instant.fromEpochSeconds(37037037L * 30)
    }

    @BeforeTest
    fun clearSessions() {
        sessions.clear()
    }

    private val clock = TotpTestClock()

    private fun configuration(
        block: TotpPluginConfigurationBuilder<String>.() -> Unit,
    ): TotpPluginConfiguration<String> = TotpPluginConfigurationBuilder<String>().apply {
        clock = this@TotpTest.clock
        getSecret { RFC_SECRET_BASE32 }
        block()
    }.build()

    @Test
    fun `base32 decoding follows RFC 4648`() {
        assertContentEquals(RFC_SECRET_RAW.toByteArray(), Base32.decode(RFC_SECRET_BASE32))
        assertContentEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
        assertContentEquals("foobar".toByteArray(), Base32.decode("mzxw 6ytb oi"))
        assertFailsWith<IllegalArgumentException> { Base32.decode("NOT-BASE32!") }
    }

    @Test
    fun `base32 secrets produce the codes of authenticator apps`() = runBlocking {
        val config = configuration { allowedDrift = 0 }

        clock.now = Instant.fromEpochSeconds(59)
        assertTrue(config.check("alice", "287082"))
        clock.now = Instant.fromEpochSeconds(1234567890)
        assertTrue(config.check("alice", "005924"))
        assertFalse(config.check("alice", "005925"))
    }

    @Test
    fun `raw secrets use the bytes of the string`() = runBlocking {
        val config = configuration {
            allowedDrift = 0
            secretEncoding = TotpPluginConfiguration.TotpSecretEncoding.Raw
            getSecret { RFC_SECRET_RAW }
        }

        clock.now = STEP_37_START
        assertTrue(config.check("alice", CODE_STEP_37))
    }

    @Test
    fun `codes of neighbouring windows are accepted within the allowed drift`() = runBlocking {
        val config = configuration {}

        // Previous window
        clock.now = STEP_37_START + 30.seconds
        assertTrue(config.check("alice", CODE_STEP_37))
        // Next window
        clock.now = STEP_37_START - 1.seconds
        assertTrue(config.check("alice", CODE_STEP_37))
        // Two windows back
        clock.now = STEP_37_START + 30.seconds
        assertFalse(config.check("alice", CODE_STEP_36))
    }

    @Test
    fun `only the current window is accepted without drift`() = runBlocking {
        val config = configuration { allowedDrift = 0 }

        clock.now = STEP_37_START + 29.seconds
        assertTrue(config.check("alice", CODE_STEP_37))
        clock.advance(1.seconds)
        assertFalse(config.check("alice", CODE_STEP_37))
    }

    @Test
    fun `malformed codes are rejected`() = runBlocking {
        val config = configuration {}
        clock.now = STEP_37_START

        assertFalse(config.check("alice", ""))
        assertFalse(config.check("alice", "50471"))
        assertFalse(config.check("alice", " 050471"))
        assertFalse(config.check("alice", "0504710"))
    }

    @Test
    fun `invalid configuration is rejected`() {
        assertFailsWith<IllegalArgumentException> { configuration { allowedDrift = -1 } }
        assertFailsWith<IllegalArgumentException> { configuration { digits = 0 } }
        assertFailsWith<IllegalArgumentException> { configuration { totpDuration = Duration.ZERO } }
    }

    @Test
    fun `replay protection rejects codes of already used time steps`() = runBlocking {
        val lastUsed = mutableMapOf<String, Long>()
        val config = configuration {
            preventReplay(
                getLastUsedTimeStep = { user -> lastUsed[user] },
                saveUsedTimeStep = { user, timeStep -> lastUsed[user] = timeStep },
            )
        }

        clock.now = STEP_37_START
        assertTrue(config.check("alice", CODE_STEP_37))
        assertEquals(37037037L, lastUsed["alice"])
        assertFalse(config.check("alice", CODE_STEP_37))

        // An older code within the drift is not accepted either
        assertFalse(config.check("alice", CODE_STEP_36))

        // Time steps are stored per user
        assertTrue(config.check("bob", CODE_STEP_37))
    }

    @Test
    fun `a used code cannot complete the step in another session`() = testApplication {
        val lastUsed = mutableMapOf<String, Long>()
        val totpPlugin = TotpPlugin<String> {
            clock = this@TotpTest.clock
            getSecret { RFC_SECRET_BASE32 }
            preventReplay(
                getLastUsedTimeStep = { user -> lastUsed[user] },
                saveUsedTimeStep = { user, timeStep -> lastUsed[user] = timeStep },
            )
        }
        val donePlugin = DonePlugin<String> { onSuccess { _, _ -> } }
        lateinit var instance: AuthentiktInstance<String>
        application {
            install(ContentNegotiation) { json() }
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                clock = this@TotpTest.clock
                sessionTimeout = 1000.minutes
                install(totpPlugin)
                install(donePlugin)
                authorization { session -> if (!session.has(totpPlugin)) totpPlugin else donePlugin }
            }
        }
        startApplication()
        clock.now = STEP_37_START

        suspend fun submit(): Boolean {
            val session = instance.createNewSession()
            session.identifiedUser = object : AuthentiktUser<String>("alice") {
                override suspend fun getEmail(): String = "alice@example.com"
                override suspend fun getUsername(): String = "alice"
                override suspend fun getDisplayName(): String = "alice"
            }
            client.get("/authentikt/flow/${session.sessionId}/check")
            val response = client.post("/authentikt/flow/${session.sessionId}/steps/plugins/${totpPlugin.namespace}") {
                contentType(ContentType.Application.Json)
                setBody("""{"totp_code":"$CODE_STEP_37"}""")
            }
            return Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("success").jsonPrimitive.boolean
        }

        assertTrue(submit())
        assertFalse(submit())
    }
}
