<script lang="ts">
    import type {Authentikt} from "$lib/AuthentiktConfiguration";
    import {JsonView} from "@zerodevx/svelte-json-view";
    import AuthentiktDebug from "$lib/AuthentiktDebug.svelte";

    let {
        authentikt,
    }: {
        authentikt: Authentikt;
    } = $props();
</script>

{#if authentikt.configuration.authentikt_debug}
    <AuthentiktDebug {authentikt} />
{/if}