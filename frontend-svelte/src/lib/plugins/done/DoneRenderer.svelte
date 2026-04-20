<script lang="ts">
    import { DonePlugin } from "./DonePlugin.svelte";
    import { useAuthentiktContext } from "$lib/context";

    const authentikt = useAuthentiktContext();
    const namespace = "authentikt-builtin/done";

    const plugin = authentikt.linkStepPlugin(namespace, DoneRenderer, () => new DonePlugin(authentikt, namespace));

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
