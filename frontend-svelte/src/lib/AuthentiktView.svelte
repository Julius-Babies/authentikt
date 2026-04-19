<script lang="ts">
    import Email from "$lib/email/Email.svelte";
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
</script>

{#if $currentFlow?.step?.type === "user_selection" && $currentFlow.step.email.enabled}
    <Email {authentikt} />
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
