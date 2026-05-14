import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";
import type { DoneResult } from "./types";

export class DonePlugin {
    result = $state<DoneResult>(null);
    private isCompleting = false;
    private readonly _ns: string;
    private readonly authentikt: Authentikt;

    constructor(authentikt: Authentikt, namespace: string) {
        this.authentikt = authentikt;
        this._ns = namespace;
    }

    get namespace(): string {
        return this._ns;
    }

    get isActive(): boolean {
        return this.authentikt.currentFlow?.step?.type === "step" &&
            this.authentikt.currentFlow.step.namespace === this._ns;
    }

    complete = async (): Promise<void> => {
        if (this.isCompleting || this.result !== null) return;
        this.isCompleting = true;

        try {
            const url = new URL("steps/plugins/" + this._ns, this.authentikt.sessionUrl);
            const response = await fetch(url.toString());
            if (!response.ok) return;

            const data = await response.json();
            this.result = data as DoneResult;
        } finally {
            this.isCompleting = false;
        }
    };
}
