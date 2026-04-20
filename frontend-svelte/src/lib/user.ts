import {writable} from "svelte/store";

export type User = { id: string; displayName: string };

export const currentUser = writable<null | "anonymous" | User>(null)
