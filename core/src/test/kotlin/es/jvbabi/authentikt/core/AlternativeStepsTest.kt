package es.jvbabi.authentikt.core

import es.jvbabi.authentikt.core.session.sessions
import es.jvbabi.authentikt.core.step.plugins.builtin.DonePlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.JunctionPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.PasswordPlugin
import es.jvbabi.authentikt.core.step.plugins.builtin.TotpPlugin
import es.jvbabi.authentikt.core.step.plugins.alternative
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun alternativesTestUser(name: String) = object : AuthentiktUser<String>(name) {
    override suspend fun getEmail(): String = "$name@example.com"
    override suspend fun getUsername(): String = name
    override suspend fun getDisplayName(): String = name
}

class AlternativeStepsTest {

    @BeforeTest
    fun clearSessions() {
        sessions.clear()
    }

    private val passwordPlugin = PasswordPlugin<String> { checkPassword { _, password -> password == "secret" } }
    private val totpPlugin = TotpPlugin<String> { validate { _, code -> code == "123456" } }
    private val donePlugin = DonePlugin<String> { onSuccess { _, _ -> } }

    private suspend fun ApplicationTestBuilder.check(sessionId: String): JsonObject =
        Json.parseToJsonElement(client.get("/authentikt/flow/$sessionId/check").bodyAsText()).jsonObject

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) = client.post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun JsonObject.namespace() = getValue("namespace").jsonPrimitive.content
    private fun JsonObject.alternatives() = getValue("alternatives").jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `switching to an alternative replaces the active step`() = testApplication {
        lateinit var instance: AuthentiktInstance<String>
        application {
            install(ContentNegotiation) { json() }
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                install(passwordPlugin)
                install(totpPlugin)
                install(donePlugin)
                authorization { session ->
                    when {
                        !session.has(passwordPlugin) && !session.has(totpPlugin) -> passwordPlugin alternative listOf(totpPlugin)
                        else -> donePlugin
                    }
                }
            }
        }
        startApplication()
        val session = instance.createNewSession()
        session.identifiedUser = alternativesTestUser("alice")

        val initial = check(session.sessionId)
        assertEquals(passwordPlugin.namespace, initial.namespace())
        assertEquals(listOf(totpPlugin.namespace), initial.alternatives())

        val switch = postJson("/authentikt/flow/${session.sessionId}/alternatives", """{"namespace":"${totpPlugin.namespace}"}""")
        assertEquals(HttpStatusCode.OK, switch.status)

        val switched = check(session.sessionId)
        assertEquals(totpPlugin.namespace, switched.namespace())
        assertEquals(listOf(passwordPlugin.namespace), switched.alternatives())
        assertEquals(listOf(totpPlugin), session.authenticationSteps.map { it.first })

        // The replaced step is no longer active
        val passwordStatus = postJson("/authentikt/flow/${session.sessionId}/steps/plugins/${passwordPlugin.namespace}", """{"password":"secret"}""")
        assertEquals(HttpStatusCode.Conflict, passwordStatus.status)

        postJson("/authentikt/flow/${session.sessionId}/steps/plugins/${totpPlugin.namespace}", """{"totp_code":"123456"}""")
        val done = check(session.sessionId)
        assertEquals(donePlugin.namespace, done.namespace())
        assertEquals(emptyList(), done.alternatives())
    }

    @Test
    fun `switching to a step that is not an alternative is rejected`() = testApplication {
        lateinit var instance: AuthentiktInstance<String>
        application {
            install(ContentNegotiation) { json() }
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                install(passwordPlugin)
                install(donePlugin)
                authorization { session -> if (!session.has(passwordPlugin)) passwordPlugin else donePlugin }
            }
        }
        startApplication()
        val session = instance.createNewSession()
        session.identifiedUser = alternativesTestUser("bob")
        check(session.sessionId)

        val switch = postJson("/authentikt/flow/${session.sessionId}/alternatives", """{"namespace":"${donePlugin.namespace}"}""")
        assertEquals(HttpStatusCode.Conflict, switch.status)
        assertEquals(listOf(passwordPlugin), session.authenticationSteps.map { it.first })
    }

    @Test
    fun `junction stores the selected option`() = testApplication {
        val junctionPlugin = JunctionPlugin<String>()
        lateinit var instance: AuthentiktInstance<String>
        application {
            install(ContentNegotiation) { json() }
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                install(junctionPlugin)
                install(passwordPlugin)
                install(totpPlugin)
                install(donePlugin)
                authorization { session ->
                    when {
                        !session.has(junctionPlugin) -> junctionPlugin(listOf(passwordPlugin, totpPlugin))
                        !session.has(passwordPlugin) && !session.has(totpPlugin) -> junctionPlugin.selectedOption(session)!!
                        else -> donePlugin
                    }
                }
            }
        }
        startApplication()
        val session = instance.createNewSession()
        session.identifiedUser = alternativesTestUser("carol")

        val junction = check(session.sessionId)
        assertEquals(junctionPlugin.namespace, junction.namespace())
        assertEquals(
            listOf(passwordPlugin.namespace, totpPlugin.namespace),
            junction.getValue("payload").jsonObject.getValue("options").jsonArray.map { it.jsonPrimitive.content },
        )

        val invalid = postJson("/authentikt/flow/${session.sessionId}/steps/plugins/${junctionPlugin.namespace}", """{"namespace":"${donePlugin.namespace}"}""")
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val select = postJson("/authentikt/flow/${session.sessionId}/steps/plugins/${junctionPlugin.namespace}", """{"namespace":"${totpPlugin.namespace}"}""")
        assertEquals(HttpStatusCode.OK, select.status)
        assertEquals(totpPlugin, junctionPlugin.selectedOption(session))
        assertEquals(totpPlugin.namespace, check(session.sessionId).namespace())
    }

    @Test
    fun `switching to a prepared alternative starts it with the prepared state`() = testApplication {
        val junctionPlugin = JunctionPlugin<String>()
        lateinit var instance: AuthentiktInstance<String>
        application {
            install(ContentNegotiation) { json() }
            instance = installAuthentikt {
                baseUrl = "http://localhost"
                uiLoginBaseUrl = "http://localhost/"
                install(junctionPlugin)
                install(passwordPlugin)
                install(totpPlugin)
                install(donePlugin)
                authorization { session ->
                    when {
                        session.authenticationSteps.isEmpty() -> passwordPlugin alternative listOf(junctionPlugin(listOf(totpPlugin)))
                        else -> donePlugin
                    }
                }
            }
        }
        startApplication()
        val session = instance.createNewSession()
        session.identifiedUser = alternativesTestUser("dave")

        assertEquals(listOf(junctionPlugin.namespace), check(session.sessionId).alternatives())
        postJson("/authentikt/flow/${session.sessionId}/alternatives", """{"namespace":"${junctionPlugin.namespace}"}""")

        val junction = check(session.sessionId)
        assertEquals(junctionPlugin.namespace, junction.namespace())
        assertEquals(listOf(passwordPlugin.namespace), junction.alternatives())
        assertEquals(
            listOf(totpPlugin.namespace),
            junction.getValue("payload").jsonObject.getValue("options").jsonArray.map { it.jsonPrimitive.content },
        )
    }
}
