import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";

export class OIDCPlugin {
    private readonly _ns: string;
    private readonly authentikt: Authentikt;
    private redirectAttempted = false;

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

    get authorizeUrl(): string | undefined {
        const step = this.authentikt.currentFlow?.step;
        if (step?.type !== "step") return undefined;
        return step.payload?.authorize_url as string | undefined;
    }

    redirect = (): void => {
        if (this.redirectAttempted || !this.authorizeUrl) return;
        this.redirectAttempted = true;
        window.location.href = this.authorizeUrl;
    }
}
