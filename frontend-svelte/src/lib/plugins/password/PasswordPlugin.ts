import { AuthentiktPlugin } from "$lib/plugins/AuthentiktPlugin";
import type { Component } from "svelte";
import PasswordRenderer from "$lib/plugins/password/PasswordRenderer.svelte";
import type { PasswordSnippet } from "$lib/plugins/password/types";

type PasswordRendererComponent = Component<{ plugin: PasswordPlugin; children?: PasswordSnippet }>;

export class PasswordPlugin extends AuthentiktPlugin<PasswordRendererComponent> {

    constructor() {
        super("authentikt-builtin/password", PasswordRenderer as PasswordRendererComponent);
    }

    login = async (password: string): Promise<"success" | "wrong"> => {
        const url = new URL("steps/plugins/" + this.namespace, this.authentikt.sessionUrl)
        const response = await fetch(url.toString(), {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({password: password}),
        });
        const data = await response.json();
        if (data.success === true) {
            await this.authentikt.updateState();
            return "success";
        }
        return "wrong";
    }
}
