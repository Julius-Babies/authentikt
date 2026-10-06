# Known limitations and troubleshooting

authentikt is young. This page lists what it does not do yet, so you can decide what to build around it, and
collects common setup problems.

## Limitations

### Sessions

- **In memory only.** Sessions live in a map inside the JVM. A restart ends all running logins, and sessions are
  not shared between instances. If you run several server instances, use sticky sessions.

### Security

- **Rate limits are in memory.** Failed attempts are not shared between instances and are lost on restart. The
  email step counts attempts per session, so its limit can be bypassed by starting new sessions. Password and TOTP
  limits are per user, so they can be used to lock a user out for the length of the period. See
  [](rate-limiting.md).
- **User enumeration.** The email step tells the client whether an account exists.
- **OIDC ID token signatures are not verified.** The ID token is fetched directly from the token endpoint over TLS,
  so only its claims (`nonce`, `aud`, `azp`, `exp` and optionally `iss`) are checked. Login attempts are not bound
  to a browser cookie, so like the rest of the flow they rely on the secrecy of the session ID. See
  [](oidc-plugin.md#security).

### Protocol

- **Error responses** are not uniform. Only unknown or expired sessions have a dedicated format (`404`, see
  [](http-api.md#errors)).

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
