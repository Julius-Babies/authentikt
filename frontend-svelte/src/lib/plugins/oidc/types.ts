import type { Snippet } from "svelte";

export type OIDCPluginInstance = {
    namespace: string;
    isActive: boolean;
    authorizeUrl: string | undefined;
    redirect: () => void;
};

export type OIDCSnippet = Snippet<[OIDCPluginInstance]>;
