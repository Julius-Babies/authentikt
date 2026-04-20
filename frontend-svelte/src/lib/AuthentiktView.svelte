<script lang="ts">
    import { useAuthentiktContext } from "$lib/context";

    let {
        children
    }: {
        children?: import("svelte").Snippet;
    } = $props();

    const authentikt = useAuthentiktContext();
    const currentFlow = authentikt.currentFlow;
    const activePlugin = $derived.by(() => {
        const step = $currentFlow?.step;
        if (!step || step.type !== "step") return null;
        return authentikt.configuration.installedPlugins.find((plugin) => plugin.namespace === step.namespace) ?? null;
    });
    const activeRenderer = $derived.by(() => activePlugin?.renderer ?? null);

    const activeUserSelections = $derived.by(() => {
        const step = $currentFlow?.step;
        if (!step || step.type !== "user_selection") return [];

        return step.plugins
            .flatMap((candidate) => {
                const plugin = authentikt.configuration.installedUserSelectionPlugins.find(
                    (installedPlugin) => installedPlugin.namespace === candidate.namespace
                );
                if (!plugin) return [];

                return [{
                    namespace: plugin.namespace,
                    plugin,
                    renderer: plugin.renderer,
                    payload: candidate.payload,
                }];
            });
    });
</script>

{#if $currentFlow?.step?.type === "user_selection"}
    {#if activeUserSelections.length > 0}
        {#each activeUserSelections as entry (entry.namespace)}
            {@const Renderer = entry.renderer}
            <Renderer plugin={entry.plugin} payload={entry.payload}>
                {@render children?.()}
            </Renderer>
        {/each}
    {/if}
{:else if $currentFlow?.step?.type === "step"}
    {#if activePlugin && activeRenderer}
        {#key activePlugin.namespace}
            {@const Renderer = activeRenderer}
            <Renderer plugin={activePlugin}>
            {@render children?.()}
            </Renderer>
        {/key}
    {/if}
{/if}
