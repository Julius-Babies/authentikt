/**
 * @module authentikt-svelte
 *
 * Svelte 5 client library for the authentikt authentication flow.
 *
 * ## Quick Start
 * ```svelte
 * <script>
 *   import { Authentikt, EmailUserSelectionRenderer, PasswordRenderer } from "authentikt-svelte";
 * </script>
 *
 * <Authentikt baseUrl="http://localhost:8080/authentikt/">
 *   <EmailUserSelectionRenderer />
 *   <PasswordRenderer />
 * </Authentikt>
 * ```
 *
 * ## Architecture
 * - **Plugin interfaces** (`PluginLike`) define the contract for authentication steps
 *   with Svelte 5 runes for reactive state.
 * - **Plugin registry** on the `Authentikt` instance stores `{ namespace, factory, component }`
 *   entries. Plugins can be registered manually or via premade renderer components.
 * - **Premade renderers** (`PasswordRenderer`, `TotpRenderer`, etc.) self-register on mount
 *   and provide both default UI and overridable snippet-based customisation.
 */
export type {
    AuthentiktConfiguration,
    FlowState,
    FlowStepData,
    FlowUserState,
    FlowDestination,
} from "./AuthentiktConfiguration.svelte";

export { default as Authentikt } from "./Authentikt.svelte";
export { default as AuthentiktDebug } from "./AuthentiktDebug.svelte";

export type { PluginLike, PluginComponentProps, PluginEntry } from "./plugins/Plugin.types";

export type { PasswordStatus, PasswordSnippet, PasswordPluginInstance } from "./plugins/password/types";
export type { TotpStatus, TotpSnippet, TotpPluginInstance } from "./plugins/totp/types";
export type {
    EmailUserSelectionStatus,
    EmailUserSelectionSnippet,
    EmailUserSelectionPayload,
    EmailUserSelectionPluginInstance
} from "./plugins/email/types";

export type { User } from "./user";

export { PasswordPlugin } from "./plugins/password/PasswordPlugin.svelte";
export { default as PasswordRenderer } from "./plugins/password/PasswordRenderer.svelte";
export { TotpPlugin } from "./plugins/totp/TotpPlugin.svelte";
export { default as TotpRenderer } from "./plugins/totp/TotpRenderer.svelte";
export { EmailUserSelectionPlugin } from "./plugins/email/EmailUserSelectionPlugin.svelte";
export { default as EmailUserSelectionRenderer } from "./plugins/email/EmailUserSelectionRenderer.svelte";
export { DonePlugin } from "./plugins/done/DonePlugin.svelte";
export { default as DoneRenderer } from "./plugins/done/DoneRenderer.svelte";
export { OIDCPlugin } from "./plugins/oidc/OIDCPlugin.svelte";
export { default as OIDCRenderer } from "./plugins/oidc/OIDCRenderer.svelte";
export { useAuthentiktContext as useAuthentiktContext } from "./context.ts"