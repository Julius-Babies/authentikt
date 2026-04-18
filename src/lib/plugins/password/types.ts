import type { Snippet } from "svelte";

export type PasswordStatus = "ready" | "loading" | "password_incorrect" | "error";

export type PasswordSnippet = Snippet<[
    string,
    PasswordStatus,
    () => Promise<void>,
    (value: string) => void
]>;
