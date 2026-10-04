# Password

`PasswordPlugin` verifies a password for the identified user through your callback.

**Namespace:** `authentikt-builtin/password`

## Configuration

```kotlin
val passwordPlugin = PasswordPlugin<User> {
    checkPassword { user, password ->
        passwordHasher.verify(password, user.passwordHash)
    }
}
```

`checkPassword { user: USER, password: String -> Boolean }` (required)
: Returns `true` if the password is correct. `user` is your own user object, not the `AuthentiktUser` wrapper.

> authentikt never stores or hashes passwords. Use a proper password hashing algorithm such as Argon2id, bcrypt or
> scrypt in your `checkPassword` implementation, and compare hashes, not plain text.
{style="warning"}

## Behaviour

- Requires `session.identifiedUser`. Only return this plugin from the step-order callback after an identification
  step has run.
- If the password is correct, the step is marked completed and `session.nextStep()` is called.
- If not, the step stays active and the user can try again. There is no built-in attempt limit. See
  [](known-limitations.md).
- The response is sent with `call.respond`, so Ktor's `ContentNegotiation` with JSON must be installed.

## HTTP contract

**Payload**

```json
{ "validated": false }
```

**Request:** `POST /flow/{sessionId}/steps/plugins/authentikt-builtin/password`

```json
{ "password": "correct horse battery staple" }
```

**Response**

```json
{ "success": true }
```

## Frontend

Use [`PasswordRenderer`](frontend-renderers.md#password). Its plugin instance exposes `password`, `status`
(`"ready" | "loading" | "password_incorrect" | "error"`) and `submit()`.
