/**
 * @module authentikt-svelte
 *
 * Svelte 5 client library for the authentikt authentication flow.
 *
 * ## Quick Start
 * ```svelte
 * <script>
 *   import { Authentikt, AuthentiktUserSelectionRenderer, AuthentiktStepRenderer } from "authentikt-svelte";
 * </script>
 *
 * <Authentikt baseUrl="http://localhost:8080/authentikt/">
 *   <AuthentiktUserSelectionRenderer />
 *   <AuthentiktStepRenderer />
 * </Authentikt>
 * ```
 *
 * ## Architecture
 * - **Plugin interfaces** (`StepPluginLike`, `UserSelectionPluginLike`) define the contract
 *   for authentication steps with Svelte 5 runes for reactive state.
 * - **Plugin registry** on the `Authentikt` instance stores `{ namespace, factory, component }`
 *   entries. Plugins can be registered manually or via premade renderer components.
 * - **Auto-renderers** (`AuthentiktStepRenderer`, `AuthentiktUserSelectionRenderer`)
 *   look up the active step in the registry and render the appropriate component
 *   with the plugin instance and user context as props.
 * - **Premade renderers** (`PasswordRenderer`, `TotpRenderer`, etc.) self-register on mount
 *   and provide both default UI and overridable snippet-based customisation.
 */
export type {
    AuthentiktConfiguration,
    FlowState,
    FlowStepData,
    FlowUserState
} from "./AuthentiktConfiguration.svelte";

export { default as Authentikt } from "./Authentikt.svelte";
export { default as AuthentiktView } from "./AuthentiktView.svelte";
export { default as AuthentiktStepRenderer } from "./AuthentiktStepRenderer.svelte";
export { default as AuthentiktUserSelectionRenderer } from "./AuthentiktUserSelectionRenderer.svelte";

export type { StepPluginLike, StepPluginComponentProps, StepPluginEntry } from "./plugins/StepPlugin.types";
export type { UserSelectionPluginLike, UserSelectionPluginComponentProps, UserSelectionPluginEntry } from "./plugins/UserSelectionPlugin.types";

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
export { default as PasswordRenderer } from "./plugins/password/PasswordRenderer.svelte";
export { TotpPlugin } from "./plugins/totp/TotpPlugin.svelte";
export { default as TotpRenderer } from "./plugins/totp/TotpRenderer.svelte";
export { EmailUserSelectionPlugin } from "./user-selection/plugins/email/EmailUserSelectionPlugin.svelte";
export { default as EmailUserSelectionRenderer } from "./user-selection/plugins/email/EmailUserSelectionRenderer.svelte";
export { DonePlugin } from "./plugins/done/DonePlugin.svelte";
export { default as DoneRenderer } from "./plugins/done/DoneRenderer.svelte";
