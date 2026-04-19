<script lang="ts">
    import {useAuthentiktContext} from "$lib/context";
    import type {DoneSnippet} from "$lib/plugins/done/types";
    import type {DonePlugin} from "$lib/plugins/done/DonePlugin";

    let {
        plugin,
        children,
    }: {
        plugin: DonePlugin,
        children?: DoneSnippet,
    } = $props();

    const authentikt = useAuthentiktContext();
    const currentFlow = authentikt.currentFlow;

    const isActive = $derived(
        $currentFlow?.step?.type === "step" &&
        $currentFlow.step.namespace === "authentikt-builtin/done"
    );

    $effect(() => {
        if (!isActive) return;

        plugin.saveToken()
    })
</script>

all done