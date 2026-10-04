# Installation

authentikt has a server part (Ktor) and an optional client part (Svelte). You can use the server library on its
own with any frontend, as long as the frontend speaks the [](http-api.md).

## Requirements

| Component | Requirement |
|-----------|-------------|
| JVM | Java 25 (the library is compiled with `jvmToolchain(25)`) |
| Ktor | 3.x server (the library is built against Ktor 3.4) |
| Frontend | Svelte 5 inside a **SvelteKit** application (the client uses `$app/navigation` and `$app/state`) |

## Server: authentikt-core

The core library is published to Maven Central under `es.jvbabi.authentikt:core`.

<tabs>
<tab title="Version catalog">

Add the library to `gradle/libs.versions.toml`:

```toml
[versions]
authentikt = "0.4.8"

[libraries]
authentikt-core = { module = "es.jvbabi.authentikt:core", version.ref = "authentikt" }
```

Then reference it in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.authentikt.core)
}
```

</tab>
<tab title="build.gradle.kts">

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("es.jvbabi.authentikt:core:0.4.8")
}
```

</tab>
</tabs>

> Check [Maven Central](https://central.sonatype.com/artifact/es.jvbabi.authentikt/core) or the
> [GitHub tags](https://github.com/Julius-Babies/authentikt/tags) for the latest version.
{style="tip"}

### Ktor plugins your application must install {id="ktor-plugins"}

The built-in plugins read JSON request bodies with `call.receive<T>()`, and the password plugin answers with
`call.respond(...)`. Both rely on Ktor's content negotiation, so install it with kotlinx.serialization JSON:

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-content-negotiation:3.4.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.2")
}
```

```kotlin
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    // installAuthentikt { ... }
}
```

If your frontend runs on a different origin during development, you will also need
[CORS](https://ktor.io/docs/server-cors.html). See [](frontend-setup.md#same-origin) for why the production setup
should be same-origin.

## Client: authentikt-svelte

The Svelte library is published to npm as `@julius-babies/authentikt-svelte`.

<tabs>
<tab title="bun">

```bash
bun add @julius-babies/authentikt-svelte
```

</tab>
<tab title="npm">

```bash
npm install @julius-babies/authentikt-svelte
```

</tab>
</tabs>

The package has `svelte@^5` as a peer dependency. The default renderers use Tailwind CSS utility classes. Without
Tailwind they still work, but they will be unstyled. Most applications replace the default UI with their own
snippets anyway, see [](frontend-renderers.md).

## Next step

Continue with [](quick-start.md) to wire up a complete login flow.
