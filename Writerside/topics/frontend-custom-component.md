# Custom components for built-in plugins

Snippets are the quickest way to restyle a built-in step (see [](frontend-renderers.md)). When a step needs its
own logic, its own markup structure or reuse across several apps, write a standalone component instead. The
built-in plugin class keeps doing the server communication, and your component only renders it.

This page builds two complete components:

- a **password step** with focus handling, error states, accessibility and a "start over" action, and
- a **completion step** that replaces the built-in page reload with a SvelteKit-friendly refresh.

## Snippet, component or central registration?

| Approach | Use it when | Effort |
|----------|-------------|--------|
| Snippet on a built-in renderer | You only want different markup or styling | Lowest |
| Self-registering component | You want a reusable component with its own logic, used like `<PasswordRenderer />` | Medium |
| Central registration + generic rendering | You want one place that maps namespaces to components and renders whatever step is active | Medium |

All three use the same plugin classes and talk to the server in exactly the same way.

## How built-in plugins and renderers fit together

Every built-in step consists of two parts, and both are exported:

| Plugin class (logic) | Renderer (UI) | Namespace |
|----------------------|---------------|-----------|
| `EmailUserSelectionPlugin` | `EmailUserSelectionRenderer` | `authentikt-builtin/email` |
| `PasswordPlugin` | `PasswordRenderer` | `authentikt-builtin/password` |
| `TotpPlugin` | `TotpRenderer` | `authentikt-builtin/totp` |
| `OIDCPlugin` | `OIDCRenderer` | `authentikt-builtin/oidc` |
| `DonePlugin` | `DoneRenderer` | `authentikt-builtin/done` |

A custom component replaces only the renderer. It registers the built-in plugin class under the built-in
namespace with `auth.registerPlugin(namespace, component, factory)` and renders the instance it gets back.

## Example 1: password step

### The component

```svelte
<!-- src/lib/auth/PasswordStep.svelte -->
<script lang="ts" module>
    // The component registers itself, so it needs a reference to its own constructor.
    import PasswordStep from "./PasswordStep.svelte";
</script>

<script lang="ts">
    import {
        PasswordPlugin,
        useAuthentiktContext,
        type PasswordPluginInstance,
    } from "@julius-babies/authentikt-svelte";

    let {
        plugin: externalPlugin,
    }: {
        /** Passed in when rendered generically via `activeStepEntry`. */
        plugin?: PasswordPluginInstance;
    } = $props();

    const auth = useAuthentiktContext();

    // Register the built-in logic with this component as its UI.
    const selfPlugin = auth.registerPlugin<PasswordPluginInstance>(
        "authentikt-builtin/password",
        PasswordStep,
        (a, ns) => new PasswordPlugin(a, ns),
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);
    const user = $derived(auth.currentFlow?.user ?? null);
    const loading = $derived(plugin.status === "loading");

    let input: HTMLInputElement | undefined = $state();

    // Focus the field as soon as this step becomes active.
    $effect(() => {
        if (plugin.isActive) input?.focus();
    });

    async function onsubmit(event: SubmitEvent) {
        event.preventDefault();
        if (!plugin.password || loading) return;

        await plugin.submit();

        // On success, the client already loaded the next step and this component hides itself.
        if (plugin.status === "password_incorrect") {
            plugin.password = "";
            input?.focus();
        }
    }

    function startOver() {
        plugin.password = "";
        void auth.startLoginFlow();
    }
</script>

{#if plugin.isActive}
    <form class="step" {onsubmit} aria-busy={loading}>
        <h2>Enter your password</h2>

        {#if user}
            <p class="identity">
                Signing in as <strong>{user.displayName}</strong>
                {#if user.username}<span>@{user.username}</span>{/if}
            </p>
        {/if}

        <label for="authentikt-password">Password</label>
        <input
            id="authentikt-password"
            bind:this={input}
            bind:value={plugin.password}
            type="password"
            name="password"
            autocomplete="current-password"
            required
            disabled={loading}
            aria-invalid={plugin.status === "password_incorrect"}
            aria-describedby="authentikt-password-error"
        />

        <p id="authentikt-password-error" class="error" role="alert">
            {#if plugin.status === "password_incorrect"}
                That password is not correct. Please try again.
            {:else if plugin.status === "error"}
                Something went wrong. Check your connection and try again.
            {/if}
        </p>

        <div class="actions">
            <button type="button" onclick={startOver} disabled={loading}>
                Start over
            </button>
            <button type="submit" disabled={!plugin.password || loading}>
                {loading ? "Checking…" : "Continue"}
            </button>
        </div>
    </form>
{/if}

<style>
    .step { display: grid; gap: 0.75rem; }
    .identity span { color: gray; margin-left: 0.25rem; }
    .error { min-height: 1.25rem; color: crimson; }
    .actions { display: flex; justify-content: space-between; }
</style>
```

