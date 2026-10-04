# Built-in renderers

Every built-in server plugin has a Svelte renderer. A renderer:

- registers its plugin with the client when it mounts,
- renders only while its step is active, and
- shows a default UI, or your own `children` snippet if you pass one.

The snippet receives the reactive plugin instance. Bind inputs to its fields and call its actions. Requests,
status handling and flow updates are done for you.

| Renderer | Namespace | Instance type |
|----------|-----------|---------------|
| `EmailUserSelectionRenderer` | `authentikt-builtin/email` | `EmailUserSelectionPluginInstance` |
| `PasswordRenderer` | `authentikt-builtin/password` | `PasswordPluginInstance` |
| `TotpRenderer` | `authentikt-builtin/totp` | `TotpPluginInstance` |
| `OIDCRenderer` | `authentikt-builtin/oidc` | `OIDCPluginInstance` |
| `DoneRenderer` | `authentikt-builtin/done` | `DonePluginInstance` |

All renderers accept the same props:

| Prop | Type | Description |
|------|------|-------------|
| `children` | `Snippet<[Instance]>` | Custom UI. Without it, the default UI is shown |
| `plugin` | `Instance` | Use this instance instead of the self-registered one (for generic rendering) |
| `user` | `FlowUserState \| null` | Accepted for API symmetry. Currently unused by the built-ins |

## Customizing with snippets

```svelte
<PasswordRenderer>
    {#snippet children(plugin)}
        <form onsubmit={(e) => { e.preventDefault(); plugin.submit(); }}>
            <h2>Welcome back{auth.currentFlow?.user ? `, ${auth.currentFlow.user.displayName}` : ""}</h2>
            <input type="password" bind:value={plugin.password} autocomplete="current-password" />
            {#if plugin.status === "password_incorrect"}
                <p class="error">Incorrect password. Please try again.</p>
            {/if}
            <button disabled={plugin.status === "loading"}>
                {plugin.status === "loading" ? "Checking..." : "Continue"}
            </button>
        </form>
    {/snippet}
</PasswordRenderer>
```

The default UIs use Tailwind classes. If you don't use Tailwind, provide snippets for every renderer.

## EmailUserSelectionRenderer {id="email"}

Server plugin: [](email-plugin.md)

| Member | Type | Description |
|--------|------|-------------|
| `email` | `string` | Input value, bindable |
| `status` | `"ready" \| "loading" \| "user_not_existing" \| "error"` | Request status |
| `typedPayload` | `{ with_username: boolean }` | Whether usernames are accepted as well |
| `isActive` | `boolean` | Whether this step is active |
| `submit()` | `() => Promise<void>` | Sends the email. On success, calls `setUser(...)` and loads the next step |

```svelte
<EmailUserSelectionRenderer>
    {#snippet children(plugin)}
        <input
            bind:value={plugin.email}
            placeholder={plugin.typedPayload.with_username ? "Email or username" : "Email"}
        />
        {#if plugin.status === "user_not_existing"}<p>No account found.</p>{/if}
        <button onclick={plugin.submit}>Next</button>
    {/snippet}
</EmailUserSelectionRenderer>
```

## PasswordRenderer {id="password"}

Server plugin: [](password-plugin.md)

| Member | Type | Description |
|--------|------|-------------|
| `password` | `string` | Input value, bindable |
| `status` | `"ready" \| "loading" \| "password_incorrect" \| "error"` | Request status |
| `isActive` | `boolean` | Whether this step is active |
| `submit()` | `() => Promise<void>` | Sends the password and loads the next step on success |

## TotpRenderer {id="totp"}

Server plugin: [](totp-plugin.md)

| Member | Type | Description |
|--------|------|-------------|
| `totp` | `string` | Input value, bindable |
| `status` | `"ready" \| "loading" \| "totp_incorrect" \| "error"` | Request status |
| `isActive` | `boolean` | Whether this step is active |
| `submit()` | `() => Promise<void>` | Sends the code and loads the next step on success |

```svelte
<TotpRenderer>
    {#snippet children(plugin)}
        <input
            bind:value={plugin.totp}
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
        />
        <button onclick={plugin.submit}>Verify</button>
    {/snippet}
</TotpRenderer>
```

## OIDCRenderer {id="oidc"}

Server plugin: [](oidc-plugin.md)

| Member | Type | Description |
|--------|------|-------------|
| `authorizeUrl` | `string \| undefined` | The provider's authorization URL from the payload |
| `isActive` | `boolean` | Whether this step is active |
| `redirect()` | `() => void` | Navigates to `authorizeUrl` (only once per instance) |

The renderer calls `redirect()` automatically as soon as the step becomes active, even with a custom snippet. Use
the snippet to show a "Redirecting to your identity provider..." message.

## DoneRenderer {id="done"}

Server plugin: [](done-plugin.md)

| Member | Type | Description |
|--------|------|-------------|
| `result` | `DoneResult` | `null` until the server responded, then the response |
| `isActive` | `boolean` | Whether this step is active |
| `complete()` | `() => Promise<void>` | Calls the server (called automatically, runs only once) |

```ts
type DoneResult =
    | { type: "success"; cookies?: string[] }
    | { type: "redirect"; to: string; cookies?: string[] }
    | { type: "device_flow_success" }
    | null;
```

As soon as the step is active, the renderer calls `complete()`. When the result arrives:

| Result | Behaviour |
|--------|-----------|
| `success` | Ends the flow and reloads the page after 2 seconds |
| `redirect` | Ends the flow and navigates to `to` after 1 second |
| `device_flow_success` | Ends the flow immediately. The device receives its token separately |

These side effects also apply when you provide a snippet. The snippet only replaces the markup:

```svelte
<DoneRenderer>
    {#snippet children(plugin)}
        {#if plugin.result?.type === "device_flow_success"}
            <p>All set. You can return to your device now.</p>
        {:else}
            <p>Signed in. One moment...</p>
        {/if}
    {/snippet}
</DoneRenderer>
```

## Using your own component for a built-in plugin

If you prefer a separate component over a snippet, register it for the built-in namespace together with the
built-in plugin class:

```ts
import { PasswordPlugin, useAuthentiktContext } from "@julius-babies/authentikt-svelte";
import MyPasswordStep from "./MyPasswordStep.svelte";

const auth = useAuthentiktContext();
auth.registerPlugin("authentikt-builtin/password", MyPasswordStep, (a, ns) => new PasswordPlugin(a, ns));
```

`MyPasswordStep` receives `plugin` as a prop. Render it through
[`activeStepEntry`](frontend-client.md#generic-rendering). The exported plugin classes are
`EmailUserSelectionPlugin`, `PasswordPlugin`, `TotpPlugin`, `OIDCPlugin` and `DonePlugin`.

For complete, step-by-step examples, see [](frontend-custom-component.md).
