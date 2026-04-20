# AGENTS.md

## Project Goal
`authentikt` is a Kotlin/Ktor authentication flow library with a sample app that demonstrates how to wire user lookup, multi-step authentication, and session-backed routing.

The core module provides the authentication flow engine and built-in steps. The `samples` module shows how to embed it in a Ktor server and expose login and protected API routes. The `frontend-svelte` module is a Svelte 5 client library and showcase app for rendering authentikt flows in the browser.

## Repository Structure
- `core/` - reusable authentication library code.
- `samples/` - runnable Ktor sample application.
- `frontend-svelte/` - Svelte 5 library and showcase app.
- `gradle/libs.versions.toml` - dependency and plugin catalog.
- `settings.gradle.kts` - declares the multi-module build.

## Core Module
The `core` module contains the library entry point and auth primitives.

- `core/src/main/kotlin/es/jvbabi/authentikt/core/Main.kt` installs the auth routes into a Ktor `Application`.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/AuthentiktUser.kt` defines the user wrapper type used by plugins and flows.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/routes/` contains flow endpoints such as session status checks and plugin routes.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/session/` manages session state and route-scoped session access.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/step/plugins/` contains built-in auth steps such as password, TOTP, and done.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/userselection/plugins/` contains user-selection plugin APIs and built-ins.

## Sample Module
The `samples` module is the reference application.

- `samples/src/main/kotlin/es/jvbabi/authentikt/samples/Main.kt` defines demo users, installs the authentikt plugin, and exposes `/login`, `/api/user/me`, and `/logout`.
- `samples/src/main/resources/application.yaml` configures the Ktor module and server port.

## Frontend Svelte Module
The `frontend-svelte` module is a Svelte 5 component library with a local showcase.

- `frontend-svelte/src/lib/AuthentiktConfiguration.ts` holds client flow state and API wiring.
- `frontend-svelte/src/lib/AuthentiktView.svelte` renders user-selection and step plugins based on server flow status.
- `frontend-svelte/src/lib/plugins/` contains step plugin classes and renderers.
- `frontend-svelte/src/lib/user-selection/plugins/` contains user-selection plugin classes and renderers.
- `frontend-svelte/src/routes/+layout.svelte` wires the showcase app.

## Main Flow
1. `Application.installAuthentikt { ... }` configures the auth pipeline and mounts routes under `/authentikt`.
2. A client starts a login flow by creating a session.
3. The flow checks session status and renders installed user-selection or step plugins in order.
4. The sample demonstrates email user selection, password, optional TOTP, and final token generation.
5. Protected routes use a cookie-based auth provider in the sample app.

## Build Notes
- Kotlin target: JVM 25.
- The project uses Gradle with version-catalog-managed dependencies.
- The sample app runs on Ktor Netty and listens on port `8080` by default.

## Working Convention
- Prefer small, targeted changes.
- Keep library code in `core` and example wiring in `samples`.
- When adding new auth steps or route handlers, update the sample if behavior should be demonstrated end-to-end.
- Keep frontend-svelte aligned with backend flow contracts when response payloads or routes change.

## Svelte 5 Hints
- Use runes-style APIs (`$state`, `$derived`, `$effect`, `$props`) consistently in `.svelte` files.
- Prefer typed snippets for extensible render APIs (for example `Snippet<[...args]>`).
- Keep plugin renderers isolated: each plugin owns its submit/fetch behavior and active-step checks.
- In Svelte library code, favor explicit type exports from `src/lib/index.ts` when adding plugin types.
- For browser-only behavior in TS modules, guard with `typeof window !== "undefined"`.
