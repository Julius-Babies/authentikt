# Quick start

This guide builds a complete login: the user enters their email, then their password, then a TOTP code if they
have one configured. On success, the server sets a session cookie.

It assumes that the frontend and the backend are served from the same origin, for example `https://example.com`
for the SvelteKit app and `https://example.com/api` for Ktor (behind a reverse proxy).

## Backend

<procedure title="Set up the Ktor side" id="backend-procedure">
<step>

**Wrap your user type.** authentikt is generic over your own user class. To read basic profile data, it needs a
small adapter that extends [`AuthentiktUser`](user-model.md):

```kotlin
data class User(
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val totpSecret: String?,
)

fun User.toAuthentiktUser() = object : AuthentiktUser<User>(this) {
    override suspend fun getEmail(): String? = email
    override suspend fun getUsername(): String? = null
    override suspend fun getDisplayName(): String? = displayName
}
```

</step>
<step>

**Create the plugins.** Every plugin is configured through a small DSL. Keep references to them, because you will
need them in the step-order callback.

```kotlin
val emailPlugin = EmailUserSelectionPlugin<User> {
    findUserByEmail { email -> userRepository.findByEmail(email)?.toAuthentiktUser() }
}

val passwordPlugin = PasswordPlugin<User> {
    checkPassword { user, password -> passwordHasher.verify(password, user.passwordHash) }
}

val totpPlugin = TotpPlugin<User> {
    validate { user, code -> totpService.verify(user.totpSecret!!, code) }
}

val donePlugin = DonePlugin<User> {
    onSuccess { session, user ->
        cookie(
            Cookie(
                name = "auth_token",
                value = tokenService.issueFor(user),
                maxAge = 7.days.inWholeSeconds.toInt(),
                path = "/",
                secure = true,
                httpOnly = true,
            )
        )
    }
}
```

</step>
<step>

**Install authentikt.** Register the plugins and define the step order:

```kotlin
val authentikt = installAuthentikt<User> {
    baseUrl = "https://example.com"
    uiLoginBaseUrl = "https://example.com/"
    apiPrefix = "/api"

    install(emailPlugin)
    install(passwordPlugin)
    install(totpPlugin)
    install(donePlugin)

    authorization { session ->
        val user = session.identifiedUser
        when {
            user == null -> emailPlugin
            !session.has(passwordPlugin) -> passwordPlugin
            user.user.totpSecret != null && !session.has(totpPlugin) -> totpPlugin
            else -> donePlugin
        }
    }
}
```

The flow endpoints are now mounted under `/api/authentikt/...`.

</step>
<step>

**Expose a login endpoint.** authentikt does not create sessions on its own. Your application decides who may
start a flow. The Svelte client expects `GET /api/login` to return a session ID:

```kotlin
routing {
    get("/api/login") {
        val session = authentikt.createNewSession()
        call.respond(mapOf("session_id" to session.sessionId))
    }
}
```

</step>
<step>

**Protect your routes.** Use whatever Ktor authentication provider fits the token you issued in `onSuccess`, for
example [`jwt`](https://ktor.io/docs/server-jwt.html) or [`session`](https://ktor.io/docs/server-session-auth.html).
authentikt is only responsible for the login itself.

</step>
</procedure>

## Frontend

<procedure title="Set up the SvelteKit side" id="frontend-procedure">
<step>

**Add the provider.** Wrap the part of your app that hosts the login in `<Authentikt>`. The `baseUrl` points at the
authentikt routes, so it is your API prefix plus `/authentikt/`:

```svelte
<!-- src/routes/+layout.svelte -->
<script lang="ts">
    import { Authentikt } from "@julius-babies/authentikt-svelte";
    import LoginFlow from "$lib/LoginFlow.svelte";

    let { children } = $props();
</script>

<Authentikt config={{ baseUrl: "https://example.com/api/authentikt/" }}>
    <LoginFlow />
    {@render children()}
</Authentikt>
```

</step>
<step>

**Render the steps.** Place one renderer per plugin you installed on the server. Each renderer only shows up
while its step is active, so you can list all of them next to each other:

```svelte
<!-- src/lib/LoginFlow.svelte -->
<script lang="ts">
    import {
        useAuthentiktContext,
        EmailUserSelectionRenderer,
        PasswordRenderer,
        TotpRenderer,
        DoneRenderer,
    } from "@julius-babies/authentikt-svelte";

    const auth = useAuthentiktContext();
</script>

{#if !auth.currentFlow}
    <button onclick={auth.startLoginFlow}>Log in</button>
{:else}
    <button onclick={auth.cancelFlow}>Cancel</button>

    <EmailUserSelectionRenderer />
    <PasswordRenderer />
    <TotpRenderer />
    <DoneRenderer />
{/if}
```

</step>
<step>

**Try it.** Click **Log in**, enter an email address, then the password. When the `DoneRenderer` becomes active,
it calls the server, the cookie is set, and the page reloads after two seconds.

</step>
</procedure>

## What's next

- Customize the UI with snippets: [](frontend-renderers.md)
- Learn what else the `Authentikt` client can do: [](frontend-client.md)
- Add single sign-on through Keycloak, Authentik or any other OIDC provider: [](oidc-plugin.md)
- Let devices without a keyboard sign in: [](oauth-device-flow.md)
