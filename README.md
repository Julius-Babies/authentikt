# authentikt

> [!NOTE]
> This project is in an early stage of development. AI is used to generate much of the code since I need this library for my own projects.
> As time moves on, the quality of the library will be improved.

**authenti**cation for **K**otlin/**T**ypeScript — a monorepo with a Ktor authentication flow library and its Svelte frontend client.

## Structure

| Directory                               | Language            | Purpose                                                                                             |
|-----------------------------------------|---------------------|-----------------------------------------------------------------------------------------------------|
| [`core/`](./core)                       | Kotlin              | Ktor server plugin — pluggable auth steps, session management, built-in password/TOTP/email plugins |
| [`samples/`](./samples)                 | Kotlin              | Runnable Ktor sample application demonstrating how to wire plugins                                  |
| [`frontend-svelte/`](./frontend-svelte) | TypeScript/Svelte 5 | Svelte client library with rune-based plugin system and auto-renderers                              |

## How it works

1. A client starts a flow by creating a session on the server.
2. The server responds with a **user-selection** step (e.g. enter email).
3. The frontend renders the appropriate user-selection plugin, submits the identifier.
4. The server identifies the user and responds with an **auth step** (e.g. password, TOTP).
5. Each step is rendered by its registered plugin component.
6. When all steps pass, the server generates a token and completes the flow.

## Building

### Ktor (server)

```sh
./gradlew :core:build
```

### Svelte (client)

```sh
cd frontend-svelte
npm install
npm run prepack
```

## Documentation

- **Ktor API docs**: `./gradlew :core:dokkaGenerateHtml` → `core/build/dokka/html/index.html`
- **Svelte API docs**: `cd frontend-svelte && npm run docs` → `frontend-svelte/docs/svelte/`
