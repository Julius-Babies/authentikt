import { AuthentiktPlugin } from "$lib/plugins/AuthentiktPlugin";
import type { Component } from "svelte";
import type {TotpSnippet} from "$lib/plugins/totp/types";
import TotpRenderer from "$lib/plugins/totp/TotpRenderer.svelte";

type TotpRendererComponent = Component<{ plugin: TotpPlugin; children?: TotpSnippet }>;

export class TotpPlugin extends AuthentiktPlugin<TotpRendererComponent> {

    constructor() {
        super("authentikt-builtin/totp", TotpRenderer as TotpRendererComponent);
    }

    useTotp = async (totp: string): Promise<"success" | "wrong"> => {
        const url = new URL("steps/plugins/" + this.namespace, this.authentikt.sessionUrl)
        const response = await fetch(url.toString(), {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({totp_code: totp}),
        });
        const data = await response.json();
        if (data.success === true) {
            await this.authentikt.updateState();
            return "success";
        }
        return "wrong";
    }
}
