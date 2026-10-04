# Defining the step order

The order of steps is not configured declaratively. Instead, you register one callback that authentikt calls
every time it needs to know what comes next:

```kotlin
installAuthentikt<User> {
    // install(...) your plugins

    authorization { session ->
        // return the next plugin
    }
}
```

The callback has the type `suspend (session: Session<USER>) -> BasePlugin<USER, *>`. It is called:

- on the first `check` request of a session, to choose the first step, and
- every time a plugin calls `session.nextStep()`, which the built-in plugins do after a successful submission.

The returned plugin must have been registered with `install(...)`. Otherwise, the request fails with
`NotInstalledPluginCalled`.

## Writing the callback

Think of the callback as a function from "what has happened so far" to "what is still missing". The most useful
inputs are:

- `session.identifiedUser`: `null` until a user has been identified. `identifiedUser.user` is your own user object.
- `session.has(plugin)`: whether a step has been completed.
- `session.destination`: whether the login belongs to a device in the [device flow](oauth-device-flow.md).
- `session.attributes`: any data you stored yourself.

### Password, then optional TOTP

```kotlin
authorization { session ->
    val user = session.identifiedUser
    when {
        user == null -> emailPlugin
        !session.has(passwordPlugin) -> passwordPlugin
        user.user.totpSecret != null && !session.has(totpPlugin) -> totpPlugin
        else -> donePlugin
    }
}
```

### Single sign-on only

The OIDC plugin identifies the user itself, so no email step is needed:

```kotlin
authorization { session ->
    if (!session.has(oidcPlugin)) oidcPlugin else donePlugin
}
```

### Stricter rules for device logins

```kotlin
authorization { session ->
    val user = session.identifiedUser
    val isDevice = session.destination is SessionDestination.DeviceFlow
    when {
        user == null -> emailPlugin
        !session.has(passwordPlugin) -> passwordPlugin
        isDevice && !session.has(totpPlugin) -> totpPlugin
        else -> donePlugin
    }
}
```

## Rules of thumb

> **Always end in the `DonePlugin`.** It is the only built-in step that issues credentials. If the callback never
> returns it, the user gets stuck after the last successful step.
{style="note"}

- **Never return a completed step again.** If the callback returns a plugin that is already completed, the session
  enters it a second time with a fresh state, and the user has to repeat it.
- **Keep it deterministic.** The callback runs once per transition. It should not have side effects such as sending
  emails. Put those into a plugin's `createState` or its routes.
- **Guard user-dependent steps.** Steps like password or TOTP read `session.identifiedUser!!`. Only return them
  after an identification step has run.
