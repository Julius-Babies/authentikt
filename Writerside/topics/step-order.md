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

The callback has the type `suspend (session: Session<USER>) -> NextStep<USER>`. A plugin is a `NextStep`, so you can
return it directly. The callback is called:

- on the first `check` request of a session, to choose the first step, and
- every time a plugin calls `session.nextStep()`, which the built-in plugins do after a successful submission.

The returned plugin and its [alternatives](#alternatives) must have been registered with `install(...)`. Otherwise,
the request fails with `NotInstalledPluginCalled`.

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

## Alternatives {id="alternatives"}

A step can offer alternatives the user may take instead, for example single sign-on next to the email step. Attach
them with `alternative`:

```kotlin
authorization { session ->
    when {
        !session.has(emailPlugin) && !session.has(oidcPlugin) -> emailPlugin alternative listOf(oidcPlugin)
        else -> donePlugin
    }
}
```

The email step is shown, and the client lists the OIDC step below it. When the user selects an alternative, it
replaces the active step on the session's step stack and starts with a fresh state. The replaced step becomes an
alternative itself, so the user can switch back.

Switching does not call the step-order callback. It is called again once the alternative has been completed, so
check for every step of the group, as `!session.has(emailPlugin) && !session.has(oidcPlugin)` does above. Otherwise,
the callback returns the original step again.

Alternatives only apply to the step they were returned with. The next step starts without alternatives unless the
callback returns some again.

## Prepared steps {id="prepared-steps"}

Some plugins take input from the callback, like a constructor. They start with a prepared state instead of the one
from `createState`. The [junction](junction-plugin.md) is started with the options the user chooses from:

```kotlin
!session.has(junctionPlugin) -> junctionPlugin(listOf(totpPlugin, passkeyPlugin, emailCodePlugin))
```

Prepared steps can be used anywhere a plugin can, including as alternatives:

```kotlin
passwordPlugin alternative listOf(junctionPlugin(listOf(passkeyPlugin, emailCodePlugin)))
```

To offer this in your own plugin, see [](custom-step-plugins.md#prepared-state).

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
