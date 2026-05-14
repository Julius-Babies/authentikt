# authentikt-svelte

Svelte 5 client library for the authentikt authentication flow framework.

## Installation

```sh
npm install authentikt-svelte
```

## Quick start

```svelte
<script>
  import {
    Authentikt,
    AuthentiktUserSelectionRenderer,
    AuthentiktStepRenderer,
    PasswordRenderer,
  } from "authentikt-svelte";
  import { useAuthentiktContext } from "authentikt-svelte/context";
  import { Button } from "your-ui-lib";
</script>

<Authentikt baseUrl="http://localhost:8080/authentikt/">
  {@const auth = useAuthentiktContext()}

  {#if !auth.currentFlow}
    <Button onclick={auth.startLoginFlow}>Login</Button>
  {:else}
    <div class="flow-modal">
      <button onclick={auth.cancelFlow}>✕</button>

      <!-- Auto-render user selection -->
      <AuthentiktUserSelectionRenderer />

      <!-- Custom password UI with snippet override -->
      <PasswordRenderer>
        {#snippet children(plugin)}
          <form onsubmit|preventDefault={plugin.submit}>
            <input bind:value={plugin.password} type="password" placeholder="Password" />
            {#if plugin.status === "password_incorrect"}
              <p class="error">Incorrect password</p>
            {/if}
            <button type="submit" disabled={plugin.status === "loading"}>
              {plugin.status === "loading" ? "Checking..." : "Continue"}
            </button>
          </form>
        {/snippet}
      </PasswordRenderer>

      <!-- Auto-render remaining steps (TOTP, done, etc.) -->
      <AuthentiktStepRenderer />
    </div>
  {/if}
</Authentikt>
```

## Architecture

### Plugin system

Plugins follow a **headless pattern**: logic (state + actions) is separated from UI (renderer component).

```
registerStepPlugin(namespace, Component, factory)
  ├── namespace — matches the server-side plugin
  ├── Component — Svelte component that renders the UI
  └── factory   — creates the plugin instance (reactive via $state runes)
```

Two plugin categories:

| Category | Interface | Purpose | Examples |
|----------|-----------|---------|----------|
| Step plugins | `StepPluginLike` | Authentication steps | password, TOTP, done |
| User-selection plugins | `UserSelectionPluginLike` | User identification | email, username |

### Components

| Component | Purpose |
|-----------|---------|
| `<Authentikt>` | Context provider — creates the `Authentikt` instance |
| `<AuthentiktStepRenderer>` | Auto-renders the active step plugin |
| `<AuthentiktUserSelectionRenderer>` | Auto-renders active user-selection plugins |
| `<PasswordRenderer>` | Password step (default UI + snippet override) |
| `<TotpRenderer>` | TOTP step (default UI + snippet override) |
| `<EmailUserSelectionRenderer>` | Email user selection (default UI + snippet override) |
| `<DoneRenderer>` | Token receipt / completion step |

### Override any part

**Custom component for a built-in plugin:**
```ts
const auth = useAuthentiktContext();
auth.registerStepPlugin(
  "authentikt-builtin/password",
  MyPasswordComponent,
  (auth, ns) => new PasswordPlugin(auth, ns)
);
```

**Custom plugin with built-in renderer:**
```ts
auth.registerStepPlugin(
  "my-auth/sms-code",
  PasswordRenderer, // just the default UI
  (auth, ns) => new MySmsPlugin(auth, ns)
);
```

**Fully custom:**
```ts
auth.registerStepPlugin("my-auth/sms-code", MyRenderer, MyPlugin);
```

## Development

```sh
cd frontend-svelte
npm install
npm run dev          # dev server with showcase app
npm run check        # typecheck
npm run prepack      # build library output → dist/
npm run docs         # generate API docs → docs/svelte/
```

## Documentation

Generate API docs with TypeDoc:

```sh
npm run docs
# open docs/svelte/index.html
```
