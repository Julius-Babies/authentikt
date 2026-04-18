import type { Snippet } from "svelte";

export type PasswordStatus = "ready" | "loading" | "password_incorrect" | "error";

export type PasswordSnippet = Snippet<[
    password: string,
    status: PasswordStatus,
    submit: () => Promise<void>,
    updatePassword: (value: string) => void
]>;
