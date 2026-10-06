# About authentikt

<img src="authentikt-logo.svg" alt="authentikt logo" width="48"/>

Build authentication flows for your Ktor application effortlessly. Plugin based and customizable.

authentikt (**authenti**cation for **K**otlin/**T**ypeScript) consists of two libraries that work together:

| Library | Package | Purpose |
|---------|---------|---------|
| **authentikt-core** | `es.jvbabi.authentikt:core` (Maven Central) | Ktor server plugin that runs multi-step authentication flows |
| **authentikt-svelte** | `@julius-babies/authentikt-svelte` (npm) | Svelte 5 client library that renders those flows in the browser |

> authentikt is in an early stage of development. APIs may still change between minor versions.
> Check [](known-limitations.md) before using it in production.
{style="warning"}

> **AI disclaimer:** Large parts of the authentikt code and of this documentation were written with the help of
> AI tools, so mistakes can slip through. Please
> [open an issue](https://github.com/Julius-Babies/authentikt/issues) if something looks wrong or doesn't match the
> actual behaviour. Code quality will keep improving over time.
{style="note"}

## What it does

A login is modelled as a **flow** made of **steps**. Every step is provided by a **plugin**: one plugin identifies
the user (for example by email), others verify them (password, TOTP, an external OpenID Connect provider), and a
final plugin hands out whatever your application uses as proof of authentication (a cookie, a redirect, an OAuth
access token).

You decide which step comes next with a single callback. That callback sees the whole session, so flows like
"password, then TOTP, but only if the user has TOTP enabled" are a plain `when` expression:

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

On the frontend, each server-side plugin has a matching headless Svelte plugin. It owns the API calls and state, and
you can keep the default UI or replace it with your own snippet.

## Features

- **Pluggable steps**: email user selection, password, TOTP, OpenID Connect and a completion step ship with the
  library. You can write your own in a few dozen lines.
- **Full control over the step order** through one callback.
- **Session attributes** for passing data between steps or to the frontend.
- **OAuth 2.0 Authorization Code Grant** with PKCE (RFC 6749, RFC 7636), so your own applications can sign users in
  through your login page.
- **OAuth 2.0 Device Authorization Grant** (RFC 8628), so TVs, CLIs and other input-constrained devices can sign in
  through your login page.
- **Headless Svelte 5 client** built on runes, with optional default UI and snippet-based customization.

## Where to go next

<deflist>
<def title="New to authentikt?">

Start with [](installation.md) and [](quick-start.md).

</def>
<def title="Want to understand the model?">

Read [](how-it-works.md), [](sessions.md) and [](step-order.md).

</def>
<def title="Looking for a specific plugin?">

See [](builtin-plugins.md).

</def>
<def title="Building your own step?">

See [](custom-step-plugins.md) and [](frontend-custom-plugins.md).

</def>
</deflist>
