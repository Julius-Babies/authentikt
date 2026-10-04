# Writing a step plugin

If the built-in plugins don't cover your needs, write your own: a magic link, an SMS code, a WebAuthn assertion, a
captcha, a terms-of-service confirmation. A plugin is a class with a namespace, a state type and some Ktor routes.

This page builds a plugin that makes users accept the current terms of service before they are logged in. The
matching frontend part is described in [](frontend-custom-plugins.md).

## Anatomy

```kotlin
abstract class BasePlugin<USER, STATE : BaseState>(val namespace: String) {
    protected val logger: Logger

    abstract suspend fun createState(session: Session<*>): STATE
    abstract fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>)
    open fun installStaticRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {}
}

interface BaseState {
    suspend fun isCompleted(): Boolean
    suspend fun createClientState(session: Session<*>): Map<String, Any?>
}
```

`namespace`
: A unique, stable identifier. It is part of the URL and is how the frontend finds the right renderer. Use a
group/project form like `acme/terms` or reverse-domain notation like `com.acme.authentikt.terms`. The
`authentikt-builtin/` prefix is reserved.

`createState(session)`
: Called when the step-order callback selects your plugin. Return a fresh, *not completed* state. You can also
trigger side effects here, such as sending a code by SMS.

`installRoutes(inRoute, instance)`
: Called once at startup. `inRoute` is already scoped to `{apiPrefix}/authentikt/flow/{sessionId}/steps/plugins/{namespace}`.
Get the session with `call.attributes[SessionKey]`.

`installStaticRoutes(inRoute, instance)`
: Optional. `inRoute` is scoped to `{apiPrefix}/authentikt/static/plugins/{namespace}` and is not tied to a session.
Use it for callbacks from external systems, as the [OIDC plugin](oidc-plugin.md) does.

`BaseState.isCompleted()`
: What `session.has(plugin)` checks.

`BaseState.createClientState(session)`
: The `payload` the client receives in the `check` response. It is serialized with Gson.

## Completing a step

When your route decides the step is done, it calls `session.completeStep(plugin, state)`. It replaces the step's
state with the completed one and calls `session.nextStep()`, which asks the step-order callback for the next plugin.

Several requests for the same session can run at the same time, for example a double-clicked submit button.
`completeStep` only advances the flow if your plugin is still the active step, and returns `false` otherwise. Answer
such requests with `call.respondStepNotActive()` (`409 Conflict`, from `es.jvbabi.authentikt.core.routes.flow`):

```kotlin
if (!session.completeStep(this@MyPlugin, MyState(completed = true))) {
    return@post call.respondStepNotActive()
}
```

To change the session together with the transition, for example to set `identifiedUser`, pass a block. It runs after
the active-step check and before `nextStep()`:

```kotlin
session.completeStep(this@MyPlugin, MyState(completed = true)) { identifiedUser = user }
```

Don't modify `session.authenticationSteps` directly. See [](sessions.md#concurrency).

## Example: terms of service

### State

```kotlin
class TermsState(
    val accepted: Boolean,
    private val version: String,
) : BaseState {
    override suspend fun isCompleted(): Boolean = accepted

    override suspend fun createClientState(session: Session<*>): Map<String, Any?> = buildGenericMap {
        put("version", version)
        put("accepted", accepted)
    }
}
```

### Plugin

```kotlin
@Serializable
data class TermsRequest(val accepted: Boolean)

class TermsPlugin<USER>(
    private val currentVersion: String,
    private val onAccepted: suspend (user: USER, version: String) -> Unit,
) : BasePlugin<USER, TermsState>(namespace = "acme/terms") {

    override suspend fun createState(session: Session<*>): TermsState =
        TermsState(accepted = false, version = currentVersion)

    override fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {
        inRoute.post {
            val session = call.attributes[SessionKey] as Session<USER>

            // Skip stale requests early. completeStep checks this again atomically.
            if (!session.isActive(this@TermsPlugin)) return@post call.respondStepNotActive()

            val request = call.receive<TermsRequest>()
            if (!request.accepted) {
                call.respondGson(buildGenericMap { put("success", false) })
                return@post
            }

            onAccepted(session.identifiedUser!!.user, currentVersion)
            logger.info("Session ${session.sessionId} accepted terms $currentVersion")

            if (!session.completeStep(this@TermsPlugin, TermsState(accepted = true, version = currentVersion))) {
                return@post call.respondStepNotActive()
            }

            call.respondGson(buildGenericMap { put("success", true) })
        }
    }
}
```

`respondGson` and `buildGenericMap` are small helpers from `es.jvbabi.authentikt.core.utils`. They serialize
`Map<String, Any?>` values without needing `@Serializable` classes. You can use `call.respond` instead.

### Wiring it up

```kotlin
val termsPlugin = TermsPlugin<User>(currentVersion = "2026-01") { user, version ->
    userRepository.markTermsAccepted(user, version)
}

installAuthentikt<User> {
    install(emailPlugin)
    install(passwordPlugin)
    install(termsPlugin)
    install(donePlugin)

    authorization { session ->
        val user = session.identifiedUser
        when {
            user == null -> emailPlugin
            !session.has(passwordPlugin) -> passwordPlugin
            user.user.acceptedTermsVersion != "2026-01" && !session.has(termsPlugin) -> termsPlugin
            else -> donePlugin
        }
    }
}
```

## Adding a configuration DSL

The built-in plugins take a builder lambda instead of constructor parameters. If you want the same style, follow
their pattern:

```kotlin
class TermsPluginConfigurationBuilder<USER> {
    var currentVersion: String = ""
    private var onAccepted: (suspend (USER, String) -> Unit)? = null

    fun onAccepted(block: suspend (user: USER, version: String) -> Unit) {
        onAccepted = block
    }

    internal fun build(): TermsPluginConfiguration<USER> {
        require(currentVersion.isNotEmpty()) { "currentVersion must be set" }
        return TermsPluginConfiguration(currentVersion, requireNotNull(onAccepted) { "onAccepted must be set" })
    }
}

data class TermsPluginConfiguration<USER>(
    val currentVersion: String,
    val onAccepted: suspend (USER, String) -> Unit,
)

class TermsPlugin<USER>(
    configuration: TermsPluginConfigurationBuilder<USER>.() -> Unit,
) : BasePlugin<USER, TermsState>(namespace = "acme/terms") {
    private val configuration = TermsPluginConfigurationBuilder<USER>().apply(configuration).build()
    // ...
}
```

## Checklist

- The namespace is unique and matches the frontend registration.
- `createState` returns a state that is *not* completed.
- On success: call `session.completeStep(plugin, completedState)` and answer `false` with `respondStepNotActive()`.
- Failed attempts are limited if the step guards a secret (codes, passwords).
- The plugin is passed to `install(...)` and returned by the step-order callback.
