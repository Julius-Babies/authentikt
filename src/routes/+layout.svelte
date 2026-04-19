<script lang="ts">
    import "./layout.css";
    import {createAuthentikt} from "$lib/AuthentiktConfiguration";
    import AuthentiktView from "$lib/AuthentiktView.svelte";
    import Authentikt from "$lib/Authentikt.svelte";
    import {Loader, X} from "@lucide/svelte";
    import {Button} from "$lib/components/ui/button";
    import {fade, fly} from "svelte/transition";
    import {quintOut} from "svelte/easing";
    import {PasswordPlugin} from "$lib/plugins/password/PasswordPlugin";
    import {TotpPlugin} from "$lib/plugins/totp/TotpPlugin";
    import {DonePlugin} from "$lib/plugins/done/DonePlugin";
    import {currentUser} from "$lib/user";
    import {onMount} from "svelte";
    import update_user from "./update_user";

    const { children } = $props();
    const passwordPlugin = new PasswordPlugin();
    const totpPlugin = new TotpPlugin();
    const donePlugin = new DonePlugin(update_user);

    const authentikt = createAuthentikt({
        baseUrl: "http://localhost:8080/authentikt/",
        authentikt_debug: true,
        plugins: [passwordPlugin, totpPlugin, donePlugin]
    });

    const currentFlow = authentikt.currentFlow;

    onMount(() => {
        update_user();
    })

    async function logout() {
        await fetch("http://localhost:8080/logout", {
            method: "POST",
            credentials: "include",
        });
        update_user();
    }
</script>

<div class="min-w-screen min-h-screen">
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
        <button onclick={authentikt.startLoginFlow}>login</button>
    {/if}

    {#if $currentUser && $currentUser !== "anonymous"}
        Angemeldet als {$currentUser.displayName}. <button onclick={logout}>logout</button>
    {/if}

    {#if $currentFlow}
        <div
                transition:fly={{ duration: 100, y: 200, opacity: 0.5, easing: quintOut }}
                class="absolute top-0 left-0 w-full h-full backdrop-blur-sm bg-background/75"
        >
            <Authentikt instance={authentikt}>
                <AuthentiktView>
                    <passwordPlugin.renderer plugin={passwordPlugin}>
                        {#snippet children(password, status, submit, setPassword)}
                            <div class="mx-auto mt-24 flex w-full max-w-sm flex-col gap-4 rounded-lg border bg-white p-4">
                                <h2 class="text-lg font-semibold">Welcome back</h2>
                                <input
                                        class="rounded border px-3 py-2"
                                        type="password"
                                        placeholder="Password"
                                        value={password}
                                        oninput={(event) => setPassword((event.currentTarget as HTMLInputElement).value)}
                                />
                                {#if status === "password_incorrect"}
                                    <p class="text-sm text-red-600">Incorrect password. Please try again.</p>
                                {/if}
                                <Button onclick={submit} disabled={status === "loading"}>
                                    {status === "loading" ? "Checking..." : "Continue"}
                                </Button>
                            </div>
                        {/snippet}
                    </passwordPlugin.renderer>

                    <totpPlugin.renderer plugin={totpPlugin} />
                </AuthentiktView>
            </Authentikt>

            <div class="absolute top-0 right-0 p-4">
                <Button variant="ghost" size="icon" onclick={authentikt.cancelFlow}>
                    <X />
                </Button>
            </div>
        </div>
    {/if}

    {@render children()}

</div>