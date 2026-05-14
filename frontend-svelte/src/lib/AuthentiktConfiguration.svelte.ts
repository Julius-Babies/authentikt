import type { Component } from "svelte";
import type { StepPluginLike, StepPluginFactory, StepPluginEntry } from "./plugins/StepPlugin.types";
import type { UserSelectionPluginLike, UserSelectionPluginFactory, UserSelectionPluginEntry } from "./plugins/UserSelectionPlugin.types";

/**
 * Configuration options passed to the `<Authentikt>` component.
 */
export interface AuthentiktConfiguration {
    /** Base URL of the authentikt server (e.g. `"http://localhost:8080/authentikt/"`). */
    baseUrl: string;
    /** Enable debug overlay showing live flow state. */
    authentikt_debug?: boolean;
}

/**
 * Identified user returned by a user-selection step.
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
    type: "user_selection",
    plugins: {
        namespace: string,
        payload?: Record<string, unknown>,
    }[]
} | {
    type: "step";
    namespace: string;
    payload?: Record<string, unknown>;
} | {
    type: "finished";
}

/**
 * Complete state of an active authentication flow.
 */
export interface FlowState {
    session_id: string;
    step: FlowStepData | null;
    user: FlowUserState | null;
}

/**
 * Core authentikt client instance.
 *
 * Manages the authentication flow state, a registry of step and user-selection
 * plugins, and lazy instantiation of plugin instances with Svelte 5 `$state` reactivity.
 *
 * Instantiated by the `<Authentikt>` component and provided to children via context.
 * Use `useAuthentiktContext()` to access it.
 */
export class Authentikt {
    baseUrl: URL;
    debug: boolean;

    /** The current authentication flow state. `null` when no flow is active. */
    currentFlow = $state<FlowState | null>(null);

    private _stepRegistry = $state<StepPluginEntry[]>([]);
    private _userSelectionRegistry = $state<UserSelectionPluginEntry[]>([]);

    private _stepInstances = new Map<string, StepPluginLike>();
    private _userSelectionInstances = new Map<string, UserSelectionPluginLike>();

    constructor(config: AuthentiktConfiguration) {
        this.baseUrl = new URL(config.baseUrl);
        this.debug = config.authentikt_debug ?? false;

        if (typeof window !== "undefined") {
            const currentUrl = new URL(window.location.href);
            if (currentUrl.searchParams.get("_authentikt_flow_active") === "true") {
                const session_id = currentUrl.searchParams.get("_authentikt_session_id");
                this.currentFlow = { session_id: session_id ?? "", step: null, user: null };
                void this.updateState();
            }
        }
    }

    /**
     * Registers a step plugin (password, TOTP, done, or custom).
     *
     * If the namespace is already registered, only the component is updated
     * (the existing plugin instance is preserved).
     *
     * @param namespace - unique identifier matching the server-side plugin.
     * @param component - Svelte component that renders this plugin's UI.
     * @param factory - function that creates the plugin instance.
     * @returns the plugin instance (existing or freshly created).
     */
    registerStepPlugin<T extends StepPluginLike>(
        namespace: string,
        component: Component<any>,
        factory: StepPluginFactory,
    ): T {
        const existing = this._stepRegistry.findIndex(e => e.namespace === namespace);
        if (existing !== -1) {
            const next = [...this._stepRegistry];
            next[existing] = { ...next[existing], component };
            this._stepRegistry = next;
            const cached = this._stepInstances.get(namespace) as T | undefined;
            if (cached) return cached;
            return this._createStepPluginInstance<T>(namespace);
        }

        this._stepRegistry = [...this._stepRegistry, { namespace, factory, component }];
        return this._createStepPluginInstance<T>(namespace);
    }

    /**
     * Registers a user-selection plugin (email, username, or custom).
     *
     * If the namespace is already registered, only the component is updated.
     *
     * @param namespace - unique identifier matching the server-side plugin.
     * @param component - Svelte component that renders this plugin's UI.
     * @param factory - function that creates the plugin instance, receiving an
     *   optional `readPayload` callback for server-provided configuration.
     * @returns the plugin instance (existing or freshly created).
     */
    registerUserSelectionPlugin<T extends UserSelectionPluginLike>(
        namespace: string,
        component: Component<any>,
        factory: UserSelectionPluginFactory,
    ): T {
        const existing = this._userSelectionRegistry.findIndex(e => e.namespace === namespace);
        if (existing !== -1) {
            const next = [...this._userSelectionRegistry];
            next[existing] = { ...next[existing], component };
            this._userSelectionRegistry = next;
            const cached = this._userSelectionInstances.get(namespace) as T | undefined;
            if (cached) return cached;
            return this._createUserSelectionPluginInstance<T>(namespace);
        }

        this._userSelectionRegistry = [...this._userSelectionRegistry, { namespace, factory, component }];
        return this._createUserSelectionPluginInstance<T>(namespace);
    }

    private _createStepPluginInstance<T extends StepPluginLike>(namespace: string): T {
        const entry = this._stepRegistry.find(e => e.namespace === namespace);
        if (!entry) throw new Error(`No step plugin registered for namespace: ${namespace}`);
        const instance = entry.factory(this, namespace) as T;
        this._stepInstances.set(namespace, instance);
        return instance;
    }

