import type { Snippet } from "svelte";

/**
 * Snippet type for custom "done" / token-receipt UI overrides.
 * The DonePlugin does not expose interactive state, so the snippet receives no arguments.
 */
export type DoneSnippet = Snippet<[]>;
