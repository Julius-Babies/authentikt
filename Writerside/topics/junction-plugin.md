# Junction

`JunctionPlugin` lets the user choose which step to take next, for example between TOTP, a passkey and an email
code. It is only a shell: the options are passed in from the step-order callback, and the selection is stored in
the junction's state.

**Namespace:** `authentikt-builtin/junction` (configurable)

## Configuration

```kotlin
val junctionPlugin = JunctionPlugin<User>()
```

The junction has no configuration. To use more than one junction in a flow, install several with different
namespaces, for example `JunctionPlugin<User>(namespace = "acme/second-factor")`.

## Usage

Start the junction with its options by calling it like a constructor. Read the selection with
`selectedOption(session)` and return it:

```kotlin
authorization { session ->
    val user = session.identifiedUser
    val secondFactors = listOf(totpPlugin, passkeyPlugin, emailCodePlugin)
    when {
        user == null -> emailPlugin
        !session.has(passwordPlugin) -> passwordPlugin
        !session.has(junctionPlugin) -> junctionPlugin(secondFactors)
        secondFactors.none { session.has(it) } -> junctionPlugin.selectedOption(session)!!
        else -> donePlugin
    }
}
```

`junctionPlugin(options)`
: Starts the junction with the given options. Options can be plugins or [prepared steps](step-order.md#prepared-steps).

`selectedOption(session)`
: The option the user selected, or `null` if the junction has not been completed. If the option is a prepared step,
it is returned with its prepared state.

Combine the selection with [alternatives](step-order.md#alternatives) to let the user change their mind later:

```kotlin
val selected = junctionPlugin.selectedOption(session)!!
selected alternative (secondFactors - selected)
```

## Behaviour

- Returning `junctionPlugin` without calling it starts the junction without options.
- Selecting an option that was not passed to the junction is answered with `400 Bad Request`.
- On a valid selection, the step is marked completed and `session.nextStep()` is called.

## HTTP contract

**Payload**

```json
{ "options": ["authentikt-builtin/totp", "acme/passkey"], "selected": null }
```

**Request:** `POST /flow/{sessionId}/steps/plugins/authentikt-builtin/junction`

```json
{ "namespace": "authentikt-builtin/totp" }
```

**Response**

```json
{ "type": "success" }
```

## Frontend

Use [`JunctionRenderer`](frontend-renderers.md#junction). Its plugin instance exposes `options`, `status`
(`"ready" | "loading" | "error"`) and `select(namespace)`.
