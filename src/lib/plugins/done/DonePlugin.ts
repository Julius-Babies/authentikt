import type {Component} from "svelte";
import type {DoneSnippet} from "$lib/plugins/done/types";
import {AuthentiktPlugin} from "$lib";
import DoneRenderer from "$lib/plugins/done/DoneRenderer.svelte";

type DoneRendererComponent = Component<{ plugin: DonePlugin; children?: DoneSnippet }>;

export class DonePlugin extends AuthentiktPlugin<DoneRendererComponent> {

    onSuccess = () => {};

    constructor(onSuccess: () => void) {
        super("authentikt-builtin/done", DoneRenderer as DoneRendererComponent);
        this.onSuccess = onSuccess;
    }

    async saveToken() {
        const url = new URL("steps/plugins/" + this.namespace, this.authentikt.sessionUrl)
        await fetch(url.toString(), {
            method: "GET",
            credentials: "include",
        });
        this.onSuccess();
        await this.authentikt.cancelFlow();
    }
}