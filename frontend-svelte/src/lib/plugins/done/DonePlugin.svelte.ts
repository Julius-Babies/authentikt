import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";

export class DonePlugin {
    private isCompleting = false;

    constructor(
        private readonly authentikt: Authentikt,
        private readonly namespace: string,
    ) {}

    get isActive(): boolean {
        return this.authentikt.currentFlow?.step?.type === "step" &&
            this.authentikt.currentFlow.step.namespace === this.namespace;
    }

    complete = async (): Promise<void> => {
        if (this.isCompleting) return;
        this.isCompleting = true;

        try {
            const url = new URL("steps/plugins/" + this.namespace, this.authentikt.sessionUrl);
            const response = await fetch(url.toString());
            if (!response.ok) return;

            await this.authentikt.cancelFlow();
            window.location.reload();
        } finally {
            this.isCompleting = false;
        }
    };
}
