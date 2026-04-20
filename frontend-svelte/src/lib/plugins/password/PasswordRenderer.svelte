<script lang="ts">
    import { useAuthentiktContext } from "$lib/context";
    import { PasswordPlugin } from "./PasswordPlugin.svelte";
    import type { PasswordPluginInstance, PasswordSnippet } from "./types";

    let { children }: { children?: PasswordSnippet } = $props();

    const authentikt = useAuthentiktContext();
    const namespace = "authentikt-builtin/password";

    const plugin = authentikt.linkStepPlugin<PasswordPluginInstance>(
        namespace,
        PasswordRenderer,
        () => new PasswordPlugin(authentikt, namespace)
    );
</script>

<script lang="ts" module>
    import PasswordRenderer from "./PasswordRenderer.svelte";
</script>

{#if plugin.isActive}
    {#if children}
        {@render children(plugin)}
    {:else}
        <div class="flex flex-col gap-2">
            <input 
                type="password" 
                placeholder="Password" 
                bind:value={plugin.password}
                class="border p-2 rounded" 
            />
            {#if plugin.status === "password_incorrect"}
                <span class="text-red-400 text-sm">Password incorrect</span>
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
