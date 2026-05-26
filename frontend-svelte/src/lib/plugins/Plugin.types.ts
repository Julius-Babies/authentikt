import type { Component } from "svelte";
import type { Authentikt } from "$lib/AuthentiktConfiguration.svelte";
import type { FlowUserState } from "$lib/AuthentiktConfiguration.svelte";

export interface PluginLike {
    readonly isActive: boolean;
    readonly namespace: string;
}

export type PluginFactory = (
    authentikt: Authentikt,
    namespace: string,
    readPayload?: () => Record<string, unknown> | undefined,
) => PluginLike;

export interface PluginComponentProps<T extends PluginLike = PluginLike> {
    plugin: T;
    payload?: Record<string, unknown>;
    user?: FlowUserState | null;
}

export interface PluginEntry {
    namespace: string;
    factory: PluginFactory;
    component: Component<any>;
}
