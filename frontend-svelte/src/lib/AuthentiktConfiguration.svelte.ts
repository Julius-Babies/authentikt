import type {Component} from "svelte";
import type {PluginEntry, PluginFactory, PluginLike} from "./plugins/Plugin.types";
import {replaceState} from "$app/navigation";
import {page} from "$app/state";

/**
 * Configuration options passed to the `<Authentikt>` component.
 */
export interface AuthentiktConfiguration {
    /** Base URL of the authentikt server (e.g. `"http://localhost:8080/authentikt/"`). */
    baseUrl: string;
    /** Enable debug overlay showing live flow state. */
    debug?: boolean | {
        show_overlay: boolean;
    };
}

/**
 * Identified user returned by an identification step.
 */
export interface FlowUserState {
    username: string;
    displayName: string;
}

/**
 * Describes the current step in the authentication flow, as returned
 * by the server's `/authentikt/flow/{sessionId}/check` endpoint.
 */
export type FlowStepData = {
    type: "step";
    namespace: string;
    payload?: Record<string, unknown>;
} | {
    type: "finished";
}

export type FlowDestination =
    | { type: "none" }
    | { type: "device_flow"; application_id: string; application_name: string }
    | { type: "oauth"; application_id: string; application_name: string; redirect_uri: string };

/**
 * Complete state of an active authentication flow.
 */
export interface FlowState {
    session_id: string;
    step: FlowStepData | null;
    user: FlowUserState | null;
    attributes: Record<string, unknown>;
    destination: FlowDestination;
}

/**
 * Core authentikt client instance.
 *
 * Manages the authentication flow state, a registry of plugins,
 * and lazy instantiation of plugin instances with Svelte 5 `$state` reactivity.
 *
 * Instantiated by the `<Authentikt>` component and provided to children via context.
 * Use `useAuthentiktContext()` to access it.
 */
export class Authentikt {
    baseUrl: URL;
    debug: boolean;

    /** The current authentication flow state. `null` when no flow is active. */
    currentFlow = $state<FlowState | null>(null);

    private _registry = $state<PluginEntry[]>([]);
    private _instances = new Map<string, PluginLike>();

    constructor(config: AuthentiktConfiguration) {
        this.baseUrl = new URL(config.baseUrl);
        this.debug = !!config.debug;

        if (typeof window !== "undefined") {
            const currentUrl = new URL(window.location.href);
            if (currentUrl.searchParams.get("_authentikt_flow_active") === "true") {
                const session_id = currentUrl.searchParams.get("_authentikt_session_id");
                this.currentFlow = { session_id: session_id ?? "", step: null, user: null, attributes: {}, destination: { type: "none" } };
                void this.updateState();
            }
        }
    }

    /**
     * Registers a plugin by namespace.
     *
     * If the namespace is already registered, only the component is updated
     * (the existing plugin instance is preserved).
     *
     * @param namespace - unique identifier matching the server-side plugin.
     * @param component - Svelte component that renders this plugin's UI.
     * @param factory - function that creates the plugin instance.
     * @returns the plugin instance (existing or freshly created).
     */
    registerPlugin<T extends PluginLike>(
        namespace: string,
        component: Component<any>,
        factory: PluginFactory,
    ): T {
        const existing = this._registry.findIndex(e => e.namespace === namespace);
        if (existing !== -1) {
            const next = [...this._registry];
            next[existing] = { ...next[existing], component };
            this._registry = next;
            const cached = this._instances.get(namespace) as T | undefined;
            if (cached) return cached;
            return this._createPluginInstance<T>(namespace);
        }

        this._registry = [...this._registry, { namespace, factory, component }];
        return this._createPluginInstance<T>(namespace);
    }

    private _createPluginInstance<T extends PluginLike>(
        namespace: string,
    ): T {
        const entry = this._registry.find(e => e.namespace === namespace);
        if (!entry) throw new Error(`No plugin registered for namespace: ${namespace}`);
        const instance = entry.factory(this, namespace) as T;
        this._instances.set(namespace, instance);
        return instance;
    }

