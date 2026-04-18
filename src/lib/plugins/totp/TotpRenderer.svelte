<script lang="ts">
    import {useAuthentiktContext} from "$lib/context";
    import type {TotpPlugin} from "$lib/plugins/totp/TotpPlugin";
    import type {TotpSnippet, TotpStatus} from "$lib/plugins/totp/types";

    let {
        plugin,
        children
    }: {
        plugin: TotpPlugin;
        children?: TotpSnippet;
    } = $props();

    const authentikt = useAuthentiktContext();
    const currentFlow = authentikt.currentFlow;

    let totp = $state("");
    let status = $state<TotpStatus>("ready");

    const isActive = $derived(
        $currentFlow?.step?.type === "step" &&
        $currentFlow.step.namespace === plugin.namespace
    );

    async function submit() {
        status = "loading";
        try {
            const result = await plugin.useTotp(totp);
            if (result === "wrong") status = "totp_incorrect";
        } catch(e) {
            console.error(e);
            status = "error";
        }
    }

    function updateTotp(value: string) {
        totp = value;
    }
</script>

{#if isActive}
    {#if children}
        {@render children(totp, status, submit, updateTotp)}
    {:else}
        <div class="flex flex-col gap-2">
            <input type="text" placeholder="TOTP" bind:value={totp} />
            {#if status === "totp_incorrect"}
                <span class="text-red-400">TOTP incorrect</span>
            {/if}
            <button onclick={submit} disabled={status === "loading"}>
                {status === "loading" ? "Checking..." : "Continue"}
            </button>
        </div>
    {/if}
{/if}
