<script lang="ts">
    import { useAuthentiktContext } from "$lib/context";
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

    const selfPlugin = authentikt.registerStepPlugin<TotpPluginInstance>(
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
                class="border p-2 rounded"
            />
            {#if plugin.status === "totp_incorrect"}
                <span class="text-red-400 text-sm">TOTP incorrect</span>
            {/if}
            <button
                onclick={plugin.submit}
                disabled={plugin.status === "loading"}
                class="bg-blue-600 text-white p-2 rounded disabled:opacity-50"
            >
                {plugin.status === "loading" ? "Checking..." : "Continue"}
            </button>
        </div>
    {/if}
{/if}
