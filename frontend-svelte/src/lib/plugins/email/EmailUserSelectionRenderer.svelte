<script lang="ts">
    import { useAuthentiktContext } from "$lib/context";
    import { EmailUserSelectionPlugin } from "./EmailUserSelectionPlugin.svelte";
    import type {
        EmailUserSelectionPluginInstance,
        EmailUserSelectionSnippet,
    } from "./types";
    import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

    let {
        children,
        plugin: externalPlugin,
        user: _user,
    }: {
        children?: EmailUserSelectionSnippet;
        plugin?: EmailUserSelectionPluginInstance;
        user?: FlowUserState | null;
    } = $props();

    const authentikt = useAuthentiktContext();
    const namespace = "authentikt-builtin/email";

    const selfPlugin = authentikt.registerPlugin<EmailUserSelectionPluginInstance>(
        namespace,
        EmailUserSelectionRenderer,
        (auth, ns) => new EmailUserSelectionPlugin(auth, ns)
    );

    const plugin = $derived(externalPlugin ?? selfPlugin);
</script>

<script lang="ts" module>
    import EmailUserSelectionRenderer from "./EmailUserSelectionRenderer.svelte";
</script>

{#if plugin.isActive}
    {#if children}
        {@render children(plugin)}
    {:else}
        <div class="flex flex-col gap-2">
            <input
                type="email"
                placeholder="Email address"
                bind:value={plugin.email}
                class="border p-2 rounded"
            />
            {#if plugin.status === "user_not_existing"}
                <span class="text-red-400 text-sm">User does not exist</span>
            {/if}
            <button
                onclick={plugin.submit}
                disabled={plugin.status === "loading"}
                class="bg-blue-600 text-white p-2 rounded disabled:opacity-50"
            >
                {plugin.status === "loading" ? "Checking..." : "Continue"}
            </button>
        </div>
    {/if}
{/if}
