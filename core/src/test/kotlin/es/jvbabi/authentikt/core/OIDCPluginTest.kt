package es.jvbabi.authentikt.core

import com.sun.net.httpserver.HttpServer
import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.OIDCPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.UserInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseQueryString
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun oidcTestUser(name: String) = object : AuthentiktUser<String>(name) {
    override suspend fun getEmail(): String = "$name@example.com"
    override suspend fun getUsername(): String = name
    override suspend fun getDisplayName(): String = name
}

/**
 * A minimal OIDC provider with a token and a user info endpoint.
 */
private class MockOIDCProvider {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    /** Form parameters of the token requests. */
    val tokenRequests = mutableListOf<Map<String, String>>()

    /** The nonce put into the ID token. `null` returns the nonce of the authorization request. */
    var idTokenNonce: String? = null
    var expectedNonce: String? = null
    var issueIdToken = true

    init {
        server.createContext("/token") { exchange ->
            val form = parseQueryString(exchange.requestBody.readBytes().decodeToString())
            tokenRequests += form.entries().associate { it.key to it.value.first() }
            val response = buildString {
                append("""{"access_token":"access-token","token_type":"Bearer"""")
                if (issueIdToken) append(""","id_token":"${idToken(idTokenNonce ?: expectedNonce.orEmpty())}"""")
                append("}")
            }
            exchange.respond(200, response)
        }
        server.createContext("/userinfo") { exchange ->
            val authorized = exchange.requestHeaders.getFirst("Authorization") == "Bearer access-token"
            if (authorized) exchange.respond(200, """{"email":"alice@example.com"}""")
            else exchange.respond(401, "{}")
        }
        server.start()
    }

    private fun idToken(nonce: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val exp = System.currentTimeMillis() / 1000 + 300
        val payload = encoder.encodeToString(
            """{"iss":"$baseUrl","aud":"client","sub":"alice","nonce":"$nonce","exp":$exp}""".toByteArray()
        )
        return "$header.$payload.signature"
    }

    private fun com.sun.net.httpserver.HttpExchange.respond(status: Int, body: String) {
        responseHeaders.add("Content-Type", "application/json")
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    fun stop() = server.stop(0)
}

class OIDCPluginTest {

    private lateinit var provider: MockOIDCProvider

    @BeforeTest
    fun setUp() {
        sessions.clear()
        provider = MockOIDCProvider()
    }

    @AfterTest
    fun tearDown() {
        provider.stop()
    }

    private val callbackPath = "/authentikt/static/plugins/authentikt-builtin/oidc/default/callback"

    private fun oidcPlugin() = OIDCPlugin<String> {
        clientId = "client"
        clientSecret = "secret"
        authorizationEndpoint = "${provider.baseUrl}/authorize"
        tokenEndpoint = "${provider.baseUrl}/token"
        userInfoEndpoint = "${provider.baseUrl}/userinfo"
        issuer = provider.baseUrl
        scopes("openid", "email")
        onUserInfo { response, _ ->
            val email = response.body<JsonObject>()["email"]?.jsonPrimitive?.content
            if (email == "alice@example.com") UserInfo.Result.Success(oidcTestUser("alice"))
            else UserInfo.Result.Failure("unknown user")
        }
    }

    private val donePlugin = DonePlugin<String> { onSuccess { _, _ -> } }

    private data class Setup(val instance: AuthentiktInstance<String>, val plugin: OIDCPlugin<String>, val client: HttpClient)

    private suspend fun ApplicationTestBuilder.setup(): Setup {
        val plugin = oidcPlugin()
        lateinit var instance: AuthentiktInstance<String>
        application {
            install(ContentNegotiation) { json() }
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/login"
                install(plugin)
                install(donePlugin)
                authorization { session -> if (!session.has(plugin)) plugin else donePlugin }
            }
        }
        startApplication()
        return Setup(instance, plugin, createClient { followRedirects = false })
    }

    private suspend fun HttpClient.authorizeUrl(sessionId: String): Url {
        val check = Json.parseToJsonElement(get("/authentikt/flow/$sessionId/check").bodyAsText()).jsonObject
        return Url(check.getValue("payload").jsonObject.getValue("authorize_url").jsonPrimitive.content)
    }

    private suspend fun HttpClient.callback(vararg params: Pair<String, String>): HttpResponse =
        get(callbackPath + "?" + params.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" })

    private fun s256(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    @Test
    fun `successful callback uses a random state, PKCE and a nonce`() = testApplication {
        val (instance, plugin, client) = setup()
        val session = instance.createNewSession()

        val url = client.authorizeUrl(session.sessionId)
        val state = assertNotNull(url.parameters["state"])
        assertTrue(session.sessionId !in state, "state must not contain the session ID")
        assertEquals("S256", url.parameters["code_challenge_method"])
        val challenge = assertNotNull(url.parameters["code_challenge"])
        provider.expectedNonce = assertNotNull(url.parameters["nonce"])

        val response = client.callback("code" to "the-code", "state" to state)
        assertEquals(HttpStatusCode.Found, response.status)
        assertTrue(response.headers[HttpHeaders.Location]!!.contains("_authentikt_session_id=${session.sessionId}"))

        val tokenRequest = provider.tokenRequests.single()
        assertEquals("the-code", tokenRequest["code"])
        assertEquals(challenge, s256(tokenRequest.getValue("code_verifier")))
        assertTrue(session.has(plugin))
        assertEquals("alice", session.identifiedUser?.user)
    }

    @Test
    fun `state can only be used once`() = testApplication {
        val (instance, _, client) = setup()
        val session = instance.createNewSession()
        val url = client.authorizeUrl(session.sessionId)
        val state = url.parameters["state"]!!
        provider.expectedNonce = url.parameters["nonce"]

        assertEquals(HttpStatusCode.Found, client.callback("code" to "c", "state" to state).status)
        assertEquals(HttpStatusCode.BadRequest, client.callback("code" to "c", "state" to state).status)
        assertEquals(1, provider.tokenRequests.size)
    }

    @Test
    fun `failed callback rotates the state so the user can retry`() = testApplication {
        val (instance, plugin, client) = setup()
        val session = instance.createNewSession()
        val url = client.authorizeUrl(session.sessionId)
        val state = url.parameters["state"]!!
        provider.idTokenNonce = "wrong-nonce"

        val response = client.callback("code" to "c", "state" to state)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(!session.has(plugin))

        val retryUrl = client.authorizeUrl(session.sessionId)
        assertNotEquals(state, retryUrl.parameters["state"])
        assertNotEquals(url.parameters["code_challenge"], retryUrl.parameters["code_challenge"])
        assertEquals(HttpStatusCode.BadRequest, client.callback("code" to "c", "state" to state).status)

        provider.idTokenNonce = null
        provider.expectedNonce = retryUrl.parameters["nonce"]
        assertEquals(HttpStatusCode.Found, client.callback("code" to "c", "state" to retryUrl.parameters["state"]!!).status)
        assertTrue(session.has(plugin))
    }

    @Test
    fun `missing ID token is rejected when the openid scope is requested`() = testApplication {
        val (instance, plugin, client) = setup()
        val session = instance.createNewSession()
        val url = client.authorizeUrl(session.sessionId)
        provider.issueIdToken = false

        val response = client.callback("code" to "c", "state" to url.parameters["state"]!!)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(!session.has(plugin))
    }

    @Test
    fun `invalid callbacks are answered with 400`() = testApplication {
        val (instance, plugin, client) = setup()
        val session = instance.createNewSession()
        val state = client.authorizeUrl(session.sessionId).parameters["state"]!!

        assertEquals(HttpStatusCode.BadRequest, client.get(callbackPath).status)
        assertEquals(HttpStatusCode.BadRequest, client.callback("code" to "c").status)
        assertEquals(HttpStatusCode.BadRequest, client.callback("code" to "c", "state" to "forged").status)
        assertEquals(
            HttpStatusCode.BadRequest,
            client.callback("code" to "c", "state" to """{"authentikt_oidc_internal_session_id":"${session.sessionId}"}""").status,
        )
        assertEquals(HttpStatusCode.BadRequest, client.callback("state" to state).status)

        val error = client.callback("error" to "access_denied", "state" to state)
        assertEquals(HttpStatusCode.BadRequest, error.status)
        assertTrue(error.bodyAsText().contains("access_denied"))

        assertTrue(provider.tokenRequests.isEmpty())
        assertTrue(!session.has(plugin))
        // An error response does not consume the state
        assertEquals(state, client.authorizeUrl(session.sessionId).parameters["state"])
    }
}