What the component relies on:

| From the plugin instance | Used for |
|--------------------------|----------|
| `isActive` | Render only while the server's current step is the password step |
| `password` | Two-way binding of the input |
| `status` | `"ready"`, `"loading"`, `"password_incorrect"` or `"error"` for UI states |
| `submit()` | Sends the password. On success, it calls `auth.updateState()`, which loads the next step |

From the client, it uses `currentFlow.user` (set by the email step) and `startLoginFlow()` to begin a new flow.

### Using it

Use it in place of `PasswordRenderer`:

```svelte
<script lang="ts">
    import {
        useAuthentiktContext,
        EmailUserSelectionRenderer,
        TotpRenderer,
        DoneRenderer,
    } from "@julius-babies/authentikt-svelte";
    import PasswordStep from "$lib/auth/PasswordStep.svelte";

    const auth = useAuthentiktContext();
</script>

{#if auth.currentFlow}
    <EmailUserSelectionRenderer />
    <PasswordStep />
    <TotpRenderer />
    <DoneRenderer />
{/if}
```

> **Don't mount `PasswordRenderer` as well.** Both components register for `authentikt-builtin/password`, and both
> render while the step is active, so the user would see two password forms.
{style="warning"}

### Keeping state per flow

A self-registering component keeps the plugin instance it got when it mounted. Mount your step components inside
`{#if auth.currentFlow}`, as above. Then they are recreated whenever a flow is opened after the previous one was
closed, and the password field starts empty.

Calling `startLoginFlow()` while a flow is already open doesn't unmount anything, so reset the fields yourself, as
`startOver()` does above.

## Example 2: completion step {id="done-example"}

The built-in `DoneRenderer` calls the server as soon as the step is active, then reloads the page (or follows a
redirect). A custom component has to take over this behaviour, because it replaces the renderer completely. The
following component refreshes the user without a full page reload and keeps the device-flow message open until
the user closes it.

```svelte
<!-- src/lib/auth/DoneStep.svelte -->
<script lang="ts" module>
    import DoneStep from "./DoneStep.svelte";
</script>

<script lang="ts">
    import { DonePlugin, useAuthentiktContext } from "@julius-babies/authentikt-svelte";
    import { invalidateAll } from "$app/navigation";
    import { refreshUser } from "$lib/user";

    let { plugin: externalPlugin }: { plugin?: DonePlugin } = $props();

    const auth = useAuthentiktContext();

    const selfPlugin = auth.registerPlugin<DonePlugin>(
        "authentikt-builtin/done",
        DoneStep,
        (a, ns) => new DonePlugin(a, ns),
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);

    // 1. Complete the flow on the server as soon as this step is active.
    //    complete() is idempotent: it runs at most once per plugin instance.
    $effect(() => {
        if (plugin.isActive && plugin.result === null) void plugin.complete();
    });

    // 2. React to the result.
    $effect(() => {
        const result = plugin.result;
        if (!result) return;

        if (result.type === "redirect") {
            window.location.href = result.to;
            return;
        }

        if (result.type === "success") {
            void finish();
        }

        // "device_flow_success": keep the message visible, the user closes it.
    });

    async function finish() {
        await refreshUser();    // reads the new session cookie
        await invalidateAll();  // re-runs your load functions
        await auth.cancelFlow(); // closes the login UI
    }
</script>

{#if plugin.isActive}
    <div class="step" role="status">
        {#if plugin.result?.type === "device_flow_success"}
            <h2>You're signed in</h2>
            <p>
                You can now return to
                {auth.currentFlow?.destination.type === "device_flow"
                    ? auth.currentFlow.destination.application_name
                    : "your device"}.
            </p>
            <button onclick={auth.cancelFlow}>Close</button>
        {:else}
            <h2>Signing you in…</h2>
        {/if}
    </div>
{/if}
```

