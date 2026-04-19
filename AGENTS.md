# AGENTS.md

## Project Goal
`authentikt` is a Kotlin/Ktor authentication flow library with a sample app that demonstrates how to wire user lookup, multi-step authentication, and session-backed routing.

The core module provides the authentication flow engine and built-in steps. The `samples` module shows how to embed it in a Ktor server and expose login and protected API routes.

## Repository Structure
- `core/` - reusable authentication library code.
- `samples/` - runnable Ktor sample application.
- `gradle/libs.versions.toml` - dependency and plugin catalog.
- `settings.gradle.kts` - declares the multi-module build.

## Core Module
The `core` module contains the library entry point and auth primitives.

- `core/src/main/kotlin/es/jvbabi/authentikt/core/Main.kt` installs the auth routes into a Ktor `Application`.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/AuthentiktUser.kt` defines the user wrapper type used by plugins and flows.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/AuthentiktUserSource.kt` defines the user lookup contract.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/routes/` contains the flow endpoints such as session status checks and email login.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/session/` manages session state and route-scoped session access.
- `core/src/main/kotlin/es/jvbabi/authentikt/core/step/plugins/` contains built-in auth steps such as password, TOTP, and done.

## Sample Module
The `samples` module is the reference application.

- `samples/src/main/kotlin/es/jvbabi/authentikt/samples/Main.kt` defines demo users, installs the authentikt plugin, and exposes `/login`, `/api/user/me`, and `/logout`.
- `samples/src/main/resources/application.yaml` configures the Ktor module and server port.

## Main Flow
1. `Application.installAuthentikt { ... }` configures the auth pipeline and mounts routes under `/authentikt`.
2. A client starts a login flow by creating a session.
3. The flow checks session status and runs installed plugins in order.
4. The sample demonstrates password, optional TOTP, and final token generation.
5. Protected routes use a cookie-based auth provider in the sample app.

## Build Notes
- Kotlin target: JVM 25.
- The project uses Gradle with version-catalog-managed dependencies.
- The sample app runs on Ktor Netty and listens on port `8080` by default.

## Working Convention
- Prefer small, targeted changes.
- Keep library code in `core` and example wiring in `samples`.
- When adding new auth steps or route handlers, update the sample if behavior should be demonstrated end-to-end.
