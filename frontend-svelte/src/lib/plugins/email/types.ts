import type { Snippet } from "svelte";
import type { RateLimitState } from "$lib/rate-limit.svelte";

export type EmailUserSelectionStatus = "ready" | "loading" | "user_not_existing" | "rate_limited" | "error";

export interface EmailUserSelectionPayload {
    with_username: boolean;
}

export type EmailUserSelectionPluginInstance = {
    namespace: string;
    email: string;
    status: EmailUserSelectionStatus;
    typedPayload: EmailUserSelectionPayload;
    /** Rate limit of lookups without a match, or `null` if lookups are not limited. */
    rateLimit: RateLimitState | null;
    isActive: boolean;
    submit: () => Promise<void>;
}

export type EmailUserSelectionSnippet = Snippet<[EmailUserSelectionPluginInstance]>;
