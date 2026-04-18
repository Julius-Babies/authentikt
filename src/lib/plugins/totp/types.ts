import type { Snippet } from "svelte";

export type TotpStatus = "ready" | "loading" | "totp_incorrect" | "error";

export type TotpSnippet = Snippet<[
    totp: string,
    status: TotpStatus,
    submit: () => Promise<void>,
    updateTotp: (value: string) => void
]>;
