package es.jvbabi.authentikt.core.step.plugins.builtin

import com.auth0.jwk.Jwk
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.time.ZoneId
import java.util.Base64
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Verifies ID tokens (OpenID Connect Core 1.0, section 3.1.3.7): the signature against the provider's JSON Web Key
 * Set, the audience, the authorized party, the expiry, the nonce and, if configured, the issuer.
 *
 * Asymmetric tokens (`RS*`, `ES*`) are verified with the keys from [jwksUri], fetched with [httpClient]. The keys are
 * cached and fetched again if a token is signed with an unknown key, at most once per minute. Symmetric tokens
 * (`HS*`) are verified with the client secret.
 */
internal class OIDCIdTokenVerifier(
    private val httpClient: HttpClient,
    private val jwksUri: Url,
    private val clientId: String,
    private val clientSecret: String,
    private val issuer: String?,
) {
    private val keysLock = Mutex()
    private var keys: List<Jwk> = emptyList()
    private var lastFetch: Instant? = null

    sealed interface Result {
        /** The ID token is valid. [claims] is its decoded payload. */
        data class Valid(val claims: JsonObject) : Result

        /** The ID token is invalid. [error] describes the problem. */
        data class Invalid(val error: String) : Result
    }

    suspend fun verify(idToken: String, expectedNonce: String, clock: Clock): Result {
        val decoded = try {
            JWT.decode(idToken)
        } catch (_: JWTVerificationException) {
            return Result.Invalid("Malformed ID token")
        }

        val algorithms = try {
            algorithmsFor(decoded, clock)
        } catch (e: Exception) {
            return Result.Invalid("Failed to load the signing keys: ${e.message}")
        }
        if (algorithms.isEmpty()) {
            return Result.Invalid("No signing key found for algorithm '${decoded.algorithm}' and key ID '${decoded.keyId}'")
        }

        var error = "Invalid signature"
        for (algorithm in algorithms) {
            val verification = JWT.require(algorithm)
                .withAudience(clientId)
                .withClaimPresence("exp")
                .acceptLeeway(LEEWAY_SECONDS)
            if (issuer != null) verification.withIssuer(issuer)
            try {
                (verification as JWTVerifier.BaseVerification).build(clock.toJavaClock()).verify(decoded)
            } catch (e: JWTVerificationException) {
                error = e.message ?: error
                continue
            }

            val nonce = decoded.getClaim("nonce").asString()
            if (nonce == null || !constantTimeEquals(nonce, expectedNonce)) return Result.Invalid("Nonce does not match")
            val azp = decoded.getClaim("azp").asString()
            if (azp != null && azp != clientId) return Result.Invalid("Authorized party is not the client ID")

            val claims = try {
                Json.parseToJsonElement(Base64.getUrlDecoder().decode(decoded.payload).decodeToString()) as JsonObject
            } catch (_: Exception) {
                return Result.Invalid("Malformed ID token payload")
            }
            return Result.Valid(claims)
        }
        return Result.Invalid(error)
    }

    /**
     * The candidate algorithms (with keys) to verify [token] with. Empty if the algorithm is not supported or no
     * matching key exists.
     */
    private suspend fun algorithmsFor(token: DecodedJWT, clock: Clock): List<Algorithm> {
        when (token.algorithm) {
            "HS256" -> return listOf(Algorithm.HMAC256(clientSecret))
            "HS384" -> return listOf(Algorithm.HMAC384(clientSecret))
            "HS512" -> return listOf(Algorithm.HMAC512(clientSecret))
        }
        val keyType = when (token.algorithm) {
            "RS256", "RS384", "RS512" -> "RSA"
            "ES256", "ES384", "ES512" -> "EC"
            else -> return emptyList()
        }

        var candidates = matchingKeys(keyType, token.keyId)
        if (candidates.isEmpty() && refreshKeys(clock.now())) candidates = matchingKeys(keyType, token.keyId)

        return candidates.mapNotNull { jwk ->
            val publicKey = runCatching { jwk.publicKey }.getOrNull()
            when (token.algorithm) {
                "RS256" -> (publicKey as? RSAPublicKey)?.let { Algorithm.RSA256(it, null) }
                "RS384" -> (publicKey as? RSAPublicKey)?.let { Algorithm.RSA384(it, null) }
                "RS512" -> (publicKey as? RSAPublicKey)?.let { Algorithm.RSA512(it, null) }
                "ES256" -> (publicKey as? ECPublicKey)?.let { Algorithm.ECDSA256(it, null) }
                "ES384" -> (publicKey as? ECPublicKey)?.let { Algorithm.ECDSA384(it, null) }
                "ES512" -> (publicKey as? ECPublicKey)?.let { Algorithm.ECDSA512(it, null) }
                else -> null
            }
        }
    }

    private suspend fun matchingKeys(keyType: String, keyId: String?): List<Jwk> = keysLock.withLock {
        keys.filter { jwk ->
            jwk.type == keyType && (jwk.usage == null || jwk.usage == "sig") && (keyId == null || jwk.id == keyId)
        }
    }

    /**
     * Fetches the key set again, unless it was fetched less than a minute ago.
     *
     * @return whether the keys were fetched.
     */
    private suspend fun refreshKeys(now: Instant): Boolean = keysLock.withLock {
        val lastFetch = lastFetch
        if (lastFetch != null && now - lastFetch < 1.minutes) return@withLock false
        this.lastFetch = now

        val response = httpClient.get(jwksUri)
        if (!response.status.isSuccess()) error("JWKS endpoint returned ${response.status}")
        val jwks = response.body<JsonObject>()["keys"] as? JsonArray ?: error("JWKS contains no keys")
        keys = jwks.mapNotNull { key ->
            val values = (key as? JsonObject)?.mapValues { (_, value) -> value.toJavaValue() } ?: return@mapNotNull null
            runCatching { Jwk.fromValues(values) }.getOrNull()
        }
        true
    }

    private companion object {
        /** Tolerated clock difference to the provider for `exp`, `iat` and `nbf`. */
        const val LEEWAY_SECONDS = 30L
    }
}

private fun kotlinx.serialization.json.JsonElement.toJavaValue(): Any? = when (this) {
    is JsonPrimitive -> contentOrNull
    is JsonArray -> map { it.toJavaValue() }
    is JsonObject -> mapValues { (_, value) -> value.toJavaValue() }
}

private fun Clock.toJavaClock(): java.time.Clock = object : java.time.Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): java.time.Clock = this
    override fun instant(): java.time.Instant = this@toJavaClock.now().toJavaInstant()
}
