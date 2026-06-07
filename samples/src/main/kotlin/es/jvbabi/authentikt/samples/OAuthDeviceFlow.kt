package es.jvbabi.authentikt.samples

import es.jvbabi.authentikt.core.utils.customSsl
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun oauthDeviceFlowTest() {
    println("Starting OAuth Device Flow")
    println()

    val baseUrl = "https://authentikt-lib.werkbank.space"

    val clientId = "authentikt-tv-app"

    val client = HttpClient(CIO) {
        customSsl(listOf(File("/Users/julius/.werkbank/certificates/rootCa.crt")))
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
            )
        }
    }

    client.use { client ->
        // ── Step 1: Request device code ──────────────────────────────
        println("Requesting device code...")

        val deviceResponse = client.submitForm(
            url = "$baseUrl/oauth/device/code",
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("scope", "openid profile email")
            }
        ).body<DeviceAuthorizationResponse>()

        println("Device code received")
        println("User code: ${deviceResponse.userCode}")

        deviceResponse.verificationUriComplete?.let {
            println("Open: $it")
        } ?: run {
            println("Open: ${deviceResponse.verificationUri}")
            println("Enter code: ${deviceResponse.userCode}")
        }

        println()

        // ── Step 2: Poll token endpoint ──────────────────────────────
        var pollingInterval = deviceResponse.interval.seconds
        val deadline = Clock.System.now() + deviceResponse.expiresIn.seconds

        while (Clock.System.now() < deadline) {
            delay(pollingInterval)

            println("Polling token endpoint (interval=${pollingInterval.inWholeSeconds}s)...")

            val response = client.submitForm(
                url = "$baseUrl/oauth/token",
                formParameters = Parameters.build {
                    append(
                        "grant_type",
                        "urn:ietf:params:oauth:grant-type:device_code"
                    )
                    append("device_code", deviceResponse.deviceCode)
                    append("client_id", clientId)
                }
            )

            if (response.status == HttpStatusCode.OK) {
                val token = response.body<TokenResponse>()

                println()
                println("=== AUTHORIZATION SUCCESSFUL ===")
                println("Access Token:")
                println(token.accessToken)

                token.refreshToken?.let {
                    println()
                    println("Refresh Token:")
                    println(it)
                }

                token.idToken?.let {
                    println()
                    println("ID Token:")
                    println(it)
                }

                return
            }

            val error = runCatching {
                response.body<OAuthErrorResponse>()
            }.getOrNull()

            when (error?.error) {
                "authorization_pending" -> {
                    println("Waiting for user authorization...")
                }

                "slow_down" -> {
                    // RFC 8628 §3.5: increase the polling interval by 5 seconds
                    val increase = 5.seconds
                    pollingInterval += increase
                    println("slow_down received — increasing interval by $increase to ${pollingInterval.inWholeSeconds}s")
                }

                "access_denied" -> {
                    println("Error: User denied the authorization request.")
                    if (error.error_description != null) println("  ${error.error_description}")
                    return
                }

                "expired_token" -> {
                    println("Error: The device code has expired.")
                    if (error.error_description != null) println("  ${error.error_description}")
                    return
                }

                else -> {
                    println("Unexpected response:")
                    println(response.bodyAsText())
                    return
                }
            }
        }

        println("Error: Device code expired before authorization was completed.")
    }
}

@Serializable
data class DeviceAuthorizationResponse(
    @SerialName("device_code")
    val deviceCode: String,

    @SerialName("user_code")
    val userCode: String,

    @SerialName("verification_uri")
    val verificationUri: String,

    @SerialName("verification_uri_complete")
    val verificationUriComplete: String? = null,

    @SerialName("expires_in")
    val expiresIn: Long,

    @SerialName("interval")
    val interval: Long = 5
)

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,

    @SerialName("refresh_token")
    val refreshToken: String? = null,

    @SerialName("token_type")
    val tokenType: String,

    @SerialName("expires_in")
    val expiresIn: Long,

    @SerialName("scope")
    val scope: String? = null,

    @SerialName("id_token")
    val idToken: String? = null
)

@Serializable
data class OAuthErrorResponse(
    val error: String,
    val error_description: String? = null
)
