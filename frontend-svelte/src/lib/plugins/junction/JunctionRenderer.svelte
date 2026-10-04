<script lang="ts">
    /**
     * Junction step renderer.
     *
     * Self-registers as the `"authentikt-builtin/junction"` step plugin on mount and lets the user choose
     * which step to take next. Labels are looked up by namespace via `label`; without it, the namespace
     * itself is shown. Pass `namespace` when the server installs the junction under a different namespace.
     */
    import { useAuthentiktContext } from "$lib/context";
    import { JunctionPlugin } from "./JunctionPlugin.svelte";
    import type { JunctionPluginInstance, JunctionSnippet } from "./types";
    import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

    let {
        children,
        plugin: externalPlugin,
        user: _user,
        namespace = "authentikt-builtin/junction",
        label = (namespace: string) => namespace,
    }: {
        children?: JunctionSnippet;
        plugin?: JunctionPluginInstance;
        user?: FlowUserState | null;
        namespace?: string;
        label?: (namespace: string) => string;
    } = $props();

    const authentikt = useAuthentiktContext();

    // The plugin registers once on mount, later namespace changes are not supported
    // svelte-ignore state_referenced_locally
    const selfPlugin = authentikt.registerPlugin<JunctionPluginInstance>(
        namespace,
        JunctionRenderer,
        (auth, ns) => new JunctionPlugin(auth, ns)
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);
</script>

<script lang="ts" module>
    import JunctionRenderer from "./JunctionRenderer.svelte";
</script>

{#if plugin.isActive}
    {#if children}
        {@render children(plugin)}
    {:else}
        <div class="flex flex-col gap-2">
            {#each plugin.options as option (option)}
                <button
                    onclick={() => plugin.select(option)}
                    disabled={plugin.status === "loading"}
                    class="border p-2 rounded disabled:opacity-50"
                >
                    {label(option)}
                </button>
            {/each}
        </div>
    {/if}
{/if}
