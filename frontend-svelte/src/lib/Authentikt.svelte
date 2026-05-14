<script lang="ts">
    import { setAuthentiktContext } from "./context";
    import { Authentikt, type AuthentiktConfiguration } from "./AuthentiktConfiguration.svelte";
    import type { Snippet } from "svelte";
    import {AuthentiktDebug} from "$lib";

    /**
     * Props for the `<Authentikt>` provider component.
     */
    let {
        baseUrl,
        authentikt_debug = false,
        children
    }: AuthentiktConfiguration & {
        children?: Snippet;
    } = $props();

    function correctBaseUrl(baseUrl: string) {
        if (baseUrl.endsWith("authentikt/")) return baseUrl;
        if (authentikt_debug) console.warn("The base URL is not correctly formatted. It should end with authentikt/. Automatically correcting it, but please update the base url to avoid this warning. This warning is only shown in debug mode.");
        if (baseUrl.endsWith("/")) return baseUrl + "authentikt/";
        return baseUrl + "/authentikt/";
    }

    const correctedBaseUrl = $derived(correctBaseUrl(baseUrl));

    const instance = new Authentikt({
        get baseUrl() { return correctedBaseUrl },
        get authentikt_debug() { return authentikt_debug }
    });
    setAuthentiktContext(instance);
</script>

{@render children?.()}

{#if authentikt_debug}
    <AuthentiktDebug authentikt={instance} />
{/if}
