import type { Snippet } from "svelte";

/**
 * Status of the password verification step.
 * - `"ready"`: awaiting user input.
 * - `"loading"`: submitting to server.
 * - `"password_incorrect"`: server rejected the password.
 * - `"error"`: network or server error.
 */
export type PasswordStatus = "ready" | "loading" | "password_incorrect" | "error";

/**
 * Reactive state and actions exposed by the password plugin instance.
 */
export type PasswordPluginInstance = {
    namespace: string;
    /** The password input value (two-way bound). */
    password: string;
    /** Current verification status. */
    status: PasswordStatus;
    /** Whether this plugin is the currently active step. */
    isActive: boolean;
    /** Submits the password to the server for verification. */
    submit: () => Promise<void>;
}

/**
 * Snippet type for custom password plugin UI overrides.
 * Receives the plugin instance so custom inputs can bind to `plugin.password`.
 */
export type PasswordSnippet = Snippet<[PasswordPluginInstance]>;
