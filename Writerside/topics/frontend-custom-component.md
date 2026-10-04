# Custom components for built-in plugins

You can replace the UI of any built-in step, for example the password form, with your own Svelte component. The
server communication stays in the library, and your component only renders.

## How it works

Every built-in step on the client consists of two parts:

- **Plugin class** (for example `PasswordPlugin`): holds the reactive state (`password`, `status`) and sends the
  requests to the server (`submit()`).
- **Renderer** (for example `PasswordRenderer`): the Svelte component that shows the form.

They are connected through the client's plugin registry. A renderer does two things:

1. On mount, it calls `auth.registerPlugin(namespace, component, factory)`. This stores *"for namespace X, create
   the plugin with `factory` and render it with `component`"* and returns the plugin instance.
2. It renders its markup while `plugin.isActive` is `true`, that is, while the server's current step has this
   namespace.

A custom component does exactly the same, just with your own markup. Because it registers the **built-in plugin
class** under the **built-in namespace**, the requests still go to the right server routes.

| Namespace | Plugin class | Built-in renderer |
|-----------|--------------|-------------------|
| `authentikt-builtin/email` | `EmailUserSelectionPlugin` | `EmailUserSelectionRenderer` |
| `authentikt-builtin/password` | `PasswordPlugin` | `PasswordRenderer` |
| `authentikt-builtin/totp` | `TotpPlugin` | `TotpRenderer` |
| `authentikt-builtin/oidc` | `OIDCPlugin` | `OIDCRenderer` |
| `authentikt-builtin/done` | `DonePlugin` | `DoneRenderer` |

The members of each plugin instance (fields, statuses, actions) are listed in [](frontend-renderers.md).

## Example: a custom password step

<procedure title="Build the component" id="password-procedure">
<step>

**Register the built-in plugin with your component.** Create `PasswordStep.svelte`. The component needs a
reference to itself to pass it to `registerPlugin`, which is what the `module` script is for:

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

    const auth = useAuthentiktContext();

    const plugin = auth.registerPlugin<PasswordPluginInstance>(
        "authentikt-builtin/password",       // built-in namespace
        PasswordStep,                        // your component
        (a, ns) => new PasswordPlugin(a, ns) // built-in plugin class
    );
</script>
```

</step>
<step>

**Render while the step is active.** Below the scripts, add your markup. Bind the input to `plugin.password`,
show messages based on `plugin.status` and call `plugin.submit()`:

```svelte
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

When the password is correct, `submit()` loads the next step from the server. `plugin.isActive` becomes `false`,
and the form disappears.

</step>
<step>

**Use it instead of the built-in renderer:**

```svelte
{#if auth.currentFlow}
    <EmailUserSelectionRenderer />
    <PasswordStep />
    <TotpRenderer />
    <DoneRenderer />
{/if}
```

Remove `<PasswordRenderer />`. If both are mounted, both register for the same namespace and both show a form.

Keep the components inside `{#if auth.currentFlow}`. They are then recreated for every new flow, so the password
field starts empty.

</step>
</procedure>

## Alternative: register all steps in one place {id="central"}

Instead of letting each component register itself, you can register every step in a single parent component and
render only the step that is currently active. Your step components then become simple components that receive
the plugin instance as a prop.

The client provides two values for this:

- `auth.activeStepEntry.component`: the component registered for the current step's namespace
- `auth.activeStepPlugin`: the plugin instance for the current step

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

    // namespace → (component, plugin class)
    auth.registerPlugin("authentikt-builtin/email", EmailForm, (a, ns) => new EmailUserSelectionPlugin(a, ns));
    auth.registerPlugin("authentikt-builtin/password", PasswordForm, (a, ns) => new PasswordPlugin(a, ns));
    auth.registerPlugin("authentikt-builtin/done", DoneView, (a, ns) => new DonePlugin(a, ns));
</script>

<!-- Render whatever step is active -->
{#if auth.currentFlow && auth.activeStepEntry && auth.activeStepPlugin}
    {@const Step = auth.activeStepEntry.component}
    <Step plugin={auth.activeStepPlugin} user={auth.currentFlow.user} />
{/if}
```

A step component only needs to accept `plugin` (and optionally `user`). Since only the active step is mounted, it
doesn't need an `isActive` check:

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

## Steps with side effects {id="side-effects"}

Two built-in renderers do more than show markup. If you replace them, your component has to do this work itself:

`DoneRenderer`
: As soon as the step is active, it calls `plugin.complete()`. The server then runs your `onSuccess` and sets the
cookies. When `plugin.result` arrives, the renderer reloads the page (`success`), follows the redirect
(`redirect`) or closes the flow (`device_flow_success`).

`OIDCRenderer`
: As soon as the step is active, it calls `plugin.redirect()`, which sends the browser to the identity provider.

Here is a done step for the [central registration](#central) above. Instead of reloading the page, it refreshes
the SvelteKit data and closes the login:

```svelte
<!-- src/lib/auth/DoneView.svelte -->
<script lang="ts">
    import { useAuthentiktContext, type DonePlugin } from "@julius-babies/authentikt-svelte";
    import { invalidateAll } from "$app/navigation";

    let { plugin }: { plugin: DonePlugin } = $props();
    const auth = useAuthentiktContext();

    // 1. Tell the server to finish the login (runs at most once per instance)
    $effect(() => {
        if (plugin.result === null) void plugin.complete();
    });

    // 2. React to the server's answer
    $effect(() => {
        const result = plugin.result;
        if (result?.type === "redirect") window.location.href = result.to;
        if (result?.type === "success") void invalidateAll().then(auth.cancelFlow);
        // "device_flow_success": keep the message visible until the user closes it
    });
</script>

{#if plugin.result?.type === "device_flow_success"}
    <p>Signed in. You can return to your device.</p>
    <button onclick={auth.cancelFlow}>Close</button>
{:else}
    <p>Signing you in…</p>
{/if}
```

If you write it as a self-registering component instead, add the `registerPlugin` call as in the password example,
and only run the effects and show the markup while `plugin.isActive` is `true`.

`plugin.result` is `null` until the server responds. After that, it is one of:

```ts
{ type: "success"; cookies?: string[] }
{ type: "redirect"; to: string; cookies?: string[] }
{ type: "device_flow_success" }
```
