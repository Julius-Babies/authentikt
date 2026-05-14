import type { Component } from "svelte";
import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";
import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

/**
 * Minimal interface that every user-selection plugin instance must satisfy.
 *
 * User-selection plugins handle the first stage of authentication where
 * the user identifies themselves (email, username, etc.).
 */
export interface UserSelectionPluginLike {
    /** Whether this plugin is part of the currently active user-selection step. */
    readonly isActive: boolean;
    /** Unique namespace matching the server-side plugin. */
    readonly namespace: string;
}

/**
 * Factory function that creates a user-selection plugin instance.
 * @param authentikt - the parent Authentikt instance.
 * @param namespace - the plugin namespace.
 * @param readPayload - optional function to read server-provided payload/configuration for this plugin instance.
 */
export type UserSelectionPluginFactory = (
    authentikt: Authentikt,
    namespace: string,
    readPayload?: () => Record<string, unknown> | undefined,
) => UserSelectionPluginLike;

/**
 * Props expected by a user-selection plugin's renderer component.
 *
 * @template T - the concrete plugin instance type.
 */
export interface UserSelectionPluginComponentProps<T extends UserSelectionPluginLike = UserSelectionPluginLike> {
    /** The plugin instance with rune-based reactive state. */
    plugin: T;
    /** Server-provided payload/configuration for this user-selection entry. */
    payload?: Record<string, unknown>;
    /** The identified user (set after a previous user selection). */
    user?: FlowUserState | null;
}

/**
 * Registration entry stored in the Authentikt registry for a user-selection plugin.
 */
export interface UserSelectionPluginEntry {
    namespace: string;
    factory: UserSelectionPluginFactory;
    component: Component<any>;
}
