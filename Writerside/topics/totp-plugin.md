# TOTP

`TotpPlugin` verifies a time-based one-time password (RFC 6238), the six-digit codes shown by authenticator apps.

**Namespace:** `authentikt-builtin/totp`

## Configuration

You can let the plugin generate the expected code from a stored secret, or verify the code yourself.

<tabs>
<tab title="Custom validation">

```kotlin
val totpPlugin = TotpPlugin<User> {
    validate { user, code ->
        totpService.verify(user.totpSecret!!, code)
    }
}
```

`validate { user: USER, code: String -> Boolean }`
: Your own check, for example with your existing TOTP service.

</tab>
<tab title="Server-side generation">

```kotlin
val totpPlugin = TotpPlugin<User> {
    getSecret { user -> user.totpSecret!! }
}
```

`getSecret { user: USER -> String }`
: Returns the shared secret, Base32-encoded by default (the format authenticator apps receive in the
`otpauth://` URI). The plugin computes the codes for the current time window and its neighbours (see
`allowedDrift`) and compares them with the submitted code in constant time.

</tab>
</tabs>

You must configure at least one of `validate` and `getSecret`. If both are set, `getSecret` is used.

`rateLimit` (default: `5 triesPer 5.minutes`)
: Failed attempts allowed per user. `null` disables the limit. See [](rate-limiting.md).

```kotlin
val totpPlugin = TotpPlugin<User> {
    rateLimit = 3 triesPer 3.minutes
    getSecret { user -> user.totpSecret!! }
}
```

### Options for server-side generation

These options only apply when `getSecret` is used:

| Property | Default | Description |
|----------|---------|-------------|
| `digits` | `6` | Number of digits in a code |
| `totpDuration` | `30.seconds` | Length of a time window |
| `hmacAlgorithm` | `SHA1` | `SHA1`, `SHA256` or `SHA512` (`TotpPluginConfiguration.TotpHmacAlgorithm`) |
| `secretEncoding` | `Base32` | `Base32` or `Raw` (`TotpPluginConfiguration.TotpSecretEncoding`). `Raw` uses the UTF-8 bytes of the string as the key |
| `allowedDrift` | `1` | Number of windows before and after the current one whose codes are also accepted, to tolerate clock drift. `0` accepts only the current window |
| `clock` | `Clock.System` | Time source (`kotlin.time.Clock`). Useful for tests |

<note>
Before Base32 became the default, the secret was used as raw UTF-8 bytes. If your stored secrets were generated
for that behaviour, set <code>secretEncoding = TotpPluginConfiguration.TotpSecretEncoding.Raw</code>.
</note>

### Replay protection

Without further configuration, a correct code can be used again as long as it is within the accepted windows.
To prevent this, store the time step of the last accepted code per user. The library has no storage, so you
provide it through two callbacks:

```kotlin
val totpPlugin = TotpPlugin<User> {
    getSecret { user -> user.totpSecret!! }
    preventReplay(
        getLastUsedTimeStep = { user -> userRepository.lastTotpTimeStep(user.id) },
        saveUsedTimeStep = { user, timeStep -> userRepository.saveLastTotpTimeStep(user.id, timeStep) },
    )
}
```

`preventReplay(getLastUsedTimeStep: (USER) -> Long?, saveUsedTimeStep: (USER, Long) -> Unit)`
: A time step is the number of `totpDuration` windows since the Unix epoch. A code is only accepted if its time
step is newer than the stored one; `saveUsedTimeStep` is called before the step is completed.

Reading, checking and saving run under a lock within one plugin instance. Across several server instances, two
simultaneous submissions of the same code can both be accepted. As a consequence of the protection, a user can
complete the TOTP step at most once per time window. `preventReplay` has no effect with `validate`.

## Behaviour

- Requires `session.identifiedUser`.
- If the code is correct, the step is marked completed and `session.nextStep()` is called.
- If not, the step stays active until the [rate limit](rate-limiting.md) is reached. While the step is locked,
  submissions are answered with `429` without checking the code.

TOTP is usually optional per user. Return the plugin from the step-order callback only for users who have a
secret:

```kotlin
user.user.totpSecret != null && !session.has(totpPlugin) -> totpPlugin
```

## Testing with a fixed clock

For tests, you can pin the clock so the expected code is always the same:

```kotlin
val fixedClock = object : Clock {
    override fun now(): Instant = Instant.fromEpochSeconds(1776442771)
}

val totpPlugin = TotpPlugin<User> {
    clock = fixedClock
    getSecret { user -> user.otpSecret!! }
}
```

## HTTP contract

**Payload**

```json
{
  "validated": false,
  "rate_limit": { "max_tries": 5, "period_seconds": 300, "remaining_tries": 5 }
}
```

`rate_limit` is omitted if the limit is disabled. See [](rate-limiting.md#client-state).

**Request:** `POST /flow/{sessionId}/steps/plugins/authentikt-builtin/totp`

```json
{ "totp_code": "476885" }
```

**Responses**

```json
{ "success": true }
```

```json
{ "success": false, "rate_limit": { "max_tries": 5, "period_seconds": 300, "remaining_tries": 4 } }
```

While locked: `429 Too Many Requests` with `{ "success": false, "error": "rate_limited", "rate_limit": { ... } }`.

## Frontend

Use [`TotpRenderer`](frontend-renderers.md#totp). Its plugin instance exposes `totp`, `status`
(`"ready" | "loading" | "totp_incorrect" | "rate_limited" | "error"`), `rateLimit` and `submit()`.
