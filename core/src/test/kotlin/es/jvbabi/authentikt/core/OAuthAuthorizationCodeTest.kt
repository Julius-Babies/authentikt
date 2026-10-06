package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.config.AuthentiktPluginConfigurationBuilder
import es.jvbabi.authentikt.core.config.OAuthAccessToken
import es.jvbabi.authentikt.core.config.OAuthAuthorizationResult
import es.jvbabi.authentikt.core.config.OAuthConfigurationBuilder
import es.jvbabi.authentikt.core.oauth.authorizationCodes
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionDestination
import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePluginScope
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.formUrlEncode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class OAuthTestClock : Clock {
    var now: Instant = Instant.fromEpochSeconds(1_700_000_000)
    override fun now(): Instant = now
    fun advance(duration: Duration) {
        now += duration
    }
}

class OAuthAuthorizationCodeTest {

    private val clock = OAuthTestClock()

    private val codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    private val codeChallenge = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray()))

    private var onSuccessCalls = 0
    private var onSuccessBlock: DonePluginScope.() -> Unit = {}

    private val donePlugin = DonePlugin<String> {
        onSuccess { _, _ ->
            onSuccessCalls++
            onSuccessBlock()
        }
        onOAuthSuccess { session, user ->
            val client = session.destination!!.applicationId
            OAuthAccessToken("token-for-$user@$client", null, 1.days)
        }
    }

    @BeforeTest
    fun clear() {
        sessions.clear()
        authorizationCodes.clear()
        onSuccessCalls = 0
        onSuccessBlock = {}
    }

    private fun ApplicationTestBuilder.setup(
        configureOAuth: OAuthConfigurationBuilder<String>.() -> Unit = {},
        configure: AuthentiktPluginConfigurationBuilder<String>.() -> Unit = {},
    ): HttpClient {
        application {
            installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/login"
                clock = this@OAuthAuthorizationCodeTest.clock
                install(donePlugin)
                authorization { donePlugin }
                oauth {
                    onAuthorize { clientId, redirectUri ->
                        when {
                            clientId != "web" -> OAuthAuthorizationResult.Error("Unknown client")
                            redirectUri != "https://app.example/cb" -> OAuthAuthorizationResult.Error("Unknown redirect URI")
                            else -> OAuthAuthorizationResult.Application(
                                clientId, redirectUri, "Web App", scopes = scopes.filter { it != "admin" },
                            )
                        }
                    }
                    configureOAuth()
                }
                configure()
            }
        }
        return createClient { followRedirects = false }
    }

    private suspend fun HttpClient.authorize(
        block: ParametersBuilder.() -> Unit = {
            append("response_type", "code")
            append("client_id", "web")
            append("redirect_uri", "https://app.example/cb")
            append("state", "xyz")
            append("scope", "read write admin")
            append("code_challenge", codeChallenge)
            append("code_challenge_method", "S256")
        },
    ): HttpResponse {
        val query = ParametersBuilder().apply(block).build().entries()
            .joinToString("&") { (key, values) -> "$key=${values.single().encodeURLParameter()}" }
        return get("/oauth/authorize?$query")
    }

    /**
     * Runs the login of the session created by [authorize] and returns the URL the browser is redirected to.
     */
    private suspend fun HttpClient.completeLogin(authorizeResponse: HttpResponse): Url {
        assertEquals(HttpStatusCode.Found, authorizeResponse.status)
        val sessionId = Url(authorizeResponse.headers[HttpHeaders.Location]!!).parameters["_authentikt_session_id"]!!
        @Suppress("UNCHECKED_CAST")
        (sessions[sessionId] as Session<String>).identifiedUser = testUser("alice")

        assertEquals(HttpStatusCode.OK, get("/authentikt/flow/$sessionId/check").status)
        val done = get("/authentikt/flow/$sessionId/steps/plugins/${donePlugin.namespace}").bodyAsText()
        assertContains(done, "\"type\":\"redirect\"")
        val to = Regex("\"to\":\"([^\"]+)\"").find(done)!!.groupValues[1].replace("\\u0026", "&").replace("\\u003d", "=")
        return Url(to)
    }

    private suspend fun HttpClient.token(block: ParametersBuilder.() -> Unit): HttpResponse =
        submitForm("/oauth/token", ParametersBuilder().apply(block).build())

    private fun tokenParameters(code: String): ParametersBuilder.() -> Unit = {
        append("grant_type", "authorization_code")
        append("code", code)
        append("redirect_uri", "https://app.example/cb")
        append("client_id", "web")
        append("code_verifier", codeVerifier)
    }

    private fun testUser(name: String) = object : AuthentiktUser<String>(name) {
        override suspend fun getEmail(): String = "$name@example.com"
        override suspend fun getUsername(): String = name
        override suspend fun getDisplayName(): String = name
    }

    @Test
    fun `authorization code flow with PKCE issues a token`() = testApplication {
        val client = setup()

        val redirect = client.completeLogin(client.authorize())
        assertEquals("app.example", redirect.host)
        assertEquals("/cb", redirect.encodedPath)
        assertEquals("xyz", redirect.parameters["state"])
        val code = assertNotNull(redirect.parameters["code"])
        assertEquals(1, onSuccessCalls)
        assertTrue(sessions.isEmpty())

        val response = client.token(tokenParameters(code))
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        assertContains(body, "token-for-alice@web")
        assertContains(body, "\"scope\":\"read write\"")
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `granted scopes are exposed on the session destination`() = testApplication {
        val client = setup()
        client.authorize()

        val destination = sessions.values.single().destination as SessionDestination.OAuth
        assertEquals(listOf("read", "write"), destination.scopes)
    }

    @Test
    fun `authorization code can only be used once`() = testApplication {
        val client = setup()
        val code = client.completeLogin(client.authorize()).parameters["code"]!!

        assertEquals(HttpStatusCode.OK, client.token(tokenParameters(code)).status)

        val second = client.token(tokenParameters(code))
        assertEquals(HttpStatusCode.BadRequest, second.status)
        assertContains(second.bodyAsText(), "invalid_grant")
    }

    @Test
    fun `authorization code expires`() = testApplication {
        val client = setup(configureOAuth = { authorizationCodeLifetime = 30.seconds })
        val code = client.completeLogin(client.authorize()).parameters["code"]!!

        clock.advance(31.seconds)

        assertContains(client.token(tokenParameters(code)).bodyAsText(), "invalid_grant")
    }

    @Test
    fun `wrong code verifier is rejected`() = testApplication {
        val client = setup()
        val code = client.completeLogin(client.authorize()).parameters["code"]!!

        val response = client.token {
            tokenParameters(code)()
            set("code_verifier", "x".repeat(43))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "invalid_grant")
    }

    @Test
    fun `mismatching redirect uri and client id are rejected`() = testApplication {
        val client = setup()
        val first = client.completeLogin(client.authorize()).parameters["code"]!!
        val second = client.completeLogin(client.authorize()).parameters["code"]!!

        assertContains(
            client.token { tokenParameters(first)(); set("redirect_uri", "https://evil.example/cb") }.bodyAsText(),
            "invalid_grant",
        )
        assertContains(
            client.token { tokenParameters(second)(); set("client_id", "other") }.bodyAsText(),
            "invalid_grant",
        )
    }

    @Test
    fun `confidential client authenticates with http basic`() = testApplication {
        val client = setup(configureOAuth = {
            authenticateClient { clientId, secret -> clientId == "web" && secret == "s3cret" }
        })
        val redirect = client.completeLogin(client.authorize {
            append("response_type", "code")
            append("client_id", "web")
            append("redirect_uri", "https://app.example/cb")
        })
        val code = redirect.parameters["code"]!!
        assertEquals(null, redirect.parameters["state"])

        val withoutSecret = client.token {
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", "https://app.example/cb")
            append("client_id", "web")
        }
        assertEquals(HttpStatusCode.Unauthorized, withoutSecret.status)
        assertContains(withoutSecret.bodyAsText(), "invalid_client")

        val code2 = client.completeLogin(client.authorize {
            append("response_type", "code")
            append("client_id", "web")
            append("redirect_uri", "https://app.example/cb")
        }).parameters["code"]!!
        val response = client.submitForm(
            "/oauth/token",
            parameters {
                append("grant_type", "authorization_code")
                append("code", code2)
                append("redirect_uri", "https://app.example/cb")
            },
        ) {
            header(HttpHeaders.Authorization, "Basic " + Base64.getEncoder().encodeToString("web:s3cret".toByteArray()))
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertContains(response.bodyAsText(), "token-for-alice@web")
    }

    @Test
    fun `wrong client secret is rejected`() = testApplication {
        val client = setup(configureOAuth = {
            authenticateClient { _, secret -> secret == "s3cret" }
        })
        val code = client.completeLogin(client.authorize()).parameters["code"]!!

        val response = client.token { tokenParameters(code)(); append("client_secret", "wrong") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertContains(response.bodyAsText(), "invalid_client")
    }

    @Test
    fun `pkce is required for public clients`() = testApplication {
        val client = setup()

        val response = client.authorize {
            append("response_type", "code")
            append("client_id", "web")
            append("redirect_uri", "https://app.example/cb")
            append("state", "abc")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        val location = Url(response.headers[HttpHeaders.Location]!!)
        assertEquals("app.example", location.host)
        assertEquals("invalid_request", location.parameters["error"])
        assertEquals("abc", location.parameters["state"])
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `plain pkce and unsupported response types are redirected as errors`() = testApplication {
        val client = setup()

        val plain = client.authorize {
            append("response_type", "code")
            append("client_id", "web")
            append("redirect_uri", "https://app.example/cb")
            append("code_challenge", codeChallenge)
            append("code_challenge_method", "plain")
        }
        assertEquals("invalid_request", Url(plain.headers[HttpHeaders.Location]!!).parameters["error"])

        val token = client.authorize {
            append("response_type", "token")
            append("client_id", "web")
            append("redirect_uri", "https://app.example/cb")
        }
        assertEquals("unsupported_response_type", Url(token.headers[HttpHeaders.Location]!!).parameters["error"])
    }

    @Test
    fun `invalid client or redirect uri is not redirected`() = testApplication {
        val client = setup()

        val missing = client.get("/oauth/authorize")
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertContains(missing.bodyAsText(), "invalid_request")

        val unknownRedirect = client.authorize {
            append("response_type", "code")
            append("client_id", "web")
            append("redirect_uri", "https://evil.example/cb")
        }
        assertEquals(HttpStatusCode.BadRequest, unknownRedirect.status)
        assertFalse(unknownRedirect.headers.contains(HttpHeaders.Location))
        assertContains(unknownRedirect.bodyAsText(), "Unknown redirect URI")
    }

    @Test
    fun `token endpoint answers with rfc 6749 errors`() = testApplication {
        val client = setup()

        val missing = client.token { }
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertContains(missing.bodyAsText(), "\"error\":\"invalid_request\"")

        val unsupported = client.token { append("grant_type", "password") }
        assertContains(unsupported.bodyAsText(), "\"error\":\"unsupported_grant_type\"")

        // Device flow is not configured in this test
        val deviceCode = client.token { append("grant_type", "urn:ietf:params:oauth:grant-type:device_code") }
        assertContains(deviceCode.bodyAsText(), "\"error\":\"unsupported_grant_type\"")
    }

    @Test
    fun `regular sessions still run onSuccess`() = testApplication {
        lateinit var instance: AuthentiktInstance<String>
        application {
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/login"
                clock = this@OAuthAuthorizationCodeTest.clock
                sessionTimeout = 30.minutes
                install(donePlugin)
                authorization { donePlugin }
            }
        }
        startApplication()
        val session = instance.createNewSession()
        session.identifiedUser = testUser("bob")

        client.get("/authentikt/flow/${session.sessionId}/check")
        val done = client.get("/authentikt/flow/${session.sessionId}/steps/plugins/${donePlugin.namespace}")
        assertContains(done.bodyAsText(), "\"type\":\"success\"")
        assertEquals(1, onSuccessCalls)
        assertTrue(authorizationCodes.isEmpty())
    }

    @Test
    fun `onSuccess cookies are set for OAuth sessions and its redirect is ignored`() = testApplication {
        onSuccessBlock = {
            cookie(Cookie("SessionToken", "sso"))
            redirect("https://elsewhere.example")
        }
        val client = setup()
        val sessionId = Url(client.authorize().headers[HttpHeaders.Location]!!).parameters["_authentikt_session_id"]!!
        @Suppress("UNCHECKED_CAST")
        (sessions[sessionId] as Session<String>).identifiedUser = testUser("alice")
        client.get("/authentikt/flow/$sessionId/check")

        val done = client.get("/authentikt/flow/$sessionId/steps/plugins/${donePlugin.namespace}")
        assertContains(done.headers.getAll(HttpHeaders.SetCookie).orEmpty().joinToString(), "SessionToken=sso")
        val body = done.bodyAsText()
        assertContains(body, "\"to\":\"https://app.example/cb?")
        assertContains(body, "\"cookies\":[\"SessionToken\"]")
        assertEquals(1, onSuccessCalls)
    }

    private fun ParametersBuilder.defaultAuthorizeParameters(prompt: String? = null) {
        append("response_type", "code")
        append("client_id", "web")
        append("redirect_uri", "https://app.example/cb")
        append("state", "xyz")
        append("scope", "read write admin")
        append("code_challenge", codeChallenge)
        append("code_challenge_method", "S256")
        if (prompt != null) append("prompt", prompt)
    }

    private fun OAuthConfigurationBuilder<String>.loggedInFromCookie() = loggedInUser { call, application ->
        assertEquals("web", application.clientId)
        call.request.cookies["SessionToken"]?.let { testUser(it) }
    }

    @Test
    fun `logged-in users get a code without the login UI`() = testApplication {
        val client = setup(configureOAuth = { loggedInFromCookie() })

        val response = client.authorize { defaultAuthorizeParameters() }
        // Without the cookie the login UI is shown
        assertTrue(response.headers[HttpHeaders.Location]!!.startsWith("http://localhost/login"))

        val sso = client.get("/oauth/authorize?" + ParametersBuilder().apply { defaultAuthorizeParameters() }.build().formUrlEncode()) {
            header(HttpHeaders.Cookie, "SessionToken=alice")
        }
        assertEquals(HttpStatusCode.Found, sso.status)
        val redirect = Url(sso.headers[HttpHeaders.Location]!!)
        assertEquals("app.example", redirect.host)
        assertEquals("xyz", redirect.parameters["state"])
        val code = assertNotNull(redirect.parameters["code"])
        assertEquals(0, onSuccessCalls)

        val token = client.token(tokenParameters(code))
        assertEquals(HttpStatusCode.OK, token.status)
        assertContains(token.bodyAsText(), "token-for-alice@web")
        // Only the session of the first request (login UI) is left
        assertEquals(1, sessions.size)
    }

    @Test
    fun `prompt=login always shows the login UI`() = testApplication {
        var calls = 0
        val client = setup(configureOAuth = {
            loggedInUser { _, _ -> calls++; testUser("alice") }
        })

        val response = client.authorize { defaultAuthorizeParameters(prompt = "login") }
        assertTrue(response.headers[HttpHeaders.Location]!!.startsWith("http://localhost/login"))
        assertEquals(0, calls)
    }

    @Test
    fun `prompt=none never shows the login UI`() = testApplication {
        val client = setup(configureOAuth = { loggedInUser { _, _ -> null } })

        val response = client.authorize { defaultAuthorizeParameters(prompt = "none") }
        val redirect = Url(response.headers[HttpHeaders.Location]!!)
        assertEquals("app.example", redirect.host)
        assertEquals("login_required", redirect.parameters["error"])
        assertEquals("xyz", redirect.parameters["state"])
        assertTrue(sessions.isEmpty())

        val combined = client.authorize { defaultAuthorizeParameters(prompt = "none login") }
        assertEquals("invalid_request", Url(combined.headers[HttpHeaders.Location]!!).parameters["error"])
    }

    @Test
    fun `prompt=none without loggedInUser requires a login`() = testApplication {
        val client = setup()
        val response = client.authorize { defaultAuthorizeParameters(prompt = "none") }
        assertEquals("login_required", Url(response.headers[HttpHeaders.Location]!!).parameters["error"])
    }

    @Test
    fun `prompt=none with a logged-in user issues a code`() = testApplication {
        val client = setup(configureOAuth = { loggedInUser { _, _ -> testUser("alice") } })
        val redirect = Url(client.authorize { defaultAuthorizeParameters(prompt = "none") }.headers[HttpHeaders.Location]!!)
        assertNotNull(redirect.parameters["code"])
    }
}
