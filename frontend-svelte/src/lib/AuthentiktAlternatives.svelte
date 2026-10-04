<script lang="ts">
    /**
     * Lists the steps the user can take instead of the active step.
     *
     * Place it below the step renderers. Renders nothing if the active step has no alternatives.
     * Labels are looked up by namespace via `label`; without it, the namespace itself is shown.
     */
    import { useAuthentiktContext } from "./context";
    import type { AlternativeOption, AlternativesSnippet } from "./AuthentiktAlternatives.types";

    let {
        children,
        label = (namespace: string) => namespace,
    }: {
        children?: AlternativesSnippet;
        label?: (namespace: string) => string;
    } = $props();

    const authentikt = useAuthentiktContext();

    const options: AlternativeOption[] = $derived(authentikt.alternatives.map(namespace => ({
        namespace,
        label: label(namespace),
        select: () => authentikt.switchToAlternative(namespace),
    })));
</script>

{#if options.length > 0}
    {#if children}
        {@render children(options)}
    {:else}
        <div class="flex flex-col gap-2 mt-4">
            <span class="text-sm text-gray-500">Other options</span>
            {#each options as option (option.namespace)}
                <button onclick={option.select} class="border p-2 rounded text-sm">
                    {option.label}
                </button>
            {/each}
        </div>
    {/if}
{/if}
