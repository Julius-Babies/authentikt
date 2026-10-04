import type { Snippet } from "svelte";

/**
 * Status of the junction step.
 * - `"ready"`: awaiting a selection.
 * - `"loading"`: submitting the selection to the server.
 * - `"error"`: network or server error.
 */
export type JunctionStatus = "ready" | "loading" | "error";

/**
 * Reactive state and actions exposed by the junction plugin instance.
 */
export type JunctionPluginInstance = {
    namespace: string;
    /** Namespaces of the steps the user can choose from. */
    options: string[];
    /** Current selection status. */
    status: JunctionStatus;
    /** Whether this plugin is the currently active step. */
    isActive: boolean;
    /** Selects the step with the given namespace. */
    select: (namespace: string) => Promise<void>;
}

/**
 * Snippet type for custom junction UI overrides.
 */
export type JunctionSnippet = Snippet<[JunctionPluginInstance]>;
