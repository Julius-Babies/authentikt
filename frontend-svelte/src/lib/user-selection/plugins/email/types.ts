import type { Snippet } from "svelte";

export type EmailUserSelectionStatus = "ready" | "loading" | "user_not_existing" | "error";

export type EmailUserSelectionPayload = {
    with_username?: boolean;
};

export type EmailUserSelectionSnippet = Snippet<[
    email: string,
    status: EmailUserSelectionStatus,
    submit: () => Promise<void>,
    updateEmail: (value: string) => void,
    payload: EmailUserSelectionPayload,
]>;
