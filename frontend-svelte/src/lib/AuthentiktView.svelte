<script lang="ts">
    import { useAuthentiktContext } from "$lib/context";

    const authentikt = useAuthentiktContext();

    const activeEntries = $derived(authentikt.activeUserSelectionEntries);
    const activeEntry = $derived(authentikt.activeStepEntry);
    const plugin = $derived(authentikt.activeStepPlugin);
    const user = $derived(authentikt.currentFlow?.user ?? null);
</script>

{#if activeEntries.length > 0}
    <div class="authentikt-user-selection-view">
        {#each activeEntries as { entry, plugin, payload } (entry.namespace)}
            {@const Renderer = entry.component}
            <Renderer {plugin} {payload} {user} />
        {/each}
    </div>
{:else if activeEntry && plugin}
    <div class="authentikt-step-view">
        {#key activeEntry.namespace}
            {@const Renderer = activeEntry.component}
            <Renderer {plugin} {user} />
        {/key}
    </div>
{/if}
