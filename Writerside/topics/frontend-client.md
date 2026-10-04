# The Authentikt client

`Authentikt` is the class behind `<Authentikt>`. It holds the flow state as Svelte 5 `$state`, provides the actions
to start and end flows, and keeps a registry of client plugins. Get it with `useAuthentiktContext()`.

## Flow state

`currentFlow: FlowState | null`
: `null` while no flow is running. All fields are reactive.

```ts
interface FlowState {
    session_id: string;
    step: FlowStepData | null;          // null until the first check returned
    user: FlowUserState | null;         // set by identification plugins
    attributes: Record<string, unknown>; // the session's public attributes
    destination: FlowDestination;       // who the login is for
}

type FlowStepData =
    | { type: "step"; namespace: string; payload?: Record<string, unknown> }
    | { type: "finished" };

interface FlowUserState {
    username: string;
    displayName: string;
}

type FlowDestination =
    | { type: "none" }
    | { type: "device_flow"; application_id: string; application_name: string };
```

`activeStepPlugin`
: The plugin instance for the current step, or `null`. Derived from `currentFlow.step`.

`activeStepEntry`
: The registry entry (`{ namespace, factory, component }`) for the current step, or `null`. You can use it to render
the active plugin's component generically.

`sessionUrl: URL`
: `{baseUrl}flow/{sessionId}/`, the base for all step requests. Useful in custom plugins.

## Actions {id="actions"}

All actions are arrow-function properties, so you can pass them directly as event handlers
(`onclick={auth.startLoginFlow}`).

`startLoginFlow(): Promise<void>`
: Requests `GET /api/login` (relative to the origin of `baseUrl`), stores the returned `session_id`, writes it into
the URL and loads the first step.

`linkToFlow(sessionId: string): Promise<void>`
: Attaches the client to an existing session, for example one your own endpoint created. It writes the ID into the
URL and loads the current step.

`cancelFlow(): Promise<void>`
: Removes the flow parameters from the URL, sets `currentFlow` to `null` and discards plugin instances. The server
session is not deleted; it expires on its own after the server's `sessionTimeout`.

`updateState(): Promise<void>`
: Requests `GET {sessionUrl}check` and updates `step`, `user`, `attributes` and `destination`. Plugins call it after a
successful submission. Call it yourself if something outside the client advanced the flow. If the server answers
with `404` because the session expired or was already completed, the flow is cancelled (see `cancelFlow()`).

`setUser(user: FlowUserState | null): void`
: Sets `currentFlow.user`. The email plugin calls it after identifying a user. `updateState()` also sets it from the
server response, so the user is restored after a page reload.

## Resuming from the URL

While a flow is running, the client keeps two query parameters in the browser URL:

```
?_authentikt_flow_active=true&_authentikt_session_id=<session id>
```

When the client is created and finds these parameters, it restores the flow and loads the current step. This
makes the flow survive page reloads, and it is how the server hands the browser back after
[OIDC logins](oidc-plugin.md) and in the [device flow](oauth-device-flow.md).

## Plugin registry

Client plugins are registered by namespace. The built-in renderers register themselves on mount. For custom
plugins, see [](frontend-custom-plugins.md).

`registerPlugin<T>(namespace, component, factory): T`
: Registers a plugin and returns its instance. If the namespace is already registered, only the component is
replaced, and the existing instance is kept.

`getPlugin<T>(namespace): T`
: Returns the instance for a namespace and creates it lazily. Throws if nothing is registered for that namespace.

`pluginInstance(namespace): PluginLike | undefined`
: Returns an existing instance without creating one.

`linkStepPlugin(namespace, component, createInstance)` (deprecated)
: Old registration API. Use `registerPlugin`.

Plugin instances are discarded when a flow starts, is linked or is cancelled, so every flow begins with fresh input
fields and status values.

## Rendering the active step generically {id="generic-rendering"}

Instead of listing every renderer, you can render whatever component is registered for the current step. The
renderers must have been mounted once to register themselves, or you register them manually:

```svelte
{#if auth.activeStepEntry && auth.activeStepPlugin}
    {@const Active = auth.activeStepEntry.component}
    <Active plugin={auth.activeStepPlugin} user={auth.currentFlow?.user} />
{/if}
```

All built-in renderers accept an optional `plugin` prop for this purpose.
