<script lang="ts">
    import "./layout.css";
    import { createAuthentikt } from "$lib/AuthentiktConfiguration";
    import AuthentiktView from "$lib/AuthentiktView.svelte";
    import Authentikt from "$lib/Authentikt.svelte";
    import { X } from "@lucide/svelte";
    import { Button } from "$lib/components/ui/button";
    import { fly } from "svelte/transition";
    import { quintOut } from "svelte/easing";
    import { PasswordPlugin } from "$lib/plugins/password/PasswordPlugin";

    const { children } = $props();
    const passwordPlugin = new PasswordPlugin();

    const authentikt = createAuthentikt({
        baseUrl: "http://localhost:8080/authentikt/",
        authentikt_debug: true,
        plugins: [passwordPlugin]
    });

    const user = authentikt.user;
    const currentFlow = authentikt.currentFlow;
</script>

{#if !$user}
    <button onclick={authentikt.startLoginFlow}>login</button>
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