    /**
     * Returns the plugin instance for the given namespace,
     * creating it lazily if it does not yet exist.
     *
     * @param namespace - the identifier of the plugin.
     */
    getPlugin<T extends PluginLike>(
        namespace: string,
    ): T {
        const cached = this._instances.get(namespace) as T | undefined;
        if (cached) return cached;
        return this._createPluginInstance<T>(namespace);
    }

    pluginInstance(namespace: string): PluginLike | undefined {
        return this._instances.get(namespace);
    }

    /**
     * The registered entry for the currently active step, or `null`.
     */
    activeStepEntry = $derived.by(() => {
        const step = this.currentFlow?.step;
        if (step?.type !== "step") return null;
        const entry = this._registry.find(e => e.namespace === step.namespace);
        if (!entry) return null;
        return entry;
    });

    /**
     * The plugin instance for the currently active step, or `null`.
     */
    activeStepPlugin = $derived.by(() => {
        const step = this.currentFlow?.step;
        if (step?.type !== "step") return null;
        return this.getPlugin(step.namespace);
    });

    /**
     * @deprecated Use [registerPlugin] instead. Kept for backward compatibility.
     */
    linkStepPlugin<T>(namespace: string, component: Component<any>, createInstance: () => T): T {
        return this.registerPlugin(
            namespace,
            component,
            (_auth, _ns) => createInstance() as unknown as PluginLike,
        ) as T;
    }

    /**
     * Starts a new login flow by creating a session on the server.
     *
     * Clears any previously cached plugin instances and updates browser history.
     */
    startLoginFlow = async () => {
        this._instances.clear();

        const startFlowUrl = new URL("/api/login", this.baseUrl);
        const response = await fetch(startFlowUrl.toString());
        const data = await response.json();
        const session_id = data.session_id;

        const currentUrl = this.currentUrl();
        currentUrl.searchParams.set("_authentikt_flow_active", "true");
        currentUrl.searchParams.set("_authentikt_session_id", session_id);
        this.replaceBrowserUrl(currentUrl);

        this.currentFlow = { session_id, step: null, user: null, attributes: {}, destination: { type: "none" } };
        await this.updateState();
    }

    /**
     * Links the current instance to an already running flow by taking a session id.
     * Clears any previously cached plugin instances and updates browser history.
     */
    linkToFlow = async (session_id: string) => {
        this._instances.clear();

        const currentUrl = this.currentUrl();
        currentUrl.searchParams.set("_authentikt_flow_active", "true");
        currentUrl.searchParams.set("_authentikt_session_id", session_id);
        this.replaceBrowserUrl(currentUrl);

        this.currentFlow = { session_id, step: null, user: null, attributes: {}, destination: { type: "none" } };
        await this.updateState();
    }

    /**
     * Cancels the current flow, clears URL parameters and plugin instances.
     */
    cancelFlow = async () => {
        const currentUrl = this.currentUrl();
        currentUrl.searchParams.delete("_authentikt_flow_active");
        currentUrl.searchParams.delete("_authentikt_session_id");
        this.replaceBrowserUrl(currentUrl);
        this.currentFlow = null;
        this._instances.clear();
    }

    get sessionUrl(): URL {
        return new URL("flow/" + (this.currentFlow?.session_id ?? "unknown") + "/", this.baseUrl);
    }

    /**
     * Polls the server for the current flow state and updates `currentFlow.step`.
     */
    updateState = async () => {
        if (!this.currentFlow) return;

        const updateStateUrl = new URL("check", this.sessionUrl);
        const response = await fetch(updateStateUrl.toString());
        const data = await response.json();
        const { attributes, destination, ...stepData } = data;
        this.currentFlow.step = stepData as FlowStepData;
        this.currentFlow.attributes = (attributes ?? {}) as Record<string, unknown>;
        this.currentFlow.destination = (destination ?? { type: "none" }) as FlowDestination;
    }

    /**
     * Sets the identified user on the current flow state.
     */
    setUser = (user: FlowUserState | null): void => {
        if (this.currentFlow) {
            this.currentFlow.user = user;
        }
    }

    private currentUrl(): URL {
        if (typeof window === "undefined") throw new Error("Browser only");
        return new URL(window.location.href);
    }

    private replaceBrowserUrl(url: URL): void {
        if (typeof window === "undefined") return;
        replaceState(url, page.state);
    }
}
