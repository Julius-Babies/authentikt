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
: Returns the shared secret. The plugin computes the code for the current time window and compares it with the
submitted code.

</tab>
</tabs>

You must configure at least one of `validate` and `getSecret`. If both are set, `getSecret` is used.

### Options for server-side generation

These options only apply when `getSecret` is used:

| Property | Default | Description |
|----------|---------|-------------|
| `digits` | `6` | Number of digits in a code |
| `totpDuration` | `30.seconds` | Length of a time window |
| `hmacAlgorithm` | `SHA1` | `SHA1`, `SHA256` or `SHA512` (`TotpPluginConfiguration.TotpHmacAlgorithm`) |
| `clock` | `Clock.System` | Time source (`kotlin.time.Clock`). Useful for tests |

## Behaviour

- Requires `session.identifiedUser`.
- If the code is correct, the step is marked completed and `session.nextStep()` is called.
- If not, the step stays active. There is no built-in attempt limit.

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
{ "validated": false }
```

**Request:** `POST /flow/{sessionId}/steps/plugins/authentikt-builtin/totp`

```json
{ "totp_code": "286133" }
```

**Response**

```json
{ "success": true }
```

## Frontend

Use [`TotpRenderer`](frontend-renderers.md#totp). Its plugin instance exposes `totp`, `status`
(`"ready" | "loading" | "totp_incorrect" | "error"`) and `submit()`.
