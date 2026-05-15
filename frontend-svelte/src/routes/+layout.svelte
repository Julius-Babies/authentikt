<script lang="ts">
    import "./layout.css";
    import {
        Authentikt, EmailUserSelectionRenderer, PasswordRenderer, TotpRenderer, DoneRenderer, useAuthentiktContext,
        type AuthentiktConfiguration
    } from "$lib"

    import {Loader, X} from "@lucide/svelte";
    import {Button} from "$lib/components/ui/button";
    import {fade, fly} from "svelte/transition";
    import {quadOut} from "svelte/easing";
    import {currentUser} from "$lib/user";
    import {onMount} from "svelte";
    import update_user from "./update_user";

    const { children } = $props();

    onMount(() => {
        update_user();
    });

    async function logout() {
        await fetch("http://localhost:8080/logout", {
            method: "POST",
            credentials: "include",
        });
        update_user();
    }

    const config: AuthentiktConfiguration = {
        baseUrl: "http://localhost:8080/authentikt/",
        debug: {
            show_overlay: true,
        },
    }
</script>

<div class="min-w-screen min-h-screen">
    <Authentikt
            config={config}
    >
        {@const authentikt = useAuthentiktContext()}

        {#if $currentUser === null}
            <div
                    transition:fade={{ duration: 50 }}
                    class="absolute top-0 left-0 flex w-full h-full z-20 items-center justify-center bg-background/75"
            >
                <div class="animate-spin w-8 h-8">
                    <Loader class="w-full h-full" />
                </div>
            </div>
        {/if}

        {#if $currentUser === "anonymous" && $currentUser !== null}
            <div class="p-4">
                <Button onclick={authentikt.startLoginFlow}>Login</Button>
            </div>
        {/if}

        {#if $currentUser && $currentUser !== "anonymous"}
            <div class="p-4 flex items-center gap-4">
                <span>Angemeldet als <strong>{$currentUser.displayName}</strong></span>
                <Button variant="outline" size="sm" onclick={logout}>Logout</Button>
            </div>
        {/if}

        {#if authentikt.currentFlow}
            <div
                    transition:fly={{ duration: 250, y: 500, easing: quadOut }}
                    class="fixed inset-0 flex items-center justify-center bg-background/75 backdrop-blur-sm z-50"
            >
                <Button
                    variant="ghost"
                    size="icon"
                    class="fixed right-4 top-4 z-60 rounded-full bg-black/50 text-white hover:bg-black/70"
                    onclick={authentikt.cancelFlow}
                >
                    <X class="w-6 h-6" />
                </Button>

                <div class="relative w-full max-w-md p-4">
                    <EmailUserSelectionRenderer />

                    <PasswordRenderer>
                        {#snippet children(plugin)}
                            <div class="flex w-full flex-col gap-4 rounded-lg border bg-white p-6 shadow-xl">
                                <h2 class="text-xl font-bold">Welcome back</h2>
                                <p class="text-sm text-muted-foreground">Custom password UI example.</p>
                                {#if authentikt.currentFlow?.attributes?.auth_id}
                                    <p class="text-xs text-gray-400">Session: {authentikt.currentFlow.attributes.auth_id}</p>
                                {/if}
                                <input
                                    class="rounded border px-3 py-2"
                                    type="password"
                                    placeholder="Password"
                                    bind:value={plugin.password}
                                />
                                {#if plugin.status === "password_incorrect"}
                                    <p class="text-sm text-red-600">Incorrect password. Please try again.</p>
                                {/if}
                                <Button onclick={plugin.submit} disabled={plugin.status === "loading"}>
                                    {plugin.status === "loading" ? "Checking..." : "Continue"}
                                </Button>
                            </div>
                        {/snippet}
                    </PasswordRenderer>

                    <TotpRenderer />

                    <DoneRenderer />
                </div>
            </div>
        {/if}

        {@render children()}
    </Authentikt>
</div>
