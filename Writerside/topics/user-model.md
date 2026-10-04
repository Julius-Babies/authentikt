# User model

authentikt never touches your database. Plugins receive your own user objects, and the library only needs a thin
adapter to read basic profile information. That adapter is `AuthentiktUser<USER>`:

```kotlin
abstract class AuthentiktUser<USER>(val user: USER) {
    abstract suspend fun getEmail(): String?
    abstract suspend fun getUsername(): String?
    abstract suspend fun getDisplayName(): String?
}
```

| Function | Used for |
|----------|----------|
| `getEmail()` | Your own logic. Built-in plugins look users up through your callbacks, not through this function |
| `getUsername()` | Returned to the client after email identification (`username`) |
| `getDisplayName()` | Returned to the client after email identification (`display_name`), for example for "Welcome back, Eric" |

All functions are `suspend`, so you can load data lazily.

## Implementing it

An anonymous object next to your user type is usually enough:

```kotlin
fun User.toAuthentiktUser() = object : AuthentiktUser<User>(this) {
    override suspend fun getEmail(): String? = email
    override suspend fun getUsername(): String? = username
    override suspend fun getDisplayName(): String? = displayName
}
```

Identification plugins return an `AuthentiktUser`, for example from `findUserByEmail` or `onUserInfo`. All
other callbacks, such as `checkPassword`, `getSecret` and `onSuccess`, receive the unwrapped `USER`, so you work
with your own type there.

In the step-order callback, use `session.identifiedUser?.user` to get your own object back.
