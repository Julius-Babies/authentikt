import type { Snippet } from "svelte";

/**
 * Status of the TOTP verification step.
 * - `"ready"`: awaiting user input.
 * - `"loading"`: submitting to server.
 * - `"totp_incorrect"`: server rejected the code.
 * - `"error"`: network or server error.
 */
export type TotpStatus = "ready" | "loading" | "totp_incorrect" | "error";

/**
 * Reactive state and actions exposed by the TOTP plugin instance.
 */
export type TotpPluginInstance = {
    namespace: string;
    /** The TOTP code input value (two-way bound). */
    totp: string;
    /** Current verification status. */
    status: TotpStatus;
    /** Whether this plugin is the currently active step. */
    isActive: boolean;
    /** Submits the TOTP code to the server for validation. */
    submit: () => Promise<void>;
}

/**
 * Snippet type for custom TOTP plugin UI overrides.
 * Receives the plugin instance so custom inputs can bind to `plugin.totp`.
 */
export type TotpSnippet = Snippet<[TotpPluginInstance]>;
