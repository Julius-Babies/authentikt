import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";
import { RateLimitTracker, type RateLimitState } from "$lib/rate-limit.svelte";
import type { PasswordStatus } from "./types";

export class PasswordPlugin {
    password = $state("");
    status = $state<PasswordStatus>("ready");

    private readonly _ns: string;
    private readonly authentikt: Authentikt;
    private readonly rateLimitTracker: RateLimitTracker;

    constructor(authentikt: Authentikt, namespace: string) {
        this.authentikt = authentikt;
        this._ns = namespace;
        this.rateLimitTracker = new RateLimitTracker(authentikt, namespace);
    }

    /** Rate limit of failed attempts, or `null` if attempts are not limited. */
    get rateLimit(): RateLimitState | null {
        return this.rateLimitTracker.state;
    }

    get namespace(): string {
        return this._ns;
    }

    get isActive(): boolean {
        return this.authentikt.currentFlow?.step?.type === "step" &&
            this.authentikt.currentFlow.step.namespace === this._ns;
    }

    submit = async (): Promise<void> => {
        if (this.rateLimit?.isLocked) return;
        this.status = "loading";
        try {
            const url = new URL("steps/plugins/" + this._ns, this.authentikt.sessionUrl);
            const response = await fetch(url.toString(), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ password: this.password }),
            });
            if (response.status === 409) {
                // The step is no longer active, e.g. a duplicate submission. Load the current step instead.
                await this.authentikt.updateState();
                this.status = "ready";
                return;
            }
            if (response.status === 429) {
                // Too many failed attempts. Load the current rate limit to lock the step.
                await this.authentikt.updateState();
                this.status = "rate_limited";
                return;
            }
            const data = await response.json();

            if (data.success === true) {
                await this.authentikt.updateState();
                this.status = "ready";
            } else {
                // Load the remaining tries
                await this.authentikt.updateState();
                this.status = "password_incorrect";
            }
        } catch (e) {
            console.error(e);
            this.status = "error";
        }
    };
}
