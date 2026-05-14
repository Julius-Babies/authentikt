import type { Component } from "svelte";
import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";
import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

/**
 * Minimal interface that every step plugin instance must satisfy.
 *
 * Step plugins handle authentication stages after user identification
 * (e.g. password, TOTP, done/token generation).
 */
export interface StepPluginLike {
    /** Whether this plugin is the currently active step in the flow. */
    readonly isActive: boolean;
    /** Unique namespace matching the server-side plugin (e.g. `"authentikt-builtin/password"`). */
    readonly namespace: string;
}

/**
 * Factory function that creates a step plugin instance.
 * @param authentikt - the parent Authentikt instance for accessing flow state.
 * @param namespace - the plugin namespace to associate this instance with.
 */
export type StepPluginFactory = (
    authentikt: Authentikt,
    namespace: string,
) => StepPluginLike;

/**
 * Props expected by a step plugin's renderer component.
 *
 * The renderer receives the plugin instance (with its reactive state) and
 * the current identified user (if any).
 */
export interface StepPluginComponentProps<T extends StepPluginLike = StepPluginLike> {
    /** The plugin instance with rune-based reactive state. */
    plugin: T;
    /** The identified user from the user-selection step, or null. */
    user?: FlowUserState | null;
}

/**
 * Registration entry stored in the Authentikt registry for a step plugin.
 *
 * Combines the namespace, factory, and renderer component so the auto-renderer
 * can create instances and display the appropriate UI.
 */
export interface StepPluginEntry {
    namespace: string;
    factory: StepPluginFactory;
    component: Component<any>;
}
