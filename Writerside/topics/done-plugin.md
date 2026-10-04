# Done

`DonePlugin` is the final step of every flow. When the client calls it, it runs your success handler, which is
where you issue whatever proves that the user is logged in.

**Namespace:** `authentikt-builtin/done`

## Configuration

```kotlin
val donePlugin = DonePlugin<User> {
    onSuccess { session, user ->
        cookie(
            Cookie(
                name = "auth_token",
                value = tokenService.issueFor(user),
                maxAge = 30.days.inWholeSeconds.toInt(),
                path = "/",
                secure = true,
                httpOnly = true,
            )
        )
        redirect("https://example.com/dashboard")
    }

    onOAuthSuccess { session, user ->
        OAuthAccessToken(
            accessToken = tokenService.issueFor(user),
            refreshToken = null,
            expiresIn = 7.days,
        )
    }
}
```

`onSuccess { session, user -> }` (required)
: Called once when a regular login completes. `user` is your own user object. Inside the block, you have access to
a [`DonePluginScope`](#scope).

`onOAuthSuccess { session, user -> OAuthAccessToken }`
: Called when a device that is waiting in the [device flow](oauth-device-flow.md) exchanges its device code. It
returns the token the device receives. This callback is required as soon as `oauth { }` is configured.

### DonePluginScope {id="scope"}

| Function | Effect |
|----------|--------|
| `cookie(cookie: Cookie)` | Adds a `Set-Cookie` header (`io.ktor.http.Cookie`) to the response. Can be called multiple times |
| `redirect(to: String)` | Tells the client to navigate to `to` after completion |

You can also do anything else in `onSuccess`, such as writing an audit log entry or updating "last login"
timestamps.

## Behaviour

When the client calls the step:

1. **Device flow sessions** (`session.destination is SessionDestination.DeviceFlow`): `onSuccess` is not called.
   The response is `device_flow_success`, and the browser can tell the user to return to their device. The device
   gets its token from `/oauth/token`.
2. **First call of a regular session**: `onSuccess` runs, cookies are attached, and the step is marked completed.
3. **Later calls**: return `{ "type": "success" }` without running `onSuccess` again.

> Cookies are set on the response to the browser's `fetch` call. Browsers only store them if the frontend and the
> API share an origin, or if your CORS and cookie settings explicitly allow cross-site credentials. See
> [](frontend-setup.md#same-origin).
{style="note"}

## HTTP contract

**Payload:** empty object.

**Request:** `GET /flow/{sessionId}/steps/plugins/authentikt-builtin/done`

**Responses**

```json
{ "type": "success", "cookies": ["auth_token"] }
```

```json
{ "type": "redirect", "to": "https://example.com/dashboard", "cookies": ["auth_token"] }
```

```json
{ "type": "device_flow_success" }
```

`cookies` lists the names of the cookies that were set and is omitted when there are none.

## Frontend

Use [`DoneRenderer`](frontend-renderers.md#done). It calls the endpoint automatically as soon as the step becomes
active. Then it:

- reloads the page after 2 seconds on `success`,
- navigates to `to` after 1 second on `redirect`, or
- ends the flow immediately on `device_flow_success`.
