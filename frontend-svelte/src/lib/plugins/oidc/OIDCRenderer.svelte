<script lang="ts">
    import { OIDCPlugin } from "./OIDCPlugin.svelte";
    import { useAuthentiktContext } from "$lib/context";
    import type { OIDCPluginInstance, OIDCSnippet } from "./types";
    import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

    let {
        children,
        plugin: externalPlugin,
        user: _user,
    }: {
        children?: OIDCSnippet;
        plugin?: OIDCPluginInstance;
        user?: FlowUserState | null;
    } = $props();

    const authentikt = useAuthentiktContext();
    const namespace = "authentikt-builtin/oidc";

    const selfPlugin = authentikt.registerPlugin<OIDCPluginInstance>(
        namespace,
        OIDCRenderer,
        (auth, ns) => new OIDCPlugin(auth, ns)
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);

    $effect(() => {
        if (!plugin.isActive) return;
        plugin.redirect();
    });
</script>

<script lang="ts" module>
    import OIDCRenderer from "./OIDCRenderer.svelte";
</script>

{#if plugin.isActive}
    {#if children}
        {@render children(plugin)}
    {:else}
        <div class="flex flex-col items-center gap-4">
            <span>Redirecting to identity provider...</span>
        </div>
    {/if}
{/if}
