import type { Snippet } from "svelte";

export type EmailUserSelectionStatus = "ready" | "loading" | "user_not_existing" | "error";

export interface EmailUserSelectionPayload {
    with_username: boolean;
}

export type EmailUserSelectionPluginInstance = {
    email: string;
    status: EmailUserSelectionStatus;
    typedPayload: EmailUserSelectionPayload;
    isActive: boolean;
    submit: () => Promise<void>;
}

export type EmailUserSelectionSnippet = Snippet<[EmailUserSelectionPluginInstance]>;
