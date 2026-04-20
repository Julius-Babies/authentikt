import { getContext, setContext } from "svelte";
import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";

const AUTHENTIKT_CONTEXT = Symbol("authentikt-context");

export function setAuthentiktContext(instance: Authentikt): void {
    setContext(AUTHENTIKT_CONTEXT, instance);
}

export function useAuthentiktContext(): Authentikt {
    const instance = getContext<Authentikt | undefined>(AUTHENTIKT_CONTEXT);
    if (!instance) {
        throw new Error("Authentikt context was not found. Wrap this component in <Authentikt instance={...}>.");
    }

    return instance;
}
