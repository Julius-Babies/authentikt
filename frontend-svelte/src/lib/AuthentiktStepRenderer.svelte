<script lang="ts">
    /**
     * Auto-renders the currently active step plugin.
     *
     * Looks up the registered plugin matching the server's current step namespace,
     * instantiates it if needed, and renders its component with the plugin instance
     * and session user as props.
     *
     * Requires that step plugins have been registered via `authentikt.registerStepPlugin()`
     * (either by mounting premade renderers or by calling the method directly).
     */
    import { useAuthentiktContext } from "$lib/context";

    const authentikt = useAuthentiktContext();

    const activeEntry = $derived(authentikt.activeStepEntry);
    const plugin = $derived(authentikt.activeStepPlugin);
    const user = $derived(authentikt.currentFlow?.user ?? null);
</script>

{#if activeEntry && plugin}
    {@const Renderer = activeEntry.component}
    <Renderer {plugin} {user} />
{/if}
