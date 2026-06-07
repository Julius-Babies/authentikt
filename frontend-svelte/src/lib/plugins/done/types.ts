import type { Snippet } from "svelte";

export type DoneResult =
    | { type: "success"; cookies?: string[] }
    | { type: "redirect"; to: string; cookies?: string[] }
    | { type: "device_flow_success" }
    | null;

export type DonePluginInstance = {
    namespace: string;
    isActive: boolean;
    result: DoneResult;
    complete: () => Promise<void>;
};

export type DoneSnippet = Snippet<[DonePluginInstance]>;
