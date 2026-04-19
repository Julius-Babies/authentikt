<script lang="ts">
    import { useAuthentiktContext } from "$lib/context";
    import type { PasswordPlugin } from "$lib/plugins/password/PasswordPlugin";
    import type { PasswordSnippet, PasswordStatus } from "$lib/plugins/password/types";

    let {
        plugin,
        children
    }: {
        plugin: PasswordPlugin;
        children?: PasswordSnippet;
    } = $props();

    const authentikt = useAuthentiktContext();
    const currentFlow = authentikt.currentFlow;

    let password = $state("password");
    let status = $state<PasswordStatus>("ready");

    const isActive = $derived(
        $currentFlow?.step?.type === "step" &&
        $currentFlow.step.namespace === "authentikt-builtin/password"
    );

    async function submit() {
        status = "loading";
        try {
            const result = await plugin.login(password);
            if (result === "wrong") status = "password_incorrect";
        } catch(e) {
            console.error(e);
            status = "error";
        }
    }

    function setPassword(value: string) {
        password = value;
    }
</script>

{#if isActive}
    {#if children}
        {@render children(password, status, submit, setPassword)}
    {:else}
        <div class="flex flex-col gap-2">
            <input type="password" placeholder="Password" bind:value={password} />
            {#if status === "password_incorrect"}
                <span class="text-red-400">Password incorrect</span>
            {/if}
            <button onclick={submit} disabled={status === "loading"}>
                {status === "loading" ? "Checking..." : "Continue"}
            </button>
        </div>
    {/if}
{/if}
