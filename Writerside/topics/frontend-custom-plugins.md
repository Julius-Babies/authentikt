# Writing a client plugin

A custom server step needs a client counterpart. Client plugins follow the same headless split as the built-ins:

- a **plugin class** holds reactive state (`$state`) and talks to the server, and
- a **renderer component** registers the class under the step's namespace and renders UI, either its own default
  or a snippet passed by the user.

This page builds the frontend for the terms-of-service plugin from [](custom-step-plugins.md). The server plugin
sends `{ version, accepted }` as payload and expects `POST { accepted: true }`.

## The contract

A plugin instance must implement `PluginLike`:

```ts
interface PluginLike {
    readonly isActive: boolean;  // true while the server's current step has this namespace
    readonly namespace: string;
}
```

Everything else, such as input fields, status values and actions, is up to you.

## Plugin class

Put the class in a `.svelte.ts` file so you can use runes:

```ts
// src/lib/auth/TermsPlugin.svelte.ts
import { useAuthentiktContext, type PluginLike } from "@julius-babies/authentikt-svelte";

type AuthentiktClient = ReturnType<typeof useAuthentiktContext>;

export type TermsStatus = "ready" | "loading" | "error";

export class TermsPlugin implements PluginLike {
    accepted = $state(false);
    status = $state<TermsStatus>("ready");

    private readonly auth: AuthentiktClient;
    private readonly _ns: string;

    constructor(auth: AuthentiktClient, namespace: string) {
        this.auth = auth;
        this._ns = namespace;
    }

    get namespace(): string {
        return this._ns;
    }

    get isActive(): boolean {
        const step = this.auth.currentFlow?.step;
        return step?.type === "step" && step.namespace === this._ns;
    }

    /** Reads the server-side payload of this step. */
    get version(): string | undefined {
        const step = this.auth.currentFlow?.step;
        if (step?.type !== "step") return undefined;
        return step.payload?.version as string | undefined;
    }

    submit = async (): Promise<void> => {
        this.status = "loading";
        try {
            const url = new URL("steps/plugins/" + this._ns, this.auth.sessionUrl);
            const response = await fetch(url, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ accepted: this.accepted }),
            });
            const data = await response.json();

            if (data.success === true) {
                await this.auth.updateState(); // load the next step
                this.status = "ready";
            } else {
                this.status = "error";
            }
        } catch (e) {
            console.error(e);
            this.status = "error";
        }
    };
}
```

The essential parts:

- Build URLs from `auth.sessionUrl`, which is `{baseUrl}flow/{sessionId}/`.
- Read the server state from `auth.currentFlow.step.payload`.
- After the server advanced the flow, call `auth.updateState()`.
- Define actions as arrow functions, so they can be passed directly as event handlers.

## Renderer component

```svelte
<!-- src/lib/auth/TermsRenderer.svelte -->
<script lang="ts" module>
    import TermsRenderer from "./TermsRenderer.svelte";
</script>

<script lang="ts">
    import type { Snippet } from "svelte";
    import { useAuthentiktContext } from "@julius-babies/authentikt-svelte";
    import { TermsPlugin } from "./TermsPlugin.svelte";

    let {
        children,
        plugin: externalPlugin,
    }: {
        children?: Snippet<[TermsPlugin]>;
        plugin?: TermsPlugin;
    } = $props();

    const auth = useAuthentiktContext();

    const selfPlugin = auth.registerPlugin<TermsPlugin>(
        "acme/terms",
        TermsRenderer,
        (a, ns) => new TermsPlugin(a, ns),
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);
</script>

{#if plugin.isActive}
    {#if children}
        {@render children(plugin)}
    {:else}
        <label>
            <input type="checkbox" bind:checked={plugin.accepted} />
            I accept the terms of service (version {plugin.version})
        </label>
        <button onclick={plugin.submit} disabled={!plugin.accepted || plugin.status === "loading"}>
            Continue
        </button>
    {/if}
{/if}
```

The module script imports the component itself, so it can register itself as the plugin's component. The optional
`plugin` prop lets the component work with [generic rendering](frontend-client.md#generic-rendering).

## Using it

Place it next to the built-in renderers:

```svelte
<EmailUserSelectionRenderer />
<PasswordRenderer />
<TermsRenderer />
<DoneRenderer />
```

## Exporting types

If you publish the plugin as a library, export the instance type and the snippet type, the same way the built-ins
do (`PasswordPluginInstance`, `PasswordSnippet`, and so on). Users can then type their snippets.

## Reusing a built-in renderer

The registry is just `namespace → (component, factory)`. If a built-in renderer's UI fits your plugin's API, you can
pair it with your own class, as long as your class exposes the same fields and actions:

```ts
auth.registerPlugin("acme/sms-code", PasswordRenderer, (a, ns) => new SmsCodePlugin(a, ns));
```
