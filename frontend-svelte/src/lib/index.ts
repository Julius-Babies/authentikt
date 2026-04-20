export type {
    AuthentiktConfiguration,
    FlowState,
    FlowStepData,
    FlowUserState
} from "./AuthentiktConfiguration.svelte";

export { default as Authentikt } from "./Authentikt.svelte";

export type { PasswordStatus, PasswordSnippet, PasswordPluginInstance } from "./plugins/password/types";
export type { TotpStatus, TotpSnippet, TotpPluginInstance } from "./plugins/totp/types";
export type {
    EmailUserSelectionStatus,
    EmailUserSelectionSnippet,
    EmailUserSelectionPayload,
    EmailUserSelectionPluginInstance
} from "./user-selection/plugins/email/types";

export type { User } from "./user";

export { PasswordPlugin } from "./plugins/password/PasswordPlugin.svelte";
export { TotpPlugin } from "./plugins/totp/TotpPlugin.svelte";
export { EmailUserSelectionPlugin } from "./user-selection/plugins/email/EmailUserSelectionPlugin.svelte";
export { DonePlugin } from "./plugins/done/DonePlugin.svelte";
