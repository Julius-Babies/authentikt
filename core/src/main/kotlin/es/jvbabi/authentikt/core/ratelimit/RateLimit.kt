package es.jvbabi.authentikt.core.ratelimit

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Allows at most [maxTries] failed attempts within a sliding window of [period].
 *
 * Create it with [triesPer]:
 * ```kotlin
 * rateLimit = 3 triesPer 3.minutes
 * ```
 */
data class RateLimit(
    val maxTries: Int,
    val period: Duration,
) {
    init {
        require(maxTries > 0) { "maxTries must be positive" }
        require(period.isPositive()) { "period must be positive" }
    }
}

/**
 * Allows this many failed attempts per [period], see [RateLimit].
 */
infix fun Int.triesPer(period: Duration): RateLimit = RateLimit(maxTries = this, period = period)

/**
 * Snapshot of a [RateLimiter] for one key.
 *
 * @param limit the configured limit.
 * @param remainingTries attempts left before the key is locked.
 * @param retryAfter time until the next attempt is allowed, or `null` if the key is not locked.
 */
data class RateLimitStatus(
    val limit: RateLimit,
    val remainingTries: Int,
    val retryAfter: Duration?,
) {
    val isLocked: Boolean get() = retryAfter != null

    /**
     * The representation sent to the client as `rate_limit`.
     */
    fun toClientState(): Map<String, Any?> = buildGenericMap {
        put("max_tries", limit.maxTries)
        put("period_seconds", limit.period.inWholeSeconds)
        put("remaining_tries", remainingTries)
        put("retry_after_seconds", retryAfter?.ceilSeconds())
    }
}

/**
 * The result of [RateLimiter.tryAcquire].
 *
 * @param allowed whether the attempt may be checked. If `false`, the key is locked and nothing was recorded.
 * @param status the status after the attempt was recorded.
 */
data class RateLimitAttempt(
    val allowed: Boolean,
    val status: RateLimitStatus,
)

/**
 * Limits failed attempts of a step, keyed per session or user.
 *
 * The limiter keeps the timestamps of failed attempts per key. Remaining tries and the remaining lock duration are
 * derived from the attempts within the last [RateLimit.period].
 *
 * Call [tryAcquire] before checking the user's input. It records the attempt as failed up front, so concurrent
 * requests cannot exceed the limit. Call [reset] when the attempt succeeds.
 *
 * ### Usage in a custom step plugin
 * ```kotlin
 * private val rateLimiter = RateLimiter.perSession(5 triesPer 5.minutes)
 *
 * post {
 *     val attempt = rateLimiter.tryAcquire(session)
 *     if (!attempt.allowed) return@post call.respondRateLimited(attempt.status)
 *     if (check(...)) rateLimiter.reset(session)
 * }
 * ```
 *
 * @param limit the allowed failed attempts per period.
 * @param key returns the key attempts are counted for, e.g. the session id or the user's name.
 */
class RateLimiter(
    val limit: RateLimit,
    private val key: suspend (session: Session<*>) -> String,
) {
    private val failedAttempts = ConcurrentHashMap<String, List<Instant>>()

    @Volatile
    private var lastCleanup: Instant = Instant.DISTANT_PAST

    /**
     * Returns the current status for the key of [session] without recording an attempt.
     */
    suspend fun status(session: Session<*>): RateLimitStatus {
        val now = session.clock.now()
        return statusOf(failedAttempts[key(session)].orEmpty().recent(now), now)
    }

    /**
     * Records an attempt for the key of [session] unless it is locked.
     */
    suspend fun tryAcquire(session: Session<*>): RateLimitAttempt {
        val now = session.clock.now()
        var attempt: RateLimitAttempt? = null
        failedAttempts.compute(key(session)) { _, attempts ->
            val recent = attempts.orEmpty().recent(now)
            val allowed = recent.size < limit.maxTries
            val updated = if (allowed) recent + now else recent
            attempt = RateLimitAttempt(allowed, statusOf(updated, now))
            updated.ifEmpty { null }
        }
        cleanup(now)
        return attempt!!
    }

    /**
     * Forgets all failed attempts for the key of [session], usually after a successful attempt.
     */
    suspend fun reset(session: Session<*>) {
        failedAttempts.remove(key(session))
    }

    companion object {
        /**
         * Counts attempts per session. Starting a new session starts with a fresh limit.
         */
        fun perSession(limit: RateLimit): RateLimiter = RateLimiter(limit) { session -> "session:${session.sessionId}" }

        /**
         * Counts attempts per identified user across all sessions, identified by username, falling back to the email.
         * Without an identified user, attempts are counted per session.
         */
        fun perUser(limit: RateLimit): RateLimiter = RateLimiter(limit) { session ->
            val user = session.identifiedUser
            val name = user?.getUsername() ?: user?.getEmail()
            if (name != null) "user:$name" else "session:${session.sessionId}"
        }
    }

    private fun List<Instant>.recent(now: Instant) = filter { it > now - limit.period }

    private fun statusOf(attempts: List<Instant>, now: Instant): RateLimitStatus {
        val remainingTries = (limit.maxTries - attempts.size).coerceAtLeast(0)
        // Locked until enough attempts have left the window to allow one more
        val retryAfter = if (remainingTries > 0) null else attempts[attempts.size - limit.maxTries] + limit.period - now
        return RateLimitStatus(limit, remainingTries, retryAfter)
    }

    /**
     * Removes keys without attempts in the current window, at most once per period.
     */
    private fun cleanup(now: Instant) {
        if (now - lastCleanup < limit.period) return
        lastCleanup = now
        failedAttempts.entries.removeIf { (_, attempts) -> attempts.last() <= now - limit.period }
    }
}

private fun Duration.ceilSeconds(): Long = (inWholeMilliseconds + 999) / 1000

/**
 * Responds with `429 Too Many Requests`, a `Retry-After` header and [body] extended by the `rate_limit` state.
 */
suspend fun ApplicationCall.respondRateLimited(
    status: RateLimitStatus,
    body: Map<String, Any?> = emptyMap(),
) {
    status.retryAfter?.let { response.header(HttpHeaders.RetryAfter, it.ceilSeconds()) }
    respondGson(
        value = buildGenericMap {
            putAll(body)
            put("error", "rate_limited")
            put("rate_limit", status.toClientState())
        },
        status = HttpStatusCode.TooManyRequests,
    )
}
