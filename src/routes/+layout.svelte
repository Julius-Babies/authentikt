<script lang="ts">
    import './layout.css';
    import {createAuthentikt} from "$lib/AuthentiktConfiguration";
    import Authentikt from "$lib/Authentikt.svelte";
    import {X} from '@lucide/svelte';
    import {Button} from "$lib/components/ui/button";
    import { fly } from 'svelte/transition';
    import {quintOut} from "svelte/easing";

    const {children} = $props();

    const authentikt = createAuthentikt({
        baseUrl: "http://localhost:8080/authentikt/",
        authentikt_debug: true,
    })

    const user = authentikt.user
    const currentFlow = authentikt.currentFlow

    $inspect($user)
</script>

{#if !$user}
    <button onclick={authentikt.startLoginFlow}>login</button>
{/if}

{#if $currentFlow}
    <div
            transition:fly={{duration: 100, y: 200, opacity: 0.5, easing: quintOut}}
            class="absolute top-0 left-0 w-full h-full backdrop-blur-sm bg-background/75"
    >
        <div class="absolute top-0 right-0 p-4">
            <Button variant="ghost" size="icon" onclick={authentikt.cancelFlow}>
                <X/>
            </Button>
        </div>
        <Authentikt {authentikt}>

        </Authentikt>
    </div>
{/if}

{@render children()}
