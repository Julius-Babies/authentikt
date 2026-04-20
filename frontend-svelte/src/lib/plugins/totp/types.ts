import type { Snippet } from "svelte";

export type TotpStatus = "ready" | "loading" | "totp_incorrect" | "error";

export type TotpPluginInstance = {
    totp: string;
    status: TotpStatus;
    isActive: boolean;
    submit: () => Promise<void>;
}

export type TotpSnippet = Snippet<[TotpPluginInstance]>;
