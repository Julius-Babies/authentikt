# Email user selection

`EmailUserSelectionPlugin` asks the user for an email address and looks them up through your callback. It is the
usual first step of a password-based flow.

**Namespace:** `authentikt-builtin/email`

## Configuration

```kotlin
val emailPlugin = EmailUserSelectionPlugin<User> {
    findUserByEmail { email ->
        userRepository.findByEmail(email)?.toAuthentiktUser()
    }
}
```

`findUserByEmail { email -> AuthentiktUser<USER>? }` (required)
: Looks up a user. Return `null` if nobody matches. The function is `suspend`, so you can call your database
directly. The value is passed through as entered, so normalize it (trim, lowercase) here if needed.

`withUsername` (default: `false`)
: A hint for the frontend that the input field also accepts a username. It is forwarded to the client as
`with_username` in the payload. The lookup itself is entirely up to `findUserByEmail`. To actually accept
usernames, match both fields there:

```kotlin
EmailUserSelectionPlugin<User> {
    withUsername = true
    findUserByEmail { input ->
        users.find { it.email == input || it.username == input }?.toAuthentiktUser()
    }
}
```

## Behaviour

When the user is found, the plugin:

1. sets `session.identifiedUser`,
2. marks its step as completed, and
3. calls `session.nextStep()`.

When no user is found, the step stays active and the client can try again.

> The response tells the client whether an account exists for the given address. If user enumeration matters for
> your application, consider a custom identification plugin that always continues with the next step.
{style="note"}

## HTTP contract

**Payload** (in the `check` response)

```json
{ "with_username": false }
```

**Request:** `POST /flow/{sessionId}/steps/plugins/authentikt-builtin/email`

```json
{ "email": "eric.smith@acme.com" }
```

**Responses**

```json
{ "type": "success", "username": "eric.smith", "display_name": "Eric Smith" }
```

```json
{ "type": "user_not_found" }
```

## Frontend

Use [`EmailUserSelectionRenderer`](frontend-renderers.md#email). Its plugin instance exposes `email`, `status`
(`"ready" | "loading" | "user_not_existing" | "error"`), `typedPayload.with_username` and `submit()`. After a
successful submission, it also calls `auth.setUser(...)` with the returned username and display name.
