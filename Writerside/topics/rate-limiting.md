# Rate limiting

Steps that take user input limit failed attempts, so passwords and one-time codes cannot be guessed and accounts
cannot be enumerated by trying addresses. The limit is enabled by default for the
[email](email-plugin.md), [password](password-plugin.md) and [TOTP](totp-plugin.md) steps.

## Configuration

Set `rateLimit` in the plugin's DSL. `triesPer` turns a number of attempts and a `Duration` into a limit:

```kotlin
import es.jvbabi.authentikt.core.ratelimit.triesPer
import kotlin.time.Duration.Companion.minutes

val passwordPlugin = PasswordPlugin<User> {
    rateLimit = 3 triesPer 3.minutes
    checkPassword { user, password -> passwordHasher.verify(password, user.passwordHash) }
}
```

Set `rateLimit = null` to disable the limit, for example if you already limit attempts elsewhere.

| Plugin | Default | Counted per | Counted attempts |
|--------|---------|-------------|------------------|
| `EmailUserSelectionPlugin` | `10 triesPer 1.minutes` | Session | Lookups without a match |
| `PasswordPlugin` | `5 triesPer 5.minutes` | User | Wrong passwords |
| `TotpPlugin` | `5 triesPer 5.minutes` | User | Wrong codes |

## How it works

For each key (a session or a user), the plugin stores the timestamps of failed attempts. The limit is a sliding
window: an attempt counts as long as it is younger than the period. From these timestamps, authentikt derives

- the **remaining tries**: allowed attempts minus attempts in the window, and
- the **remaining lock time**: once no tries remain, the time until the oldest relevant attempt leaves the window.

With `3 triesPer 3.minutes`, failed attempts at 12:00, 12:01 and 12:02 lock the step until 12:03. Then the first
attempt leaves the window and one more attempt is allowed.

While a step is locked, submissions are rejected before your callback runs, even if the input is correct. A
successful attempt clears the failed attempts of its key.

An attempt is recorded before the input is checked, so parallel requests cannot exceed the limit.

### Per user and per session

Password and TOTP attempts are counted per user (by username, falling back to the email) across all sessions.
Starting a new session does not reset the limit. The email step has no user yet, so it counts attempts per
session.

> Because password and TOTP attempts are counted per user, anyone who knows a username can lock that user out for
> the length of the period. Keep periods short. The email step's limit can be bypassed by starting new sessions;
> combine it with a per-IP limit such as Ktor's [RateLimit](https://ktor.io/docs/server-rate-limit.html) plugin on
> your login route if enumeration matters.
{style="note"}

Attempts are kept in memory, like sessions. They are lost on restart and not shared between server instances.

## Client state

Rate-limited steps add `rate_limit` to their payload in the [`check` response](http-api.md):

```json
{
  "validated": false,
  "rate_limit": {
    "max_tries": 3,
    "period_seconds": 180,
    "remaining_tries": 0,
    "retry_after_seconds": 42
  }
}
```

| Field | Description |
|-------|-------------|
| `max_tries` | Failed attempts allowed per period |
| `period_seconds` | Length of the period |
| `remaining_tries` | Attempts left before the step is locked |
| `retry_after_seconds` | Seconds until the next attempt is allowed. Only present while the step is locked |

`retry_after_seconds` is relative, so the client does not depend on its clock being in sync with the server.

Failed submissions include the same `rate_limit` object in their response. Submissions while the step is locked are
answered with `429 Too Many Requests` and a `Retry-After` header:

```json
{
  "success": false,
  "error": "rate_limited",
  "rate_limit": { "max_tries": 3, "period_seconds": 180, "remaining_tries": 0, "retry_after_seconds": 42 }
}
```

The email step answers with `"type": "rate_limited"` instead of `"success": false`.

In authentikt-svelte, the email, password and TOTP plugin instances expose this as `rateLimit`, see
[](frontend-renderers.md#rate-limits).

## In custom step plugins {id="custom-plugins"}

`RateLimiter` from `es.jvbabi.authentikt.core.ratelimit` can be used in your own plugins:

```kotlin
private val rateLimiter = RateLimiter.perSession(5 triesPer 5.minutes)

override fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {
    inRoute.post {
        val session = call.attributes[SessionKey] as Session<USER>
        if (!session.isActive(this@MyPlugin)) return@post call.respondStepNotActive()

        val attempt = rateLimiter.tryAcquire(session)
        if (!attempt.allowed) return@post call.respondRateLimited(attempt.status)

        if (!check(...)) {
            call.respondGson(buildGenericMap {
                put("success", false)
                put("rate_limit", attempt.status.toClientState())
            })
            return@post
        }

        rateLimiter.reset(session)
        // complete the step
    }
}
```

`RateLimiter.perSession` and `RateLimiter.perUser` cover the built-in keys. For other keys, pass a function:
`RateLimiter(limit) { session -> "..." }`. To show the limit in the UI, put
`rateLimiter.status(session).toClientState()` into your state's `createClientState` as `rate_limit`.
