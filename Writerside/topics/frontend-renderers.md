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
| `JunctionRenderer` | `authentikt-builtin/junction` | `JunctionPluginInstance` |
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
| `status` | `"ready" \| "loading" \| "user_not_existing" \| "rate_limited" \| "error"` | Request status |
| `typedPayload` | `{ with_username: boolean }` | Whether usernames are accepted as well |
| `rateLimit` | `RateLimitState \| null` | Lookups left and lock state, see [below](#rate-limits) |
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
| `status` | `"ready" \| "loading" \| "password_incorrect" \| "rate_limited" \| "error"` | Request status |
| `rateLimit` | `RateLimitState \| null` | Attempts left and lock state, see [below](#rate-limits) |
| `isActive` | `boolean` | Whether this step is active |
| `submit()` | `() => Promise<void>` | Sends the password and loads the next step on success |

## TotpRenderer {id="totp"}

Server plugin: [](totp-plugin.md)

| Member | Type | Description |
|--------|------|-------------|
| `totp` | `string` | Input value, bindable |
| `status` | `"ready" \| "loading" \| "totp_incorrect" \| "rate_limited" \| "error"` | Request status |
| `rateLimit` | `RateLimitState \| null` | Attempts left and lock state, see [below](#rate-limits) |
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

## Rate limits {id="rate-limits"}

The email, password and TOTP plugins expose the step's [rate limit](rate-limiting.md) as `rateLimit`. It is `null`
if the server does not limit the step.

| Member | Type | Description |
|--------|------|-------------|
| `maxTries` | `number` | Failed attempts allowed per period |
| `periodSeconds` | `number` | Length of the period |
| `remainingTries` | `number` | Attempts left before the step is locked |
| `isLocked` | `boolean` | Whether the step is locked. `submit()` does nothing while locked |
| `remainingLockSeconds` | `number` | Seconds until the lock expires, `0` if not locked |
| `lockedUntil` | `Date \| null` | When the lock expires |

While the step is locked, `remainingLockSeconds` and `isLocked` update every second. When the lock expires, the
flow state is reloaded to get the new number of remaining tries. The default UIs disable the input while locked
and show a countdown. In a snippet, use `formatLockDuration` to format the seconds as `m:ss`:

```svelte
<script>
    import { PasswordRenderer, formatLockDuration } from "authentikt-svelte";
</script>

<PasswordRenderer>
    {#snippet children(plugin)}
        <input type="password" bind:value={plugin.password} disabled={plugin.rateLimit?.isLocked} />
        {#if plugin.rateLimit?.isLocked}
            <p>Too many attempts. Try again in {formatLockDuration(plugin.rateLimit.remainingLockSeconds)}.</p>
        {:else if plugin.status === "password_incorrect"}
            <p>Wrong password, {plugin.rateLimit?.remainingTries} attempts left.</p>
        {/if}
        <button onclick={plugin.submit} disabled={plugin.rateLimit?.isLocked}>Continue</button>
    {/snippet}
</PasswordRenderer>
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

## JunctionRenderer {id="junction"}

Server plugin: [](junction-plugin.md)

| Member | Type | Description |
|--------|------|-------------|
| `options` | `string[]` | Namespaces of the steps the user can choose from |
| `status` | `"ready" \| "loading" \| "error"` | Request status |
| `isActive` | `boolean` | Whether this step is active |
| `select(namespace)` | `(namespace: string) => Promise<void>` | Selects an option and loads the next step |

Besides the common props, `JunctionRenderer` accepts `namespace` for junctions installed under a different namespace,
and `label`, a function that maps a namespace to the text shown in the default UI. Without `label`, the namespace
itself is shown.

```svelte
<JunctionRenderer>
    {#snippet children(plugin)}
        <p>How do you want to confirm it's you?</p>
        {#each plugin.options as option (option)}
            <button onclick={() => plugin.select(option)}>{labels[option] ?? option}</button>
        {/each}
    {/snippet}
</JunctionRenderer>
```

## AuthentiktAlternatives {id="alternatives"}

Lists the [alternatives](step-order.md#alternatives) of the active step. Place it below the step renderers. It
renders nothing if the active step has no alternatives.

| Prop | Type | Description |
|------|------|-------------|
| `children` | `Snippet<[AlternativeOption[]]>` | Custom UI. Without it, a list of buttons is shown |
| `label` | `(namespace: string) => string` | Looks up the label of a step. Without it, the namespace itself is shown |

Each `AlternativeOption` has a `namespace`, its `label` and `select()`, which switches to it.

```svelte
<AuthentiktAlternatives label={(ns) => labels[ns] ?? ns}>
    {#snippet children(options)}
        {#each options as option (option.namespace)}
            <button onclick={option.select}>Use {option.label} instead</button>
        {/each}
    {/snippet}
</AuthentiktAlternatives>
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
`EmailUserSelectionPlugin`, `PasswordPlugin`, `TotpPlugin`, `OIDCPlugin`, `JunctionPlugin` and `DonePlugin`.

For complete, step-by-step examples, see [](frontend-custom-component.md).
