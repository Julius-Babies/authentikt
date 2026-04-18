<script lang="ts">
    import type {Authentikt} from "$lib/AuthentiktConfiguration";
    import AuthentiktDebug from "$lib/AuthentiktDebug.svelte";
    import Email from "$lib/email/Email.svelte";

    let {
        authentikt,
    }: {
        authentikt: Authentikt;
    } = $props();

    const currentState = $derived(authentikt.currentFlow)
</script>

{#if authentikt.configuration.authentikt_debug}
    <AuthentiktDebug {authentikt}/>
{/if}

{#if $currentState.step}
    <div class="w-full h-full relative">
        {#if $currentState.step.type === "user_selection"}
            {#if $currentState.step.email.enabled}
                <Email {authentikt}/>
            {/if}
        {/if}
    </div>
{/if}