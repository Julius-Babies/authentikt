# Debug overlay

authentikt-svelte includes a small, draggable overlay that shows the live `currentFlow` state as a JSON tree: the
session ID, the current step and its payload, the identified user, public attributes and the destination.

## Enabling it

Turn it on in the provider configuration:

```svelte
<Authentikt config={{ baseUrl: "https://example.com/api/authentikt/", debug: true }}>
    ...
</Authentikt>
```

`debug` accepts:

| Value | Effect |
|-------|--------|
| `false` / omitted | No overlay, no debug warnings |
| `true` | Overlay and debug warnings |
| `{ show_overlay: true }` | Overlay and debug warnings |
| `{ show_overlay: false }` | Debug warnings only, such as the `baseUrl` correction notice |

Enable it based on the environment, so it never ships to production:

```ts
import { dev } from "$app/environment";

const config = { baseUrl: "...", debug: { show_overlay: dev } };
```

## Placing it yourself

The overlay is also exported as a component, if you want to show it somewhere else or for a specific client:

```svelte
<script lang="ts">
    import { AuthentiktDebug, useAuthentiktContext } from "@julius-babies/authentikt-svelte";
    const auth = useAuthentiktContext();
</script>

<AuthentiktDebug authentikt={auth} />
```

## What to look for

| Symptom | Check in the overlay |
|---------|----------------------|
| Nothing renders during a flow | `step.namespace`: is a renderer for this namespace mounted? |
| Renderer stays on the same step after submitting | `step.payload`: did the server mark the step as completed? |
| "Signing in to ..." is missing | `destination.type`: is it `none`? |
| Custom attribute is missing | `attributes`: only `publicAttributes` are sent to the client |
