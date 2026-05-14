import type { Snippet } from "svelte";

/**
 * Status of the email-based user selection step.
 * - `"ready"`: awaiting user input.
 * - `"loading"`: submitting to server.
 * - `"user_not_existing"`: no user found for the entered email.
 * - `"error"`: network or server error.
 */
export type EmailUserSelectionStatus = "ready" | "loading" | "user_not_existing" | "error";

/**
 * Server-provided payload for the email user-selection plugin.
 * Configures client-side behaviour such as requiring a username alongside the email.
 */
export interface EmailUserSelectionPayload {
    /** Whether the server also expects a username after email entry. */
    with_username: boolean;
}

/**
 * Reactive state and actions exposed by the email user-selection plugin instance.
 */
export type EmailUserSelectionPluginInstance = {
    namespace: string;
    /** The email input value (two-way bound). */
    email: string;
    /** Current submission status. */
    status: EmailUserSelectionStatus;
    /** Parsed server payload. */
    typedPayload: EmailUserSelectionPayload;
    /** Whether this plugin is part of the current user-selection step. */
    isActive: boolean;
    /** Submits the email to the server for user lookup. */
    submit: () => Promise<void>;
}

/**
 * Snippet type for custom email user-selection UI overrides.
 * Receives the plugin instance so custom inputs can bind to `plugin.email`.
 */
export type EmailUserSelectionSnippet = Snippet<[EmailUserSelectionPluginInstance]>;
