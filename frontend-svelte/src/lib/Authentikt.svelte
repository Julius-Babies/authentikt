<script lang="ts">
    import type { Authentikt as AuthentiktInstance } from "$lib/AuthentiktConfiguration";
    import AuthentiktDebug from "$lib/AuthentiktDebug.svelte";
    import { setAuthentiktContext } from "$lib/context";

    const props = $props<{
        instance: AuthentiktInstance;
        children?: import("svelte").Snippet;
    }>();

    const instance = props.instance;
    const currentFlow = instance.currentFlow;
    setAuthentiktContext(instance);
</script>

{#if instance.configuration.authentiktDebug}
    <AuthentiktDebug authentikt={instance} />
{/if}

{#if $currentFlow}
    <div class="w-full h-full relative">
        {@render props.children?.()}
    </div>
{/if}
