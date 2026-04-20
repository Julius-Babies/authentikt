import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";
import type { PasswordStatus } from "./types";

export class PasswordPlugin {
    password = $state("");
    status = $state<PasswordStatus>("ready");

    constructor(
        private readonly authentikt: Authentikt,
        private readonly namespace: string,
    ) {}

    get isActive(): boolean {
        return this.authentikt.currentFlow?.step?.type === "step" &&
            this.authentikt.currentFlow.step.namespace === this.namespace;
    }

    submit = async (): Promise<void> => {
        this.status = "loading";
        try {
            const url = new URL("steps/plugins/" + this.namespace, this.authentikt.sessionUrl);
            const response = await fetch(url.toString(), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ password: this.password }),
            });
            const data = await response.json();

            if (data.success === true) {
                await this.authentikt.updateState();
                this.status = "ready";
            } else {
                this.status = "password_incorrect";
            }
        } catch (e) {
            console.error(e);
            this.status = "error";
        }
    };
}