    private _createUserSelectionPluginInstance<T extends UserSelectionPluginLike>(
        namespace: string,
        readPayload?: () => Record<string, unknown> | undefined,
    ): T {
        const entry = this._userSelectionRegistry.find(e => e.namespace === namespace);
        if (!entry) throw new Error(`No user selection plugin registered for namespace: ${namespace}`);
        const instance = entry.factory(this, namespace, readPayload) as T;
        this._userSelectionInstances.set(namespace, instance);
        return instance;
    }

    /**
     * Returns the step plugin instance for the given namespace,
     * creating it lazily if it does not yet exist.
     */
    getStepPlugin<T extends StepPluginLike>(namespace: string): T {
        const cached = this._stepInstances.get(namespace) as T | undefined;
        if (cached) return cached;
        return this._createStepPluginInstance<T>(namespace);
    }

    /**
     * Returns the user-selection plugin instance for the given namespace,
     * creating it lazily if it does not yet exist.
     *
     * @param readPayload - optional callback to read server-provided configuration
     *   for this user-selection entry.
     */
    getUserSelectionPlugin<T extends UserSelectionPluginLike>(
        namespace: string,
        readPayload?: () => Record<string, unknown> | undefined,
    ): T {
        const cached = this._userSelectionInstances.get(namespace) as T | undefined;
        if (cached) return cached;
        return this._createUserSelectionPluginInstance<T>(namespace, readPayload);
    }

    stepInstance(namespace: string): StepPluginLike | undefined {
        return this._stepInstances.get(namespace);
    }

    userSelectionInstance(namespace: string): UserSelectionPluginLike | undefined {
        return this._userSelectionInstances.get(namespace);
    }

    /**
     * The registered entry for the currently active step, or `null`.
     * Used internally by `<AuthentiktStepRenderer>`.
     */
    activeStepEntry = $derived.by(() => {
        const step = this.currentFlow?.step;
        if (step?.type !== "step") return null;
        const entry = this._stepRegistry.find(e => e.namespace === step.namespace);
        if (!entry) return null;
        return entry;
    });

    /**
     * The plugin instance for the currently active step, or `null`.
     * Used internally by `<AuthentiktStepRenderer>`.
     */
    activeStepPlugin = $derived.by(() => {
        const step = this.currentFlow?.step;
        if (step?.type !== "step") return null;
        const plugin = this.getStepPlugin(step.namespace);
        return plugin;
    });

    /**
     * Registered entries + plugin instances for the currently active user-selection
     * step, or an empty array. Used internally by `<AuthentiktUserSelectionRenderer>`.
     */
    activeUserSelectionEntries = $derived.by(() => {
        const step = this.currentFlow?.step;
        if (step?.type !== "user_selection") return [];
        return step.plugins.map(candidate => {
            const entry = this._userSelectionRegistry.find(e => e.namespace === candidate.namespace);
            if (!entry) return null;
            const plugin = this.getUserSelectionPlugin(candidate.namespace, () => candidate.payload);
            return { entry, plugin, payload: candidate.payload };
        }).filter(Boolean) as {
            entry: UserSelectionPluginEntry;
            plugin: UserSelectionPluginLike;
            payload?: Record<string, unknown>;
        }[];
    });

    /**
     * Plugin instances for the currently active user-selection step, or an empty array.
     */
    activeUserSelectionPlugins = $derived.by(() => {
        const step = this.currentFlow?.step;
        if (step?.type !== "user_selection") return [];
        return step.plugins.map(candidate => {
            return this.getUserSelectionPlugin(candidate.namespace, () => candidate.payload);
        }).filter(Boolean) as UserSelectionPluginLike[];
    });

    /**
     * @deprecated Use [registerStepPlugin] instead. Kept for backward compatibility.
     */
    linkStepPlugin<T>(namespace: string, component: Component<any>, createInstance: () => T): T {
        return this.registerStepPlugin(
            namespace,
            component,
            (_auth, _ns) => createInstance() as unknown as StepPluginLike,
        ) as T;
    }

    /**
     * @deprecated Use [registerUserSelectionPlugin] instead. Kept for backward compatibility.
     */
    linkUserSelectionPlugin<T>(namespace: string, component: Component<any>, createInstance: () => T): T {
        return this.registerUserSelectionPlugin(
            namespace,
            component,
            (_auth, _ns, _rp) => createInstance() as unknown as UserSelectionPluginLike,
        ) as T;
    }

    /**
     * Starts a new login flow by creating a session on the server.
     *
     * Clears any previously cached plugin instances and updates browser history.
     */
    startLoginFlow = async () => {
        this._stepInstances.clear();
        this._userSelectionInstances.clear();

        const startFlowUrl = new URL("/login", this.baseUrl);
        const response = await fetch(startFlowUrl.toString());
        const data = await response.json();
        const session_id = data.session_id;

        const currentUrl = this.currentUrl();
        currentUrl.searchParams.set("_authentikt_flow_active", "true");
        currentUrl.searchParams.set("_authentikt_session_id", session_id);
        this.replaceBrowserUrl(currentUrl);

        this.currentFlow = { session_id, step: null, user: null };
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
        this._stepInstances.clear();
        this._userSelectionInstances.clear();
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
        const data: FlowStepData = await response.json();

        this.currentFlow.step = data;
    }

    /**
     * Sets the identified user on the current flow state.
     * Called by user-selection plugins after successful identification.
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
        window.history.replaceState(window.history.state, "", url);
    }
}
