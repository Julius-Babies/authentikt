# Known limitations and troubleshooting

authentikt is young. This page lists what it does not do yet, so you can decide what to build around it, and
collects common setup problems.

## Limitations

### Sessions

- **In memory only.** Sessions live in a map inside the JVM. A restart ends all running logins, and sessions are
  not shared between instances. If you run several server instances, use sticky sessions.

### Security

- **No attempt limits.** Password, TOTP and email steps can be retried indefinitely. Add rate limiting, for example
  with Ktor's [RateLimit](https://ktor.io/docs/server-rate-limit.html) plugin or inside your `checkPassword` and
  `validate` callbacks.
- **No ordering checks in built-in routes.** The built-in step routes don't verify that they are the active step.
  The step-order callback still decides what happens next, but write your own plugins defensively. See
  [](custom-step-plugins.md).
- **User enumeration.** The email step tells the client whether an account exists.

### Protocol

- **Error responses** are not uniform. An unknown session ID causes a `500` response.

### Frontend

- **SvelteKit only**, because the client uses `$app/navigation` and `$app/state`.
- **Fixed login route.** `startLoginFlow()` always calls `/api/login` on the origin of `baseUrl`. Use
  `linkToFlow()` for anything else.
- **Same origin for cookies.** Requests are sent without `credentials: "include"`.

## Troubleshooting

### Startup fails with `... must be set` or `... must be configured`

A required option is missing. The message names it. See the validation table in [](backend-configuration.md) and
the configuration section of the affected plugin.

### `NotInstalledPluginCalled: Plugin ... has been selected ... but was not installed`

Your step-order callback returned a plugin that was not passed to `install(...)`. Every plugin the callback can
return must be installed.

### POST requests to steps fail with `415 Unsupported Media Type` or a serialization error

Ktor's `ContentNegotiation` with kotlinx.serialization JSON is not installed, or the client did not send
`Content-Type: application/json`. See [](installation.md#ktor-plugins).

### The login succeeds, but the user is not logged in afterwards

The cookie set by the `DonePlugin` was dropped by the browser. Check that:

- frontend and API are on the same origin (see [](frontend-setup.md#same-origin)),
- `secure = true` cookies are only used over HTTPS, and
- the cookie `path` covers the routes that read it (usually `"/"`).

### The OIDC provider reports an invalid redirect URI

Compare the URL logged at startup (`OIDC Callback Route for application ... installed at ...`) with the redirect
URIs registered at the provider. Both `baseUrl` and `apiPrefix` are part of it.

### OIDC callback fails with `PKIX path building failed`

The provider uses a certificate your JVM does not trust. Add the CA certificate with
`customSslCert("/path/to/ca.crt")`.

### Nothing is rendered while a flow is running

No renderer for the current namespace is mounted. Turn on the [debug overlay](frontend-debugging.md) to see the
active namespace.
