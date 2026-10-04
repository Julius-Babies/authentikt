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
| `attributes` | The session's public attributes |
| `user` | The identified user as `{ "username", "display_name" }`. Omitted until a user-selection step has identified a user |
| `destination` | `{ "type": "none" }` or `{ "type": "device_flow", "application_id", "application_name" }` |

The active step is always the last entered one. After the `DonePlugin` has run, `check` keeps returning it.

## Step routes

Each plugin's routes are mounted under `/flow/{sessionId}/steps/plugins/{namespace}`. Built-in plugins:

| Namespace | Method | Request body | Response |
|-----------|--------|--------------|----------|
| `authentikt-builtin/email` | `POST` | `{ "email": string }` | `{ "type": "success", "username", "display_name" }` or `{ "type": "user_not_found" }` |
| `authentikt-builtin/password` | `POST` | `{ "password": string }` | `{ "success": boolean }` |
| `authentikt-builtin/totp` | `POST` | `{ "totp_code": string }` | `{ "success": boolean }` |
| `authentikt-builtin/oidc` | none | | Redirect to `payload.authorize_url` instead |
| `authentikt-builtin/done` | `GET` | | `{ "type": "success" \| "redirect" \| "device_flow_success", "to"?, "cookies"? }` |

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
| `POST /oauth/device/code` | `onDeviceFlow` is set | Starts a device flow |
| `POST /oauth/token` | always | Exchanges a device code for a token |

Details and responses: [](oauth-device-flow.md).

## URL parameters for resuming

Whenever the server sends the browser to `uiLoginBaseUrl` (after OIDC and in device-flow verification URIs), it appends:

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

Validation failures (wrong password, unknown user) are returned with status `200` and a negative body, as listed
above.
