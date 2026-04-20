<script lang="ts">
    import { useAuthentiktContext } from "$lib/context";

    const authentikt = useAuthentiktContext();
    const currentFlow = $derived(authentikt.currentFlow);

    const activePlugin = $derived.by(() => {
        const step = currentFlow?.step;
        if (!step || step.type !== "step") return null;
        return (
            authentikt.plugins.find((p) => p.namespace === step.namespace) ?? {
                namespace: step.namespace,
                component: null,
            }
        );
    });

    const activeUserSelections = $derived.by(() => {
        const step = currentFlow?.step;
        if (!step || step.type !== "user_selection") return [];

        return step.plugins.map((candidate) => {
            const pluginEntry = authentikt.userSelectionPlugins.find(
                (p) => p.namespace === candidate.namespace
            );
            return {
                namespace: candidate.namespace,
                component: pluginEntry?.component ?? null,
                payload: candidate.payload,
            };
        });
    });

    $effect(() => {
        if (authentikt.debug) {
            console.log("AuthentiktView State:", {
                currentFlow,
                activeUserSelections,
                activePlugin,
                registeredPlugins: authentikt.plugins.map(p => p.namespace),
                registeredUserSelectionPlugins: authentikt.userSelectionPlugins.map(p => p.namespace)
            });
        }
    });
</script>

{#if currentFlow?.step?.type === "user_selection"}
    <div class="authentikt-user-selection-view">
        {#each activeUserSelections as entry (entry.namespace)}
            {#if entry.component}
                {@const Renderer = entry.component}
                <Renderer payload={entry.payload} />
            {/if}
        {/each}
    </div>
{:else if currentFlow?.step?.type === "step"}
    <div class="authentikt-step-view">
        {#if activePlugin?.component}
            {#key activePlugin.namespace}
                {@const Renderer = activePlugin.component}
                <Renderer />
            {/key}
        {/if}
    </div>
{/if}
