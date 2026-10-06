package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPluginConfiguration
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPluginConfigurationBuilder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
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
        const val RFC_SECRET = "12345678901234567890"

        // Time step 37037036 (T = 1111111109)
        const val CODE_STEP_36 = "081804"

        // Time step 37037037 (T = 1111111111)
        const val CODE_STEP_37 = "050471"
        val STEP_37_START: Instant = Instant.fromEpochSeconds(37037037L * 30)
    }

    private val clock = TotpTestClock()

    private fun configuration(
        block: TotpPluginConfigurationBuilder<String>.() -> Unit,
    ): TotpPluginConfiguration<String> = TotpPluginConfigurationBuilder<String>().apply {
        clock = this@TotpTest.clock
        getSecret { RFC_SECRET }
        block()
    }.build()

    @Test
    fun `codes match the RFC 6238 test vectors`() = runBlocking {
        val config = configuration { allowedDrift = 0 }

        clock.now = Instant.fromEpochSeconds(59)
        assertTrue(config.check("alice", "287082"))
        clock.now = Instant.fromEpochSeconds(1234567890)
        assertTrue(config.check("alice", "005924"))
        assertFalse(config.check("alice", "005925"))
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
}
