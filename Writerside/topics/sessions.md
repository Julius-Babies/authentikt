# Sessions

A `Session<USER>` represents one login attempt. It is created by your code, advanced by step plugins and read by
your step-order callback.

## Creating a session

```kotlin
val session = authentikt.createNewSession()
call.respond(mapOf("session_id" to session.sessionId))
```

`createNewSession` takes an optional [`SessionDestination`](#destinations). Without one, the session is a regular
login for your own application.

The session ID is generated from three random UUIDs (96 hex characters). Treat it like a secret: whoever knows it
can continue the flow.

## Properties

| Member | Type | Description |
|--------|------|-------------|
| `sessionId` | `String` | Unique ID used in all flow URLs |
| `createdAt` | `Instant` | When the session was created |
| `lastActivityAt` | `Instant` | When the session was last accessed through a flow route |
| `expiresAt` | `Instant` | When the session expires, see [](#storage-and-lifetime) |
| `destination` | `SessionDestination?` | Where the login result goes. `null` for regular logins |
| `identifiedUser` | `AuthentiktUser<USER>?` | Set by an identification step. `null` until then |
| `authenticationSteps` | `MutableList<Pair<BasePlugin, BaseState>>` | Every step that has been entered, in order. The last entry is the active step |
| `attributes` | `SessionAttributeScope` | Private key/value storage, never sent to the client |
| `publicAttributes` | `PublicSessionAttributeScope` | Key/value storage that is included in every `check` response |

## Functions

`has(plugin, needsCompletion = true)`
: Returns `true` if the session has entered `plugin`. By default, the step must also be completed. Pass
`needsCompletion = false` to only check whether the step was entered.

`nextStep()`
: Calls the step-order callback, creates the returned plugin's initial state and appends it to
`authenticationSteps`. Throws `NotInstalledPluginCalled` if the plugin was not passed to `install(...)`.
Built-in plugins call this after a successful submission. Custom plugins should do the same.

`pop()`
: Removes the last step. If no steps are left, it clears `identifiedUser` instead. Use it to implement "go back"
behaviour in custom routes.

`getPublicAttributes()`
: Returns the public attributes as a `Map<String, Any?>`, keyed by attribute name.

`isExpired()`
: Returns `true` once `expiresAt` has passed.

`invalidate()`
: Removes the session from the store. Further requests for it are answered with `404`. Use it to end a flow early,
for example after too many failed attempts.

## Attributes

Attributes let you attach data to a session without subclassing anything. Keys are Ktor `AttributeKey`s, so the
values are typed:

```kotlin
val LoginAttemptsKey = AttributeKey<Int>("login_attempts")
val AuthIdKey = AttributeKey<Int>("auth_id")

get("/api/login") {
    val session = authentikt.createNewSession()

    // Only visible on the server
    session.attributes[LoginAttemptsKey] = 0

    // Sent to the client as "attributes": { "auth_id": 123456 }
    session.publicAttributes[AuthIdKey] = Random.nextInt(100000, 999999)

    call.respond(mapOf("session_id" to session.sessionId))
}
```

On the client, public attributes are available as `auth.currentFlow.attributes`:

```svelte
{#if auth.currentFlow?.attributes?.auth_id}
    <p>Login request #{auth.currentFlow.attributes.auth_id}</p>
{/if}
```

Public attributes are serialized with Gson. Stick to primitives, strings, lists and maps.

## Destinations {id="destinations"}

A destination describes who receives the result of the login. It has an `applicationId` and an
`applicationName`:

`SessionDestination.DeviceFlow(deviceCode, userCode, applicationId, applicationName)`
: Created by `POST /oauth/device/code`. A device is waiting for the user to finish the login. See
[](oauth-device-flow.md).

The `check` response contains the destination, so the frontend can show "Signing in to *TV App*". In your step-order
callback and in `DonePlugin.onSuccess`, you can branch on `session.destination` as well.

## Accessing the session in a route

Inside routes installed by a step plugin, the current session is stored in the call attributes:

```kotlin
post {
    val session = call.attributes[SessionKey] as Session<USER>
    // ...
}
```

## Storage and lifetime {id="storage-and-lifetime"}

Sessions are kept in an in-memory map inside the JVM. They are not persisted and not shared between server
instances. See [](known-limitations.md) for what this means in practice.

A session ends in one of these ways:

- **Inactivity.** Every request to a flow route (`/flow/{sessionId}/...`) counts as activity. A session that sees
  no activity for [`sessionTimeout`](backend-configuration.md) (default: 30 minutes) expires.
- **Device flow lifetime.** Sessions with a `DeviceFlow` destination expire
  [`deviceCodeLifetime`](oauth-device-flow.md#configuration) after creation (default: 10 minutes), regardless of
  activity. This is the `expires_in` value the device receives.
- **Completion.** The session is removed as soon as the `DonePlugin` has completed. For device flows, this happens
  when the device redeems its code at `POST /oauth/token`.
- **Manually**, by calling `invalidate()`.

Expired sessions are removed when they are accessed and, in addition, by a background job that runs every
[`sessionCleanupInterval`](backend-configuration.md) (default: 1 minute). Requests for unknown or expired sessions
are answered with `404`:

```json
{
  "error": "session_not_found",
  "error_description": "The session does not exist or has expired."
}
```

The Svelte client cancels the flow when it receives this response.