`refreshUser` is the "who am I" helper from [](frontend-setup.md). Use whatever your app uses to load the
current user.

`DonePlugin` exposes:

| Member | Description |
|--------|-------------|
| `isActive` | Whether the done step is active |
| `result` | `null` until the server responded, then `{ type: "success", cookies? }`, `{ type: "redirect", to, cookies? }` or `{ type: "device_flow_success" }` |
| `complete()` | Calls the server once. Further calls are ignored |

## Alternative: central registration and generic rendering

Instead of letting every component register itself, you can register all steps in one place and render the
active one generically. The step components then become purely presentational and receive the instance as a
prop:

```svelte
<!-- src/lib/auth/PasswordForm.svelte -->
<script lang="ts">
    import type { FlowUserState, PasswordPluginInstance } from "@julius-babies/authentikt-svelte";

    let { plugin, user }: { plugin: PasswordPluginInstance; user?: FlowUserState | null } = $props();
</script>

<form onsubmit={(e) => { e.preventDefault(); plugin.submit(); }}>
    <p>Signing in as {user?.displayName}</p>
    <input type="password" autocomplete="current-password" bind:value={plugin.password} />
    {#if plugin.status === "password_incorrect"}<p role="alert">Incorrect password.</p>{/if}
    <button disabled={plugin.status === "loading"}>Continue</button>
</form>
```

```svelte
<!-- src/lib/auth/LoginFlow.svelte -->
<script lang="ts">
    import {
        useAuthentiktContext,
        EmailUserSelectionPlugin,
        PasswordPlugin,
        TotpPlugin,
        DonePlugin,
    } from "@julius-babies/authentikt-svelte";
    import EmailForm from "./EmailForm.svelte";
    import PasswordForm from "./PasswordForm.svelte";
    import TotpForm from "./TotpForm.svelte";
    import DoneView from "./DoneView.svelte";

    const auth = useAuthentiktContext();

    auth.registerPlugin("authentikt-builtin/email", EmailForm, (a, ns) => new EmailUserSelectionPlugin(a, ns));
    auth.registerPlugin("authentikt-builtin/password", PasswordForm, (a, ns) => new PasswordPlugin(a, ns));
    auth.registerPlugin("authentikt-builtin/totp", TotpForm, (a, ns) => new TotpPlugin(a, ns));
    auth.registerPlugin("authentikt-builtin/done", DoneView, (a, ns) => new DonePlugin(a, ns));
</script>

{#if auth.currentFlow && auth.activeStepEntry && auth.activeStepPlugin}
    {@const Step = auth.activeStepEntry.component}
    <Step plugin={auth.activeStepPlugin} user={auth.currentFlow.user} />
{/if}
```

With this approach:

- Only the active step is mounted, so the components don't need `isActive` checks.
- `auth.activeStepPlugin` always returns the instance for the current flow. Instances are recreated when a flow
  starts, so no state leaks between flows.
- Steps with side effects, such as `DoneView` calling `plugin.complete()` and `OIDCForm` calling
  `plugin.redirect()`, must trigger them in an `$effect`, as in [example 2](#done-example).

## Checklist

- Register the **built-in plugin class** under the **built-in namespace**. Otherwise, the requests go to the wrong
  server route.
- Mount either your component or the built-in renderer for a namespace, never both.
- Render only while `plugin.isActive` (self-registering components).
- Replicate the side effects of renderers you replace: `DoneRenderer` completes the flow, `OIDCRenderer`
  redirects to the identity provider.
- Mount step components inside `{#if auth.currentFlow}` so every flow starts with fresh state.
