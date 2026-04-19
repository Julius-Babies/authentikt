export { createAuthentikt, Authentikt as AuthentiktInstance } from "./AuthentiktConfiguration";
export type {
    AuthentiktConfiguration,
    FlowState,
    FlowStepData,
    FlowUserState
} from "./AuthentiktConfiguration";

export { default as Authentikt } from "./Authentikt.svelte";
export { default as AuthentiktView } from "./AuthentiktView.svelte";

export { AuthentiktPlugin } from "./plugins/AuthentiktPlugin";
export { PasswordPlugin } from "./plugins/password/PasswordPlugin";
export type { PasswordStatus, PasswordSnippet } from "./plugins/password/types";

export type { User } from "./User";
