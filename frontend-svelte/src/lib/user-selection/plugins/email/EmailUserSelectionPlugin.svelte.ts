import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";
import type { EmailUserSelectionPayload, EmailUserSelectionStatus } from "./types";

export class EmailUserSelectionPlugin {
    email = $state("");
    status = $state<EmailUserSelectionStatus>("ready");

    private readonly _ns: string;
    private readonly authentikt: Authentikt;
    private readonly readPayload: () => Record<string, unknown> | undefined;

    constructor(
        authentikt: Authentikt,
        namespace: string,
        readPayload: () => Record<string, unknown> | undefined,
    ) {
        this.authentikt = authentikt;
        this._ns = namespace;
        this.readPayload = readPayload;
    }

    get namespace(): string {
        return this._ns;
    }

    get typedPayload(): EmailUserSelectionPayload {
        return {
            with_username: this.readPayload()?.with_username === true,
        };
    }

    get isActive(): boolean {
        return this.authentikt.currentFlow?.step?.type === "user_selection" &&
            this.authentikt.currentFlow.step.plugins.some((candidate) => candidate.namespace === this._ns);
    }

    submit = async (): Promise<void> => {
        this.status = "loading";
        try {
            const url = new URL("user-selection/plugins/" + this._ns, this.authentikt.sessionUrl);
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
