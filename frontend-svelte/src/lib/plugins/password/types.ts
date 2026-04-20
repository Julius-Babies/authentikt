import type { Snippet } from "svelte";

export type PasswordStatus = "ready" | "loading" | "password_incorrect" | "error";

export type PasswordPluginInstance = {
    password: string;
    status: PasswordStatus;
    isActive: boolean;
    submit: () => Promise<void>;
}

export type PasswordSnippet = Snippet<[PasswordPluginInstance]>;
