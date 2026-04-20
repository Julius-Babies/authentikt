<script lang="ts">
    import { useAuthentiktContext } from "$lib/context";
    import type { EmailUserSelectionPlugin } from "$lib/user-selection/plugins/email/EmailUserSelectionPlugin";
    import type {
        EmailUserSelectionPayload,
        EmailUserSelectionSnippet,
        EmailUserSelectionStatus,
    } from "$lib/user-selection/plugins/email/types";

    let {
        plugin,
        payload,
        children,
    }: {
        plugin: EmailUserSelectionPlugin;
        payload?: Record<string, unknown>;
        children?: EmailUserSelectionSnippet;
    } = $props();

    const authentikt = useAuthentiktContext();
    const currentFlow = authentikt.currentFlow;

    let email = $state("admin@acme.com");
    let status = $state<EmailUserSelectionStatus>("ready");

    const typedPayload = $derived<EmailUserSelectionPayload>({
        with_username: payload?.with_username === true,
    });

    const isActive = $derived(
        $currentFlow?.step?.type === "user_selection" &&
        $currentFlow.step.plugins.some((candidate) => candidate.namespace === plugin.namespace)
    );

    async function submit() {
        status = "loading";
        try {
            const result = await plugin.selectUserByEmail(email);
            if (result === "not-existing") {
                status = "user_not_existing";
            }
        } catch (error) {
            console.error(error);
            status = "error";
        }
    }

    function updateEmail(value: string) {
        email = value;
    }
</script>

{#if isActive}
    {#if children}
        {@render children(email, status, submit, updateEmail, typedPayload)}
    {:else}
        <div class="flex flex-col gap-2">
            <input type="email" placeholder="Email" bind:value={email} />
            {#if status === "user_not_existing"}
                <span class="text-red-400">User does not exist</span>
            {/if}
            <button onclick={submit} disabled={status === "loading"}>
                {status === "loading" ? "Checking..." : "Continue"}
            </button>
        </div>
    {/if}
{/if}
