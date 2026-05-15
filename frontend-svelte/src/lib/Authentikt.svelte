<script lang="ts">
    import { setAuthentiktContext } from "./context";
    import { Authentikt, type AuthentiktConfiguration } from "./AuthentiktConfiguration.svelte";
    import {type Snippet, tick} from "svelte";
    import {AuthentiktDebug} from "$lib";

    /**
     * Props for the `<Authentikt>` provider component.
     */
    let {
        config,
        children
    }: {
        config: AuthentiktConfiguration;
        children?: Snippet;
    } = $props();

    function correctBaseUrl(baseUrl: string) {
        if (baseUrl.endsWith("authentikt/")) return baseUrl;
        if (config.debug) console.warn("The base URL is not correctly formatted. It should end with authentikt/. Automatically correcting it, but please update the base url to avoid this warning. This warning is only shown in debug mode.");
        if (baseUrl.endsWith("/")) return baseUrl + "authentikt/";
        return baseUrl + "/authentikt/";
    }

    let isReady = $state(false);
    const correctedBaseUrl = $derived(correctBaseUrl(config.baseUrl));

    const instance = $derived(new Authentikt({...config, baseUrl: correctedBaseUrl}));
    $effect(() => {
        setAuthentiktContext(instance);
        tick().then(() => isReady = true);
    });

    const showOverlay = $derived.by(() => {
        if (typeof config.debug === 'boolean') {
            return config.debug;
        }
        return !!config.debug?.show_overlay;
    })
</script>

{#if isReady}
    {@render children?.()}
{/if}

{#if showOverlay}
    <AuthentiktDebug authentikt={instance} />
{/if}
