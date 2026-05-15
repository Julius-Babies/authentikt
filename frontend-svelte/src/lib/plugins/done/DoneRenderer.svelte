<script lang="ts">
    import { DonePlugin } from "./DonePlugin.svelte";
    import { useAuthentiktContext } from "$lib/context";
    import type { DonePluginInstance, DoneSnippet } from "./types";
    import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

    let {
        children,
        plugin: externalPlugin,
        user: _user,
    }: {
        children?: DoneSnippet;
        plugin?: DonePluginInstance;
        user?: FlowUserState | null;
    } = $props();

    const authentikt = useAuthentiktContext();
    const namespace = "authentikt-builtin/done";

    const selfPlugin = authentikt.registerStepPlugin<DonePluginInstance>(
        namespace,
        DoneRenderer,
        (auth, ns) => new DonePlugin(auth, ns)
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);

    $effect(() => {
        if (!plugin.isActive) return;
        if (plugin.result !== null) return;
        void plugin.complete();
    });

    $effect(() => {
        const result = plugin.result;
        if (!result) return;

        const delay = result.type === "redirect" ? 1000 : 2000;

        const timeout = setTimeout(() => {
            void authentikt.cancelFlow();
            if (result.type === "redirect") {
                window.location.href = result.to;
            } else {
                window.location.reload();
            }
        }, delay);

        return () => clearTimeout(timeout);
    });
</script>

<script lang="ts" module>
    import DoneRenderer from "./DoneRenderer.svelte";
</script>

{#if plugin.isActive}
    {#if children}
        {@render children(plugin)}
    {:else if plugin.result === null}
        <div class="flex flex-col items-center gap-4">
            <span class="text-green-600 font-bold">Successfully authenticated!</span>
            <p>Completing authentication...</p>
        </div>
    {:else if plugin.result.type === "redirect"}
        <div class="flex flex-col items-center gap-4">
            <span class="text-green-600 font-bold">Successfully authenticated!</span>
            <p>Redirecting to <a href={plugin.result.to} class="underline">{plugin.result.to}</a> in 1 second...</p>
        </div>
    {:else}
        <div class="flex flex-col items-center gap-4">
            <span class="text-green-600 font-bold">Successfully authenticated!</span>
            <p>Completing...</p>
        </div>
    {/if}
{/if}
