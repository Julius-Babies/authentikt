# Frontend setup

authentikt-svelte is a headless Svelte 5 client for authentikt flows. It keeps track of the current flow, talks to
the server and gives you reactive plugin objects, so you can render the login however you like.

> The library imports `$app/navigation` and `$app/state` to keep the URL in sync with the flow. It therefore
> requires **SvelteKit**. Plain Svelte + Vite projects are not supported.
{style="note"}

## The provider

Everything starts with the `<Authentikt>` component. It creates an [`Authentikt` client](frontend-client.md) and
provides it to its children through Svelte context:

```svelte
<script lang="ts">
    import { Authentikt, type AuthentiktConfiguration } from "@julius-babies/authentikt-svelte";

    let { children } = $props();

    const config: AuthentiktConfiguration = {
        baseUrl: "https://example.com/api/authentikt/",
        debug: false,
    };
</script>

<Authentikt {config}>
    {@render children()}
</Authentikt>
```

Mount it on the page you configured as `uiLoginBaseUrl` on the server, typically in your root `+layout.svelte`.
That page has to be able to resume a flow from the URL after OIDC or device-flow redirects.

### Configuration

`baseUrl` (required)
: Absolute URL of the authentikt routes: your server origin, plus the `apiPrefix`, plus `/authentikt/`. If it does
not end in `authentikt/`, the provider appends it (and warns about it in debug mode).

`debug` (default: `false`)
: `true` or `{ show_overlay: true }` shows a draggable overlay with the live flow state. See
[](frontend-debugging.md).

### Accessing the client

Anywhere below `<Authentikt>`, call `useAuthentiktContext()`:

```svelte
<script lang="ts">
    import { useAuthentiktContext } from "@julius-babies/authentikt-svelte";

    const auth = useAuthentiktContext();
</script>

<button onclick={auth.startLoginFlow}>Log in</button>
```

You can also use it inline, directly inside the provider, with `{@const}`:

```svelte
<Authentikt {config}>
    {@const auth = useAuthentiktContext()}
    {#if auth.currentFlow}
        <!-- flow UI -->
    {/if}
</Authentikt>
```

## Backend expectations

The client relies on these server-side conventions:

| What | Expected by |
|------|-------------|
| `GET /api/login` on the same origin as `baseUrl`, returning `{ "session_id": "..." }` | `auth.startLoginFlow()` |
| authentikt routes under `baseUrl` | all plugins |

> `startLoginFlow()` always requests `/api/login` relative to the **origin** of `baseUrl`, regardless of your API
> prefix. If your login endpoint lives elsewhere, create the session yourself and call
> [`auth.linkToFlow(sessionId)`](frontend-client.md#actions) instead.
{style="note"}

## Same origin {id="same-origin"}

The client uses plain `fetch` without `credentials: "include"`. Cookies that the [DonePlugin](done-plugin.md) sets
are therefore only stored if the SvelteKit app and the Ktor API are served from the **same origin**, for example
through a reverse proxy:

```
https://example.com/api/*    → Ktor (port 8080)
https://example.com/oauth/*  → Ktor (port 8080)
https://example.com/*        → SvelteKit (port 5173)
```

During development, a Vite proxy or a local reverse proxy gives you the same setup.

## Building the login UI

The typical layout shows a login button while no flow is running, and one renderer per installed plugin while a
flow is active:

```svelte
<script lang="ts">
    import {
        useAuthentiktContext,
        EmailUserSelectionRenderer,
        PasswordRenderer,
        TotpRenderer,
        OIDCRenderer,
        DoneRenderer,
    } from "@julius-babies/authentikt-svelte";

    const auth = useAuthentiktContext();
</script>

{#if !auth.currentFlow}
    <button onclick={auth.startLoginFlow}>Log in</button>
{:else}
    <div class="modal">
        <button onclick={auth.cancelFlow} aria-label="Close">×</button>

        {#if auth.currentFlow.destination.type !== "none"}
            <p>Signing in to <strong>{auth.currentFlow.destination.application_name}</strong></p>
        {/if}

        <EmailUserSelectionRenderer />
        <PasswordRenderer />
        <TotpRenderer />
        <OIDCRenderer />
        <DoneRenderer />
    </div>
{/if}
```

Renderers render nothing while their step is inactive, so their order in the markup doesn't matter. Only include
renderers for plugins you installed on the server. Every renderer registers its plugin with the client on mount.

## Showing the logged-in user

authentikt doesn't decide how your app learns who is logged in. That depends on the credential your `onSuccess`
issues. A common pattern is a "who am I" endpoint, protected by your Ktor authentication provider, that the app
calls on startup:

```ts
// src/lib/user.ts
import { writable } from "svelte/store";

export type User = { id: string; displayName: string };
export const currentUser = writable<null | "anonymous" | User>(null);

export async function refreshUser() {
    const response = await fetch("/api/user/me");
    currentUser.set(response.status === 401 ? "anonymous" : await response.json());
}
```

The [`DoneRenderer`](frontend-renderers.md#done) reloads the page after a successful login, so `refreshUser()` runs
again and picks up the new cookie.
