import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";
import type { JunctionStatus } from "./types";

export class JunctionPlugin {
    status = $state<JunctionStatus>("ready");

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

    get options(): string[] {
        const step = this.authentikt.currentFlow?.step;
        if (step?.type !== "step") return [];
        return (step.payload?.options as string[] | undefined) ?? [];
    }

    select = async (namespace: string): Promise<void> => {
        this.status = "loading";
        try {
            const url = new URL("steps/plugins/" + this._ns, this.authentikt.sessionUrl);
            await fetch(url.toString(), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ namespace }),
            });
            // On 409 the junction is no longer active, e.g. a duplicate submission. Load the current step either way.
            await this.authentikt.updateState();
            this.status = "ready";
        } catch (e) {
            console.error(e);
            this.status = "error";
        }
    };
}
