<script lang="ts">
    /**
     * Password verification step renderer.
     *
     * Self-registers as the `"authentikt-builtin/password"` step plugin on mount.
     * Renders a password input with submit button by default, or uses the
     * provided `children` snippet for custom UI.
     *
     * When used via `<AuthentiktStepRenderer>`, pass the plugin instance as the
     * `plugin` prop instead of relying on self-registration.
     */
    import { useAuthentiktContext } from "$lib/context";
    import { formatLockDuration } from "$lib/rate-limit.svelte";
    import { PasswordPlugin } from "./PasswordPlugin.svelte";
    import type { PasswordPluginInstance, PasswordSnippet } from "./types";
    import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

    let {
        children,
        plugin: externalPlugin,
        user: _user,
    }: {
        children?: PasswordSnippet;
        plugin?: PasswordPluginInstance;
        user?: FlowUserState | null;
    } = $props();

    const authentikt = useAuthentiktContext();
    const namespace = "authentikt-builtin/password";

    const selfPlugin = authentikt.registerPlugin<PasswordPluginInstance>(
        namespace,
        PasswordRenderer,
        (auth, ns) => new PasswordPlugin(auth, ns)
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);
</script>

<script lang="ts" module>
    import PasswordRenderer from "./PasswordRenderer.svelte";
</script>

{#if plugin.isActive}
    {#if children}
        {@render children(plugin)}
    {:else}
        <div class="flex flex-col gap-2">
            <input
                type="password"
                placeholder="Password"
                bind:value={plugin.password}
                disabled={plugin.rateLimit?.isLocked}
                class="border p-2 rounded disabled:opacity-50"
            />
            {#if plugin.rateLimit?.isLocked}
                <span class="text-red-400 text-sm">
                    Too many attempts. Try again in {formatLockDuration(plugin.rateLimit.remainingLockSeconds)}.
                </span>
            {:else if plugin.status === "password_incorrect"}
                <span class="text-red-400 text-sm">
                    Password incorrect{#if plugin.rateLimit}, {plugin.rateLimit.remainingTries} {plugin.rateLimit.remainingTries === 1 ? "attempt" : "attempts"} left{/if}
                </span>
            {/if}
            <button
                onclick={plugin.submit}
                disabled={plugin.status === "loading" || plugin.rateLimit?.isLocked}
                class="bg-blue-600 text-white p-2 rounded disabled:opacity-50"
            >
                {plugin.status === "loading" ? "Checking..." : "Continue"}
            </button>
        </div>
    {/if}
{/if}
