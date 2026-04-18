import type { Component } from "svelte";
import {Authentikt} from "$lib/AuthentiktConfiguration";

export type AuthentiktPluginComponentProps<TPlugin extends AuthentiktPlugin = AuthentiktPlugin> = {
    plugin: TPlugin;
};

export abstract class AuthentiktPlugin<TRenderer extends Component<any> = Component<any>> {
    namespace: string;
    renderer: TRenderer;

    protected constructor(namespace: string, renderer: TRenderer) {
        this.namespace = namespace;
        this.renderer = renderer;
    }

    private _authentikt: Authentikt | null = null;
    public set authentikt(authentikt: Authentikt) {
        this._authentikt = authentikt;
    }
    public get authentikt(): Authentikt {
        if (!this._authentikt) {
            throw new Error("Authentikt instance not set");
        }
        return this._authentikt;
    }
}
