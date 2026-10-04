<script lang="ts">
    import { useAuthentiktContext } from "$lib/context";
    import { formatLockDuration } from "$lib/rate-limit.svelte";
    import { TotpPlugin } from "./TotpPlugin.svelte";
    import type { TotpPluginInstance, TotpSnippet } from "./types";
    import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

    let {
        children,
        plugin: externalPlugin,
        user: _user,
    }: {
        children?: TotpSnippet;
        plugin?: TotpPluginInstance;
        user?: FlowUserState | null;
    } = $props();

    const authentikt = useAuthentiktContext();
    const namespace = "authentikt-builtin/totp";

    const selfPlugin = authentikt.registerPlugin<TotpPluginInstance>(
        namespace,
        TotpRenderer,
        (auth, ns) => new TotpPlugin(auth, ns)
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);
</script>

<script lang="ts" module>
    import TotpRenderer from "./TotpRenderer.svelte";
</script>

{#if plugin.isActive}
    {#if children}
        {@render children(plugin)}
    {:else}
        <div class="flex flex-col gap-2">
            <input
                type="text"
                placeholder="TOTP Code"
                bind:value={plugin.totp}
                disabled={plugin.rateLimit?.isLocked}
                class="border p-2 rounded disabled:opacity-50"
            />
            {#if plugin.rateLimit?.isLocked}
                <span class="text-red-400 text-sm">
                    Too many attempts. Try again in {formatLockDuration(plugin.rateLimit.remainingLockSeconds)}.
                </span>
            {:else if plugin.status === "totp_incorrect"}
                <span class="text-red-400 text-sm">
                    TOTP incorrect{#if plugin.rateLimit}, {plugin.rateLimit.remainingTries} {plugin.rateLimit.remainingTries === 1 ? "attempt" : "attempts"} left{/if}
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
