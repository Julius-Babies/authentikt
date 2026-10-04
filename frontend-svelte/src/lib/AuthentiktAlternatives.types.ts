import type { Snippet } from "svelte";

/**
 * A step the user can switch to instead of the active step.
 */
export type AlternativeOption = {
    namespace: string;
    /** Display label, looked up by namespace. */
    label: string;
    /** Replaces the active step with this one. */
    select: () => Promise<void>;
};

/**
 * Snippet type for custom alternatives UI. Receives the available alternatives.
 */
export type AlternativesSnippet = Snippet<[AlternativeOption[]]>;
