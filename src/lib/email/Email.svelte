<script lang="ts">
    import type {Authentikt} from "$lib/AuthentiktConfiguration";

    let {
        authentikt,
    }: {
        authentikt: Authentikt,
    } = $props();

    let email = $state("admin@acme.com");
    let result: null | "user_not_existing" = $state(null);

    async function submit() {
        result = null
        const emailResult = await authentikt.useEmail(email);
        if (emailResult === "not-existing") result = "user_not_existing";
    }
</script>

<div class="flex flex-col gap-2">
    <input
            type="email"
            placeholder="Email"
            bind:value={email}
    />

    <button onclick={submit}>weiter mit {email}</button>

    {#if result === "user_not_existing"}
        <span class="text-red-400">User existiert nicht</span>
    {/if}
</div>
