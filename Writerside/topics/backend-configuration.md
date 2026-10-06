# Configuration

`installAuthentikt` is the entry point of the server library. Call it from your `Application` module and keep the
returned instance. That instance is how you interact with authentikt later, for example to create sessions.

```kotlin
val authentikt: AuthentiktInstance<User> = installAuthentikt<User> {
    baseUrl = "https://myapplication.com"   // Where your application lives
    apiPrefix = "/api"                      // The sub-path your Ktor routes are served under
    uiLoginBaseUrl = "https://myapplication.com/login" // The page that hosts the Svelte login UI
    sessionTimeout = 30.minutes             // Optional: how long an idle login may stay open

    install(emailPlugin)
    install(passwordPlugin)
    install(donePlugin)

    authorization { session -> /* next step */ }
}
```

`installAuthentikt` validates the configuration immediately and throws an `IllegalArgumentException` at startup
if something required is missing.

## Properties

`baseUrl` (required)
: The public base URL of your server, **without** the API prefix, for example `https://example.com`. It is used to
build absolute URLs that external systems call back into, such as the OIDC redirect URI.

`apiPrefix` (default: `""`)
: The path prefix of all authentikt routes. With `apiPrefix = "/api"`, the flow routes are served under
`/api/authentikt/...`. Use the same prefix that your frontend's `baseUrl` points to.

`uiLoginBaseUrl` (required)
: The URL of the page where `<Authentikt>` is mounted. authentikt redirects users there, with the session ID in the
query string, after an OIDC login and for device flows. During development, this is often your Vite dev
server, for example `http://localhost:5173/`.

`sessionTimeout` (default: `30.minutes`)
: How long a [session](sessions.md#storage-and-lifetime) may be inactive before it expires. Every request to a
flow route resets the timer. Device flow sessions use
[`deviceCodeLifetime`](oauth-device-flow.md#configuration) instead.

`sessionCleanupInterval` (default: `1.minutes`)
: How often a background job removes expired sessions from memory. Expired sessions are also rejected and removed
when they are accessed, so this only affects memory usage.

`clock` (default: `Clock.System`)
: The `kotlin.time.Clock` used to determine whether a session has expired. Replace it in tests to control time.

## Functions

`install(plugin)`
: Registers a step plugin. Every plugin your step-order callback can return must be installed. Its routes are
mounted under `/flow/{sessionId}/steps/plugins/{namespace}` and `/static/plugins/{namespace}`.

`authorization { session -> ... }` (required)
: Sets the callback that picks the next step. See [](step-order.md).

`customSslCert(path)`
: Adds an X.509 certificate file (PEM or DER) that outgoing HTTPS requests trust, for example when your identity
provider uses a certificate from a local development CA. Currently only used by the [OIDC plugin](oidc-plugin.md).
The file must exist at startup.

`oauth { ... }`
: Turns authentikt into a minimal OAuth provider (authorization code and device flow). See [](oauth-device-flow.md).

## AuthentiktInstance

| Member | Description |
|--------|-------------|
| `configuration` | The resolved `AuthentiktConfiguration<USER>`: base URLs, prefix, installed plugins, OAuth settings |
| `createNewSession(destination = null)` | Creates and registers a new [session](sessions.md) |

## Type parameter

All authentikt types are generic over `USER`, your application's user class. Kotlin can often infer it, but naming
it explicitly gives clearer error messages:

```kotlin
val passwordPlugin = PasswordPlugin<User> { ... }
val authentikt = installAuthentikt<User> { ... }
```

## Multiple instances

In theory, you can call `installAuthentikt` more than once in the same application, for example with different
API prefixes. We cannot think of a use case where this makes sense, and it is not tested. Note that all instances
share the same in-memory session store. If you try it, use distinct `apiPrefix` values.

## Validation errors

| Message | Cause |
|---------|-------|
| `findNextStepCallback must be configured via authorization { ... }` | `authorization { }` was not called |
| `baseUrl must be set` | `baseUrl` is empty |
| `uiLoginBaseUrl must be set` | `uiLoginBaseUrl` is empty |
| `customSslCerts must exist` | A path passed to `customSslCert` does not exist |
| `sessionTimeout must be positive` | `sessionTimeout` is zero or negative |
| `sessionCleanupInterval must be positive` | `sessionCleanupInterval` is zero or negative |
| `deviceCodeLifetime must be positive` | `deviceCodeLifetime` in `oauth { }` is zero or negative |
| `authorizationCodeLifetime must be positive` | `authorizationCodeLifetime` in `oauth { }` is zero or negative |
| `DonePlugin is required for OAuth flow` | `oauth { }` is configured but no `DonePlugin` is installed |
| `onOAuthSuccess callback in DonePlugin is required for OAuth flow` | `oauth { }` is configured but the `DonePlugin` has no `onOAuthSuccess` |
