# authentikt-core

Ktor server plugin for pluggable, multi-step authentication flows.

## Installation

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("es.jvbabi.authentikt:core:1.0-SNAPSHOT")
}
```

## Quick start

```kotlin
fun Application.module() {
    installAuthentikt {
        apiPrefix = "/api/v1"

        // 1. User identification
        install(EmailUserSelectionPlugin {
            findUserByEmail { email -> userRepository.findByEmail(email) }
        })

        // 2. Password verification
        install(PasswordPlugin {
            checkPassword { user, password -> passwordHasher.verify(user, password) }
        })

        // 3. Optional TOTP
        install(TotpPlugin {
            getSecret { user -> user.totpSecret }
        })

        // 4. Token generation
        install(DonePlugin {
            generateToken { session, user -> jwtService.createToken(user) }
            cookie(name = "auth_token", validFor = 7.days)
        })

        // Wire the step order
        authorization { session, user ->
            when {
                !session.has(passwordPlugin) -> passwordPlugin
                user.hasTotpEnabled && !session.has(totpPlugin) -> totpPlugin
                else -> donePlugin
            }
        }
    }
}
```

## Architecture

### Plugin types

**User-selection plugins** identify the user (email, username, etc.). At least one must be installed.

```
POST /authentikt/flow/{sessionId}/user-selection/plugins/{namespace}/
```

**Step plugins** verify the user (password, TOTP, custom). They run in the order determined by the authorization callback.

```
POST /authentikt/flow/{sessionId}/steps/plugins/{namespace}/
```

### Flow lifecycle

```
Client                    Server
  │                         │
  │  POST /login            │
  │────────────────────────►│  creates Session, returns sessionId
  │                         │
  │  GET /check             │
  │────────────────────────►│  returns { type: "user_selection", plugins: [...] }
  │                         │
  │  POST user-selection    │
  │────────────────────────►│  identifies user, advances flow
  │                         │
  │  GET /check             │
  │────────────────────────►│  returns { type: "step", namespace: "..." }
  │                         │
  │  POST step              │
  │────────────────────────►│  validates, advances flow
  │                         │
  │  ... repeat for each step ... │
  │                         │
  │  GET /check             │
  │────────────────────────►│  returns { type: "finished" }
```

## Built-in plugins

| Plugin | Namespace | Purpose |
|--------|-----------|---------|
| `EmailUserSelectionPlugin` | `authentikt-builtin/email` | Look up user by email |
| `PasswordPlugin` | `authentikt-builtin/password` | Verify password |
| `TotpPlugin` | `authentikt-builtin/totp` | Verify TOTP code |
| `DonePlugin` | `authentikt-builtin/done` | Generate auth token |

## Custom plugin

```kotlin
class SmsPlugin<USER>(
    configuration: SmsPluginConfigurationBuilder<USER>.() -> Unit
) : BasePlugin<SmsState>(namespace = "acme/sms-code") {
    override suspend fun createState(session: Session<*>): SmsState = SmsState()

    override fun installRoutes(inRoute: Route) {
        with(inRoute) {
            post {
                val request = call.receive<SmsRequest>()
                // validate code...
                if (valid) session.nextStep()
                call.respond(mapOf("success" to valid))
            }
        }
    }
}
```

## Documentation

Generate API docs with Dokka:

```sh
./gradlew :core:dokkaGenerateHtml
# open core/build/dokka/html/index.html
```
