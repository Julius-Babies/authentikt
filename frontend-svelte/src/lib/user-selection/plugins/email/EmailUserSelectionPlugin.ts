import type { Component } from "svelte";
import { AuthentiktUserSelectionPlugin } from "$lib/user-selection/plugins/AuthentiktUserSelectionPlugin";
import EmailUserSelectionRenderer from "$lib/user-selection/plugins/email/EmailUserSelectionRenderer.svelte";
import type { EmailUserSelectionSnippet } from "$lib/user-selection/plugins/email/types";

type EmailUserSelectionRendererComponent = Component<{
    plugin: EmailUserSelectionPlugin;
    payload?: Record<string, unknown>;
    children?: EmailUserSelectionSnippet;
}>;

export class EmailUserSelectionPlugin extends AuthentiktUserSelectionPlugin<EmailUserSelectionRendererComponent> {
    constructor() {
        super("authentikt-builtin/email", EmailUserSelectionRenderer as EmailUserSelectionRendererComponent);
    }

    selectUserByEmail = async (email: string): Promise<"success" | "not-existing"> => {
        const emailUrl = new URL(`user-selection/plugins/${this.namespace}`, this.authentikt.sessionUrl);
        const response = await fetch(emailUrl.toString(), {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ email }),
        });
        const data = await response.json();
        if (data.type === "user_not_found") return "not-existing";
        if (data.type === "success") {
            this.authentikt.setFlowUserState({
                username: data.username,
                displayName: data.display_name,
            });
            await this.authentikt.updateState();
            return "success";
        }
        throw new Error("Unknown response type");
    };
}
