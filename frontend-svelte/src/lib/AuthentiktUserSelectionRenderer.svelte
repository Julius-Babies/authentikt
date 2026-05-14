<script lang="ts">
    /**
     * Auto-renders all registered user-selection plugins for the current step.
     *
     * When the server reports a user_selection step, this component iterates over
     * each candidate plugin, looks it up in the registry, and renders its component
     * with the plugin instance, payload, and user context as props.
     *
     * Requires that user-selection plugins have been registered via
     * `authentikt.registerUserSelectionPlugin()`.
     */
    import { useAuthentiktContext } from "$lib/context";

    const authentikt = useAuthentiktContext();

    const activeEntries = $derived(authentikt.activeUserSelectionEntries);
    const user = $derived(authentikt.currentFlow?.user ?? null);
</script>

{#each activeEntries as { entry, plugin, payload } (entry.namespace)}
    {@const Renderer = entry.component}
    <Renderer {plugin} {payload} {user} />
{/each}
