# Module authentikt-core

## Getting Started

This guide walks through setting up a full authentikt authentication flow with a Ktor backend
and Svelte frontend.

## Backend setup (Ktor)

### 1. Add the dependency

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("es.jvbabi.authentikt:core:1.0-SNAPSHOT")
}
```

### 2. Create a user wrapper

```kotlin
class MyUser(private val dbUser: DbUser) : AuthentiktUser<DbUser>(dbUser) {
    override suspend fun getEmail(): String? = dbUser.email
    override suspend fun getUsername(): String? = dbUser.username
    override suspend fun getDisplayName(): String? = dbUser.displayName
}
```

### 3. Install the auth plugin

```kotlin
fun Application.module() {
    installAuthentikt {
        apiPrefix = "/api/v1"

        val emailPlugin = EmailUserSelectionPlugin {
            findUserByEmail { email ->
                val user = userDao.findByEmail(email)
                user?.let { MyUser(it) }
            }
        }
        install(emailPlugin)

        val passwordPlugin = PasswordPlugin {
            checkPassword { user, password ->
                passwordHasher.verify(user.passwordHash, password)
            }
        }
        install(passwordPlugin)

        val totpPlugin = TotpPlugin {
            getSecret { user -> user.totpSecret }
        }
        install(totpPlugin)

        val donePlugin = DonePlugin {
            generateToken { session, user ->
                jwtService.createToken(user)
            }
            cookie(name = "auth_token", validFor = 7.days)
        }
        install(donePlugin)

        authorization { session, user ->
            when {
                !session.has(passwordPlugin) -> passwordPlugin
                user.user.hasTotpEnabled && !session.has(totpPlugin) -> totpPlugin
                else -> donePlugin
            }
        }
    }
}
```

### 4. Add login / logout endpoints

```kotlin
routing {
    post("/login") {
        val session = authentiktPluginConfiguration.createNewSession()
        call.respond(mapOf("session_id" to session.sessionId))
    }

    post("/logout") {
        call.response.cookies.append("auth_token", "", maxAge = 0)
        call.respond(mapOf("status" to "ok"))
    }
}
```

### 5. Protect routes

```kotlin
routing {
    authenticate("auth-token") {
        get("/api/user/me") {
            val user = call.principal<UserPrincipal>()
            call.respond(mapOf("id" to user.id, "displayName" to user.name))
        }
    }
}
```

## Frontend setup (Svelte)

### 1. Install the package

```sh
npm install authentikt-svelte
```

### 2. Add the provider and auto-renderers

```svelte
<script>
  import {
    Authentikt,
    AuthentiktUserSelectionRenderer,
    AuthentiktStepRenderer,
    PasswordRenderer,
  } from "authentikt-svelte";
  import { useAuthentiktContext } from "authentikt-svelte/context";
</script>

<Authentikt baseUrl="http://localhost:8080/api/v1/authentikt/">
  {@const auth = useAuthentiktContext()}

  {#if !auth.currentFlow}
    <button onclick={auth.startLoginFlow}>Login</button>
  {:else}
    <AuthentiktUserSelectionRenderer />

    <PasswordRenderer>
      {#snippet children(plugin)}
        <input bind:value={plugin.password} type="password" />
        <button onclick={plugin.submit}>
          {plugin.status === "loading" ? "..." : "Continue"}
        </button>
      {/snippet}
    </PasswordRenderer>

    <AuthentiktStepRenderer />
  {/if}
</Authentikt>
```

### 3. Show/hide content based on auth state

```svelte
<script>
  import { currentUser } from "authentikt-svelte/user";
</script>

{#if $currentUser === null}
  <p>Loading...</p>
{:else if $currentUser === "anonymous"}
  <p>Not logged in</p>
{:else}
  <p>Welcome, {$currentUser.displayName}</p>
{/if}
```

## Flow lifecycle

```
Frontend                         Backend
   │                               │
   │  POST /login                   │
   │──────────────────────────────►│  Creates session, returns sessionId
   │                               │
   │  GET /check                    │
   │──────────────────────────────►│  Returns { type: "user_selection", plugins: [...] }
   │                               │
   │  POST user-selection/email     │
   │──────────────────────────────►│  Identifies user, advances flow
   │                               │
   │  GET /check                    │
   │──────────────────────────────►│  Returns { type: "step", namespace: "password" }
   │                               │
   │  POST steps/plugins/password   │
   │──────────────────────────────►│  Validates password, advances flow
   │                               │
   │  ... repeat per step ...       │
   │                               │
   │  GET steps/plugins/done        │
   │──────────────────────────────►│  Generates token, sets cookie
   │                               │
   │  Flow completes, page reloads  │
```

## Custom step plugin

```kotlin
class SmsPlugin<USER>(
    configuration: SmsPluginConfigurationBuilder<USER>.() -> Unit
) : BasePlugin<SmsState>(namespace = "acme/sms-code") {

    override suspend fun createState(session: Session<*>): SmsState = SmsState()

    override fun installRoutes(inRoute: Route) {
        with(inRoute) {
            post {
                val request = call.receive<SmsCodeRequest>()
                val session = call.attributes[SessionKey]
                val valid = verifyCode(session, request.code)
                if (valid) {
                    session.authenticationSteps[session.lastIndex] =
                        this@SmsPlugin to SmsState(validated = true)
                    session.nextStep()
                }
                call.respond(mapOf("success" to valid))
            }
        }
    }
}
```

Then register it in the frontend:

```ts
import { MySmsRenderer } from "./MySmsRenderer.svelte";

const auth = useAuthentiktContext();
auth.registerStepPlugin("acme/sms-code", MySmsRenderer, (auth, ns) => new MySmsPlugin(auth, ns));
```
