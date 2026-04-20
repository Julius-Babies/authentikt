<script lang="ts">
    import { setAuthentiktContext } from "./context";
    import { Authentikt, type AuthentiktConfiguration } from "./AuthentiktConfiguration.svelte";
    import type { Snippet } from "svelte";

    let {
        baseUrl,
        authentikt_debug = false,
        children
    }: AuthentiktConfiguration & {
        children?: Snippet;
    } = $props();

    const instance = new Authentikt({ 
        get baseUrl() { return baseUrl }, 
        get authentikt_debug() { return authentikt_debug } 
    });
    setAuthentiktContext(instance);
</script>

{@render children?.()}
