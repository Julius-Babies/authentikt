<script lang="ts">
    import { DonePlugin } from "./DonePlugin.svelte";
    import { useAuthentiktContext } from "$lib/context";
    import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

    let {
        plugin: externalPlugin,
        user: _user,
    }: {
        plugin?: DonePlugin;
        user?: FlowUserState | null;
    } = $props();

    const authentikt = useAuthentiktContext();
    const namespace = "authentikt-builtin/done";

    const selfPlugin = authentikt.registerStepPlugin<DonePlugin>(
        namespace,
        DoneRenderer,
        (auth, ns) => new DonePlugin(auth, ns)
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);

    $effect(() => {
        if (!plugin.isActive) return;
        void plugin.complete();
    });
</script>

<script lang="ts" module>
    import DoneRenderer from "./DoneRenderer.svelte";
</script>

{#if plugin.isActive}
    <div class="flex flex-col items-center gap-4">
        <span class="text-green-600 font-bold">Successfully authenticated!</span>
        <p>You are being redirected...</p>
    </div>
{/if}
