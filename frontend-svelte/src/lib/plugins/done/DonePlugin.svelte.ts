import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";

export class DonePlugin {
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
        if (this.isCompleting) return;
        this.isCompleting = true;

        try {
            const url = new URL("steps/plugins/" + this._ns, this.authentikt.sessionUrl);
            const response = await fetch(url.toString());
            if (!response.ok) return;

            await this.authentikt.cancelFlow();
            window.location.reload();
        } finally {
            this.isCompleting = false;
        }
    };
}
