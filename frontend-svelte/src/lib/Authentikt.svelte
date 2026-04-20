<script lang="ts">
    import type { Authentikt as AuthentiktInstance } from "$lib/AuthentiktConfiguration";
    import AuthentiktDebug from "$lib/AuthentiktDebug.svelte";
    import { setAuthentiktContext } from "$lib/context";

    const props = $props<{
        instance: AuthentiktInstance;
        children?: import("svelte").Snippet;
    }>();

    // Set context immediately during component initialization
    setAuthentiktContext(props.instance);

    const currentFlow = $derived(props.instance.currentFlow);
</script>

{#if props.instance.configuration.authentiktDebug}
    <AuthentiktDebug authentikt={props.instance} />
{/if}

{#if $currentFlow}
    <div class="w-full h-full relative">
        {@render props.children?.()}
    </div>
{/if}

{#if $currentFlow}
    <div class="w-full h-full relative">
        {@render props.children?.()}
    </div>
{/if}
