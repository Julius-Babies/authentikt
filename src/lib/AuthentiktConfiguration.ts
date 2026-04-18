import {get, readable, type Readable, writable, type Writable} from "svelte/store";
import type {User} from "$lib/User";
import {goto} from "$app/navigation";
import {browser} from "$app/environment";

export interface AuthentiktConfiguration {
    baseUrl: string;
    authentikt_debug: boolean | undefined;
}

interface FlowState {
    session_id: string;
    state: any | undefined;
}

export class Authentikt {
    configuration: {
        baseUrl: URL,
        authentikt_debug: boolean,
    };

    private _user: Writable<User | null> = writable(null);
    user: Readable<User | null> = readable(null, (set) => {
        return this._user.subscribe(set);
    });

    private _currentFlow: Writable<FlowState | null> = writable(null);
    currentFlow: Readable<FlowState | null> = readable(null, (set) => {
        return this._currentFlow.subscribe(set);
    })

    constructor(configuration: AuthentiktConfiguration) {
        this.configuration = {
            baseUrl: new URL(configuration.baseUrl),
            authentikt_debug: configuration.authentikt_debug ?? false,
        };

        if (!browser) return;
        const currentUrl = new URL(window.location.href);
        if (currentUrl.searchParams.get("_authentikt_flow_active") === "true") {
            const session_id = currentUrl.searchParams.get("_authentikt_session_id");
            this._currentFlow.set({session_id: session_id ?? "", state: undefined});
            this.updateState();
        }
    }

    startLoginFlow = async () => {
        const startFlowUrl = new URL("flow/start", this.configuration.baseUrl);
        const response = await fetch(startFlowUrl.toString());
        const data = await response.json();
        const session_id = data.session_id;

        const currentUrl = new URL(window.location.href);
        currentUrl.searchParams.set("_authentikt_flow_active", "true")
        currentUrl.searchParams.set("_authentikt_session_id", session_id);
        await goto(currentUrl.toString(), {replaceState: true, noScroll: true});
        this._currentFlow.set({session_id, state: undefined});

        await this.updateState();
    }

    cancelFlow = async () => {
        const currentUrl = new URL(window.location.href);
        currentUrl.searchParams.delete("_authentikt_flow_active");
        currentUrl.searchParams.delete("_authentikt_session_id");
        await goto(currentUrl.toString(), {replaceState: true, noScroll: true});
        this._currentFlow.set(null);
    }

    get sessionUrl(): URL {
        return new URL("flow/" + (get(this.currentFlow)?.session_id ?? "unknown") + "/", this.configuration.baseUrl);
    }

    updateState = async () => {
        const updateStateUrl = new URL("check", this.sessionUrl);
        const response = await fetch(updateStateUrl.toString());
        const data = await response.json();
        console.log(data);
        this._currentFlow.update((flow) => (
            {...flow!, state: data.state}
        ))
    }
}

export function createAuthentikt(configuration: AuthentiktConfiguration): Authentikt {
    return new Authentikt(configuration);
}