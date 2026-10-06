package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.OIDCPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.OIDCTokens
import es.jvbabi.authentikt.core.step.plugins.builtin.UserInfo
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class OIDCTokensTest {

    @BeforeTest
    fun clearSessions() {
        sessions.clear()
    }

    private val receivedAt = Instant.fromEpochSeconds(1_700_000_000)

    private fun parse(json: String) =
        OIDCTokens.fromTokenResponse(Json.parseToJsonElement(json).jsonObject, receivedAt)

    @Test
    fun `parses a full token response`() {
        val tokens = parse(
            """
            {
              "access_token": "at",
              "token_type": "Bearer",
              "refresh_token": "rt",
              "id_token": "header.payload.signature",
              "expires_in": 300,
              "refresh_expires_in": 1800,
              "scope": "openid profile  email offline_access"
            }
            """.trimIndent()
        )

        assertEquals("at", tokens.accessToken)
        assertEquals("Bearer", tokens.tokenType)
        assertEquals("rt", tokens.refreshToken)
        assertEquals("header.payload.signature", tokens.idToken)
        assertEquals(300.seconds, tokens.expiresIn)
        assertEquals(receivedAt + 300.seconds, tokens.expiresAt)
        assertEquals(listOf("openid", "profile", "email", "offline_access"), tokens.scopes)
        assertEquals("1800", tokens.raw.getValue("refresh_expires_in").jsonPrimitive.content)
    }

    @Test
    fun `optional fields are null when missing`() {
        val tokens = parse("""{ "access_token": "at", "refresh_token": null }""")

        assertEquals("at", tokens.accessToken)
        assertNull(tokens.tokenType)
        assertNull(tokens.refreshToken)
        assertNull(tokens.idToken)
        assertNull(tokens.expiresIn)
        assertNull(tokens.expiresAt)
        assertNull(tokens.scopes)
    }

    @Test
    fun `expires_in sent as string is accepted`() {
        assertEquals(60.seconds, parse("""{ "access_token": "at", "expires_in": "60" }""").expiresIn)
    }

    @Test
    fun `missing access token is rejected`() {
        assertFailsWith<IllegalArgumentException> { parse("""{ "token_type": "Bearer" }""") }
    }

    @Test
    fun `toString does not leak tokens`() {
        val text = parse("""{ "access_token": "secret-at", "refresh_token": "secret-rt", "id_token": "secret-id" }""")
            .toString()
        assertFalse("secret" in text, text)
    }

    @Test
    fun `additional authorization parameters are added to the authorize url`() = testApplication {
        val oidcPlugin = OIDCPlugin<String> {
            clientId = "client"
            clientSecret = "secret"
            authorizationEndpoint = "https://sso.example.com/auth"
            tokenEndpoint = "https://sso.example.com/token"
            userInfoEndpoint = "https://sso.example.com/userinfo"
            jwksUri = "https://sso.example.com/jwks"
            scopes("openid", "offline_access")
            authorizationParameter("access_type", "offline")
            authorizationParameter("prompt", "consent")
            onUserInfo { _, _ ->
                // The full token response is available through the receiver
                UserInfo.Result.Failure("refresh token: ${tokens.refreshToken}")
            }
        }
        val donePlugin = DonePlugin<String> { onSuccess { _, _ -> } }

        lateinit var instance: AuthentiktInstance<String>
        application {
            install(ContentNegotiation) { json() }
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                install(oidcPlugin)
                install(donePlugin)
                authorization { session -> if (!session.has(oidcPlugin)) oidcPlugin else donePlugin }
            }
        }
        startApplication()

        val session = instance.createNewSession()
        val check = Json.parseToJsonElement(client.get("/authentikt/flow/${session.sessionId}/check").bodyAsText())
            .jsonObject
        val authorizeUrl = Url(check.getValue("payload").jsonObject.getValue("authorize_url").jsonPrimitive.content)

        assertEquals("openid offline_access", authorizeUrl.parameters["scope"])
        assertEquals("offline", authorizeUrl.parameters["access_type"])
        assertEquals("consent", authorizeUrl.parameters["prompt"])
    }

    @Test
    fun `authorization parameters set by the plugin cannot be overridden`() {
        assertFailsWith<IllegalArgumentException> {
            OIDCPlugin<String> {
                authorizationParameter("state", "fixed")
            }
        }
    }
}
