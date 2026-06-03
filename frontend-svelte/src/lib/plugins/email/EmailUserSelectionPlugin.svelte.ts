import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";
import type { EmailUserSelectionPayload, EmailUserSelectionStatus } from "./types";

export class EmailUserSelectionPlugin {
    email = $state("");
    status = $state<EmailUserSelectionStatus>("ready");

    private readonly _ns: string;
    private readonly authentikt: Authentikt;

    constructor(
        authentikt: Authentikt,
        namespace: string,
    ) {
        this.authentikt = authentikt;
        this._ns = namespace;
    }

    get namespace(): string {
        return this._ns;
    }

    get typedPayload(): EmailUserSelectionPayload {
        const step = this.authentikt.currentFlow?.step;
        if (step?.type !== "step") return { with_username: false };
        return {
            with_username: (step.payload as Record<string, unknown>)?.with_username === true,
        };
    }

    get isActive(): boolean {
        return this.authentikt.currentFlow?.step?.type === "step" &&
            this.authentikt.currentFlow.step.namespace === this._ns;
    }

    submit = async (): Promise<void> => {
        this.status = "loading";
        try {
            const url = new URL("steps/plugins/" + this._ns, this.authentikt.sessionUrl);
            const response = await fetch(url.toString(), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email: this.email }),
            });
            const data = await response.json();

            if (data.type === "success") {
                this.authentikt.setUser({
                    username: data.username,
                    displayName: data.display_name,
                });
                await this.authentikt.updateState();
                this.status = "ready";
            } else {
                this.status = "user_not_existing";
            }
        } catch (e) {
            console.error(e);
            this.status = "error";
        }
    };
}
