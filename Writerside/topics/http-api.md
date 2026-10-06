# HTTP API

This page describes the wire protocol between a client and authentikt-core. You only need it if you build a client
without authentikt-svelte, for example a native mobile app, or if you debug network traffic.

All JSON keys use `snake_case`. Paths are relative to `{apiPrefix}/authentikt` unless they start with `/oauth`.

## Starting a flow

authentikt doesn't expose a "create session" route, because only your application knows who may start a flow.
Expose one yourself:

```kotlin
get("/api/login") {
    val session = authentikt.createNewSession()
    call.respond(mapOf("session_id" to session.sessionId))
}
```

authentikt-svelte expects exactly this route (`GET /api/login` → `{ "session_id": "..." }`) on the origin of its
`baseUrl`.

## GET /flow/{sessionId}/check

Returns the current step. On the first call of a session, this selects the first step through the step-order
callback.

```json
{
  "type": "step",
  "namespace": "authentikt-builtin/password",
  "payload": { "validated": false },
  "alternatives": ["authentikt-builtin/oidc"],
  "attributes": { "auth_id": 482913 },
  "user": { "username": "eric", "display_name": "Eric Smith" },
  "destination": { "type": "none" }
}
```

| Field | Description |
|-------|-------------|
| `type` | Always `"step"` |
| `namespace` | Namespace of the active step plugin |
| `payload` | The step state's `createClientState()` |
| `alternatives` | Namespaces of the steps the user can switch to instead of the active step. See [](step-order.md#alternatives) |
| `attributes` | The session's public attributes |
| `user` | The identified user as `{ "username", "display_name" }`. Omitted until a user-selection step has identified a user |
| `destination` | `{ "type": "none" }` or `{ "type": "device_flow", "application_id", "application_name" }` |

The active step is always the last entered one. After the `DonePlugin` has run, the session is removed and `check`
answers with `404`. For device flows, this happens once the device has redeemed its code.

## POST /flow/{sessionId}/alternatives

Replaces the active step with one of its `alternatives`. The alternative starts with a fresh state, and the replaced
step becomes an alternative itself.

```json
{ "namespace": "authentikt-builtin/oidc" }
```

The response is `{ "type": "success" }`. Call `check` afterwards to get the new step. If the namespace is not an
alternative of the active step, for example because of a duplicate click, the server answers with `409 Conflict`:

```json
{
  "error": "alternative_not_available",
  "error_description": "The step is not an alternative of the active step."
}
```

## Step routes

Each plugin's routes are mounted under `/flow/{sessionId}/steps/plugins/{namespace}`. Built-in plugins:

| Namespace | Method | Request body | Response |
|-----------|--------|--------------|----------|
| `authentikt-builtin/email` | `POST` | `{ "email": string }` | `{ "type": "success", "username", "display_name" }` or `{ "type": "user_not_found", "rate_limit"? }` |
| `authentikt-builtin/password` | `POST` | `{ "password": string }` | `{ "success": boolean, "rate_limit"? }` |
| `authentikt-builtin/totp` | `POST` | `{ "totp_code": string }` | `{ "success": boolean, "rate_limit"? }` |
| `authentikt-builtin/oidc` | none | | Redirect to `payload.authorize_url` instead |
| `authentikt-builtin/junction` | `POST` | `{ "namespace": string }` | `{ "type": "success" }`, or `400` if the namespace is not an option |
| `authentikt-builtin/done` | `GET` | | `{ "type": "success" \| "redirect" \| "device_flow_success", "to"?, "cookies"? }` (OAuth sessions: `redirect` to the client with the authorization code) |

After a successful step submission, call `check` again to get the next step.

Request bodies are JSON (`Content-Type: application/json`).

## Static routes

Session-independent plugin routes are mounted under `/static/plugins/{namespace}`. The only built-in one is the
OIDC callback:

```
GET /static/plugins/authentikt-builtin/oidc/{applicationName}/callback?code=...&state=...
```

## OAuth routes

Only present when `oauth { }` is configured. They are mounted at the server root:

| Route | Present when | Description |
|-------|--------------|-------------|
| `GET /oauth/authorize` | `onAuthorize` is set | Starts an authorization code flow and redirects to `uiLoginBaseUrl` |
| `POST /oauth/device/code` | `onDeviceFlow` is set | Starts a device flow |
| `POST /oauth/token` | always | Exchanges an authorization code or a device code for a token |

Errors of the OAuth routes use the format of [RFC 6749, section 5.2](https://datatracker.ietf.org/doc/html/rfc6749#section-5.2):
`{ "error": "...", "error_description": "..." }`.

Details and responses: [](oauth-device-flow.md).

## URL parameters for resuming

Whenever the server sends the browser to `uiLoginBaseUrl` (after OIDC, after `/oauth/authorize` and in device-flow
verification URIs), it appends:

```
?_authentikt_flow_active=true&_authentikt_session_id=<session id>
```

A client should pick up these parameters on load and continue the flow with `check`.

## Errors

The flow routes do not use a common error format yet. Requests for an unknown or
[expired session](sessions.md#storage-and-lifetime) are answered with `404`:

```json
{
  "error": "session_not_found",
  "error_description": "The session does not exist or has expired."
}
```

Requests to a step that is not the session's active step, for example a duplicate submission, are answered with
`409 Conflict`. The client should reload the flow state with `check`:

```json
{
  "error": "step_not_active",
  "error_description": "This step is not the active step of the session."
}
```

Validation failures (wrong password, unknown user) are returned with status `200` and a negative body, as listed
above.

Submissions to a [rate-limited](rate-limiting.md) step that is locked are answered with `429 Too Many Requests` and
a `Retry-After` header. The body contains `"error": "rate_limited"` and the step's `rate_limit` state.
