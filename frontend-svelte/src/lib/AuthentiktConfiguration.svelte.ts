import type { Component } from "svelte";

export interface AuthentiktConfiguration {
    baseUrl: string;
    authentikt_debug?: boolean;
}

export interface FlowUserState {
    username: string;
    displayName: string;
}

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

export interface FlowState {
    session_id: string;
    step: FlowStepData | null;
    user: FlowUserState | null;
}

export interface PluginRegistryEntry {
    namespace: string;
    instance: any;
    component: Component<any>;
}

export class Authentikt {
    baseUrl: URL;
    debug: boolean;

    // Runes
    currentFlow = $state<FlowState | null>(null);
    plugins = $state<PluginRegistryEntry[]>([]);
    userSelectionPlugins = $state<PluginRegistryEntry[]>([]);

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

    linkStepPlugin<T>(namespace: string, component: Component<any>, createInstance: () => T): T {
        const index = this.plugins.findIndex(p => p.namespace === namespace);
        if (index === -1) {
            const instance = createInstance();
            this.plugins = [...this.plugins, { namespace, instance, component }];
            return instance;
        }

        const existing = this.plugins[index];
        if (existing.component !== component) {
            const next = [...this.plugins];
            next[index] = { ...existing, component };
            this.plugins = next;
        }

        return existing.instance as T;
    }

    linkUserSelectionPlugin<T>(namespace: string, component: Component<any>, createInstance: () => T): T {
        const index = this.userSelectionPlugins.findIndex(p => p.namespace === namespace);
        if (index === -1) {
            const instance = createInstance();
            this.userSelectionPlugins = [...this.userSelectionPlugins, { namespace, instance, component }];
            return instance;
        }

        const existing = this.userSelectionPlugins[index];
        if (existing.component !== component) {
            const next = [...this.userSelectionPlugins];
            next[index] = { ...existing, component };
            this.userSelectionPlugins = next;
        }

        return existing.instance as T;
    }

    startLoginFlow = async () => {
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

    cancelFlow = async () => {
        const currentUrl = this.currentUrl();
        currentUrl.searchParams.delete("_authentikt_flow_active");
        currentUrl.searchParams.delete("_authentikt_session_id");
        this.replaceBrowserUrl(currentUrl);
        this.currentFlow = null;
    }

    get sessionUrl(): URL {
        return new URL("flow/" + (this.currentFlow?.session_id ?? "unknown") + "/", this.baseUrl);
    }

    updateState = async () => {
        if (!this.currentFlow) return;

        const updateStateUrl = new URL("check", this.sessionUrl);
        const response = await fetch(updateStateUrl.toString());
        const data: FlowStepData = await response.json();

        this.currentFlow.step = data;
    }

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
