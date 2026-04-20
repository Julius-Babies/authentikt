import {get, type Readable, readonly, writable, type Writable} from "svelte/store";
import type {AuthentiktPlugin} from "$lib/plugins/AuthentiktPlugin";
import type {AuthentiktUserSelectionPlugin} from "$lib/user-selection/plugins/AuthentiktUserSelectionPlugin";

export interface AuthentiktConfiguration {
    baseUrl: string;
    authentikt_debug: boolean | undefined;
    plugins: AuthentiktPlugin[] | undefined;
    userSelectionPlugins?: AuthentiktUserSelectionPlugin[];
}

export interface FlowUserState {
    username: string;
    displayName: string;
}

export interface FlowState {
    session_id: string;
    step: FlowStepData | null;
    state: FlowUserState | null;
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

export class Authentikt {
    configuration: {
        baseUrl: URL,
        authentiktDebug: boolean,
        installedPlugins: AuthentiktPlugin[];
        installedUserSelectionPlugins: AuthentiktUserSelectionPlugin[];
    };

    private _currentFlow: Writable<FlowState | null> = writable(null);
    currentFlow: Readable<FlowState | null> = readonly(this._currentFlow);

    constructor(configuration: AuthentiktConfiguration) {
        this.configuration = {
            baseUrl: new URL(configuration.baseUrl),
            authentiktDebug: configuration.authentikt_debug ?? false,
            installedPlugins: configuration.plugins ?? [],
            installedUserSelectionPlugins: configuration.userSelectionPlugins ?? [],
        };

        this.configuration.installedPlugins.forEach(plugin => {
            plugin.authentikt = this
        })

        this.configuration.installedUserSelectionPlugins.forEach(plugin => {
            plugin.authentikt = this
        })

        if (typeof window === "undefined") return;

        const currentUrl = new URL(window.location.href);
        if (currentUrl.searchParams.get("_authentikt_flow_active") === "true") {
            const session_id = currentUrl.searchParams.get("_authentikt_session_id");
            this._currentFlow.set({session_id: session_id ?? "", step: null, state: null});
            void this.updateState();
        }
    }

    startLoginFlow = async () => {
        const startFlowUrl = new URL("/login", this.configuration.baseUrl);
        const response = await fetch(startFlowUrl.toString());
        const data = await response.json();
        const session_id = data.session_id;

        const currentUrl = this.currentUrl();
        currentUrl.searchParams.set("_authentikt_flow_active", "true");
        currentUrl.searchParams.set("_authentikt_session_id", session_id);
        this.replaceBrowserUrl(currentUrl);
        this._currentFlow.set({session_id, step: null, state: null});

        await this.updateState();
    }

    cancelFlow = async () => {
        const currentUrl = this.currentUrl();
        currentUrl.searchParams.delete("_authentikt_flow_active");
        currentUrl.searchParams.delete("_authentikt_session_id");
        this.replaceBrowserUrl(currentUrl);
        this._currentFlow.set(null);
    }

    get sessionUrl(): URL {
        return new URL("flow/" + (get(this.currentFlow)?.session_id ?? "unknown") + "/", this.configuration.baseUrl);
    }

    updateState = async () => {
        if (!get(this.currentFlow)) return;

        const updateStateUrl = new URL("check", this.sessionUrl);
        const response = await fetch(updateStateUrl.toString());
        const data: FlowStepData = await response.json();

        if (data.type === "step" && !this.configuration.installedPlugins.map(p => p.namespace).includes(data.namespace))
            throw new Error(`No plugin found for step with namespace ${data.namespace}`);

        if (data.type === "user_selection") {
            const installedNamespaces = this.configuration.installedUserSelectionPlugins.map(p => p.namespace);
            data.plugins.forEach((plugin) => {
                if (!installedNamespaces.includes(plugin.namespace)) {
                    throw new Error(`No user selection plugin found for namespace ${plugin.namespace}`);
                }
            });
        }

        this._currentFlow.update((flow) => ({ ...flow!, step: data }));
    }

    setFlowUserState = (state: FlowUserState | null): void => {
        this._currentFlow.update((flow) => {
            if (!flow) return flow;
            return { ...flow, state };
        });
    }

    private currentUrl(): URL {
        if (typeof window === "undefined") {
            throw new Error("This operation is only available in the browser");
        }

        return new URL(window.location.href);
    }

    private replaceBrowserUrl(url: URL): void {
        if (typeof window === "undefined") return;
        window.history.replaceState(window.history.state, "", url);
    }
}

export function createAuthentikt(configuration: AuthentiktConfiguration): Authentikt {
    return new Authentikt(configuration);
}
