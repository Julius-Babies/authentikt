# Password

`PasswordPlugin` verifies a password for the identified user through your callback.

**Namespace:** `authentikt-builtin/password`

## Configuration

```kotlin
val passwordPlugin = PasswordPlugin<User> {
    rateLimit = 3 triesPer 3.minutes
    checkPassword { user, password ->
        passwordHasher.verify(password, user.passwordHash)
    }
}
```

`checkPassword { user: USER, password: String -> Boolean }` (required)
: Returns `true` if the password is correct. `user` is your own user object, not the `AuthentiktUser` wrapper.

`rateLimit` (default: `5 triesPer 5.minutes`)
: Failed attempts allowed per user. `null` disables the limit. See [](rate-limiting.md).

> authentikt never stores or hashes passwords. Use a proper password hashing algorithm such as Argon2id, bcrypt or
> scrypt in your `checkPassword` implementation, and compare hashes, not plain text.
{style="warning"}

## Behaviour

- Requires `session.identifiedUser`. Only return this plugin from the step-order callback after an identification
  step has run.
- If the password is correct, the step is marked completed and `session.nextStep()` is called.
- If not, the step stays active and the user can try again until the [rate limit](rate-limiting.md) is reached.
  While the step is locked, submissions are answered with `429` without calling `checkPassword`.

## HTTP contract

**Payload**

```json
{
  "validated": false,
  "rate_limit": { "max_tries": 3, "period_seconds": 180, "remaining_tries": 2 }
}
```

`rate_limit` is omitted if the limit is disabled. See [](rate-limiting.md#client-state).

**Request:** `POST /flow/{sessionId}/steps/plugins/authentikt-builtin/password`

```json
{ "password": "correct horse battery staple" }
```

**Responses**

```json
{ "success": true }
```

```json
{ "success": false, "rate_limit": { "max_tries": 3, "period_seconds": 180, "remaining_tries": 1 } }
```

While locked: `429 Too Many Requests` with `{ "success": false, "error": "rate_limited", "rate_limit": { ... } }`.

## Frontend

Use [`PasswordRenderer`](frontend-renderers.md#password). Its plugin instance exposes `password`, `status`
(`"ready" | "loading" | "password_incorrect" | "rate_limited" | "error"`), `rateLimit` and `submit()`.
