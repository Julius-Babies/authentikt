# Custom components for built-in plugins

A built-in step consists of a **plugin class** (state + server calls) and a **renderer** (UI). To use your own UI,
keep the plugin class and register it under the built-in namespace with your component:

```ts
auth.registerPlugin(namespace, YourComponent, (auth, ns) => new BuiltInPluginClass(auth, ns));
```

| Namespace | Plugin class | Replaces |
|-----------|--------------|----------|
| `authentikt-builtin/email` | `EmailUserSelectionPlugin` | `EmailUserSelectionRenderer` |
| `authentikt-builtin/password` | `PasswordPlugin` | `PasswordRenderer` |
| `authentikt-builtin/totp` | `TotpPlugin` | `TotpRenderer` |
| `authentikt-builtin/oidc` | `OIDCPlugin` | `OIDCRenderer` |
| `authentikt-builtin/done` | `DonePlugin` | `DoneRenderer` |

There are two ways to wire this up: a component that [registers itself](#self-registering), or
[central registration](#central) with generic rendering.

## Self-registering component {id="self-registering"}

The component registers itself on mount and renders while its step is active. It is used like a built-in renderer.

```svelte
<!-- src/lib/auth/PasswordStep.svelte -->
<script lang="ts" module>
    import PasswordStep from "./PasswordStep.svelte";
</script>

<script lang="ts">
    import {
        PasswordPlugin,
        useAuthentiktContext,
        type PasswordPluginInstance,
    } from "@julius-babies/authentikt-svelte";

    let { plugin: externalPlugin }: { plugin?: PasswordPluginInstance } = $props();

    const auth = useAuthentiktContext();

    const selfPlugin = auth.registerPlugin<PasswordPluginInstance>(
        "authentikt-builtin/password",
        PasswordStep,
        (a, ns) => new PasswordPlugin(a, ns),
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);
</script>

{#if plugin.isActive}
    <form onsubmit={(e) => { e.preventDefault(); plugin.submit(); }}>
        <p>Signing in as {auth.currentFlow?.user?.displayName}</p>
        <input type="password" autocomplete="current-password" bind:value={plugin.password} />

        {#if plugin.status === "password_incorrect"}
            <p role="alert">Incorrect password.</p>
        {:else if plugin.status === "error"}
            <p role="alert">Something went wrong.</p>
        {/if}

        <button disabled={plugin.status === "loading"}>Continue</button>
    </form>
{/if}
```

- The module script imports the component itself, so it can pass itself to `registerPlugin`.
- The optional `plugin` prop makes the component usable with [central registration](#central) as well.
- `plugin.submit()` calls `auth.updateState()` on success. The next step becomes active, and this component hides
  itself.

Use it instead of the built-in renderer:

```svelte
{#if auth.currentFlow}
    <EmailUserSelectionRenderer />
    <PasswordStep />
    <TotpRenderer />
    <DoneRenderer />
{/if}
```

> Don't mount `PasswordRenderer` as well. Both would register for the same namespace and both would render.
{style="warning"}

A self-registering component keeps the instance it got on mount. Mount it inside `{#if auth.currentFlow}`, so
it is recreated (with empty fields) for each new flow.

## Central registration {id="central"}

Register all steps in one place and render only the active one. The step components receive the instance as a
prop and don't need `isActive` checks:

```svelte
<!-- src/lib/auth/LoginFlow.svelte -->
<script lang="ts">
    import {
        useAuthentiktContext,
        EmailUserSelectionPlugin,
        PasswordPlugin,
        DonePlugin,
    } from "@julius-babies/authentikt-svelte";
    import EmailForm from "./EmailForm.svelte";
    import PasswordForm from "./PasswordForm.svelte";
    import DoneView from "./DoneView.svelte";

    const auth = useAuthentiktContext();

    auth.registerPlugin("authentikt-builtin/email", EmailForm, (a, ns) => new EmailUserSelectionPlugin(a, ns));
    auth.registerPlugin("authentikt-builtin/password", PasswordForm, (a, ns) => new PasswordPlugin(a, ns));
    auth.registerPlugin("authentikt-builtin/done", DoneView, (a, ns) => new DonePlugin(a, ns));
</script>

{#if auth.currentFlow && auth.activeStepEntry && auth.activeStepPlugin}
    {@const Step = auth.activeStepEntry.component}
    <Step plugin={auth.activeStepPlugin} user={auth.currentFlow.user} />
{/if}
```

```svelte
<!-- src/lib/auth/PasswordForm.svelte -->
<script lang="ts">
    import type { FlowUserState, PasswordPluginInstance } from "@julius-babies/authentikt-svelte";

    let { plugin, user }: { plugin: PasswordPluginInstance; user?: FlowUserState | null } = $props();
</script>

<form onsubmit={(e) => { e.preventDefault(); plugin.submit(); }}>
    <p>Signing in as {user?.displayName}</p>
    <input type="password" bind:value={plugin.password} />
    <button disabled={plugin.status === "loading"}>Continue</button>
</form>
```

`auth.activeStepPlugin` always returns the instance of the current flow, so no state carries over between flows.

## Steps with side effects {id="side-effects"}

Some renderers do more than render. A custom component for these steps must trigger the same actions itself:

| Renderer | Side effect to replicate |
|----------|--------------------------|
| `DoneRenderer` | Call `plugin.complete()` when the step is active, then handle `plugin.result` |
| `OIDCRenderer` | Call `plugin.redirect()` when the step is active |

Example: a done step that refreshes the app via SvelteKit instead of reloading the page.

```svelte
<!-- src/lib/auth/DoneView.svelte -->
<script lang="ts">
    import { useAuthentiktContext, type DonePlugin } from "@julius-babies/authentikt-svelte";
    import { invalidateAll } from "$app/navigation";

    let { plugin }: { plugin: DonePlugin } = $props();
    const auth = useAuthentiktContext();

    // Complete the flow on the server. Runs at most once per instance.
    $effect(() => {
        if (plugin.result === null) void plugin.complete();
    });

    $effect(() => {
        const result = plugin.result;
        if (result?.type === "redirect") window.location.href = result.to;
        if (result?.type === "success") void invalidateAll().then(auth.cancelFlow);
        // "device_flow_success": keep the message until the user closes it
    });
</script>

{#if plugin.result?.type === "device_flow_success"}
    <p>Signed in. You can return to your device.</p>
    <button onclick={auth.cancelFlow}>Close</button>
{:else}
    <p>Signing you in…</p>
{/if}
```

This version is written for central registration. As a self-registering component, add the `registerPlugin` call
and wrap both effects and the markup in `plugin.isActive` checks.

`plugin.result` is `null` until the server responds, then one of:

```ts
{ type: "success"; cookies?: string[] }
{ type: "redirect"; to: string; cookies?: string[] }
{ type: "device_flow_success" }
```
