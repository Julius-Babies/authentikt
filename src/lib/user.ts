import {writable} from "svelte/store";

export const currentUser = writable<null | "anonymous" | { id: string, displayName: string }>(null)