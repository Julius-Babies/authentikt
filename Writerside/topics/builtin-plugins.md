# Built-in plugins

authentikt ships with five step plugins. Each one has a matching renderer in authentikt-svelte.

| Plugin | Namespace | Role | Svelte renderer |
|--------|-----------|------|-----------------|
| [](email-plugin.md) | `authentikt-builtin/email` | Identifies the user by email address | `EmailUserSelectionRenderer` |
| [](password-plugin.md) | `authentikt-builtin/password` | Verifies a password | `PasswordRenderer` |
| [](totp-plugin.md) | `authentikt-builtin/totp` | Verifies a time-based one-time password | `TotpRenderer` |
| [](oidc-plugin.md) | `authentikt-builtin/oidc` | Identifies the user through an external OpenID Connect provider | `OIDCRenderer` |
| [](done-plugin.md) | `authentikt-builtin/done` | Completes the flow and issues cookies, redirects or OAuth tokens | `DoneRenderer` |

All plugins follow the same pattern:

```kotlin
val plugin = SomePlugin<User> {
    // configuration DSL
}

installAuthentikt<User> {
    install(plugin)
    authorization { session -> /* ...return plugin when it should run... */ }
}
```

Required configuration is validated when the plugin is created. A missing callback fails at startup, not during
the first login.

If none of these fit, write your own: [](custom-step-plugins.md).
