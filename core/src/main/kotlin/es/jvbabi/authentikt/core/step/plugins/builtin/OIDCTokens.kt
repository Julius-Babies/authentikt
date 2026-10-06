package es.jvbabi.authentikt.core.step.plugins.builtin

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The token response returned by the OIDC provider's token endpoint after exchanging the authorization code.
 *
 * Persist [refreshToken] if your application needs to call the provider (or a resource server) on behalf of the
 * user later on. Whether a refresh token is issued depends on the provider and the requested scopes, see
 * [OIDCPluginConfigurationBuilder.scopes] and [OIDCPluginConfigurationBuilder.authorizationParameter].
 *
 * @property accessToken The access token (`access_token`).
 * @property tokenType The token type (`token_type`), usually `Bearer`.
 * @property refreshToken The refresh token (`refresh_token`), or `null` if the provider did not issue one.
 * @property idToken The raw, encoded ID token JWT (`id_token`), or `null` if none was issued. With the `openid` scope,
 * its signature and claims have been verified before [OIDCPluginConfigurationBuilder.onUserInfo] is called.
 * @property expiresIn Lifetime of the access token (`expires_in`), or `null` if the provider did not send it.
 * @property expiresAt The point in time the access token expires, calculated from [receivedAt] and [expiresIn].
 * @property scopes The granted scopes (`scope`), or `null` if the provider did not send them. Per RFC 6749 this
 * means the granted scopes are identical to the requested ones.
 * @property receivedAt The point in time the token response was received.
 * @property raw The complete JSON token response, including provider-specific fields such as Keycloak's
 * `refresh_expires_in`.
 */
data class OIDCTokens(
    val accessToken: String,
    val tokenType: String?,
    val refreshToken: String?,
    val idToken: String?,
    val expiresIn: Duration?,
    val scopes: List<String>?,
    val receivedAt: Instant,
    val raw: JsonObject,
) {
    val expiresAt: Instant? get() = expiresIn?.let { receivedAt + it }

    override fun toString(): String =
        "OIDCTokens(tokenType=$tokenType, expiresIn=$expiresIn, scopes=$scopes, receivedAt=$receivedAt, " +
            "hasRefreshToken=${refreshToken != null}, hasIdToken=${idToken != null})"

    companion object {
        /**
         * Parses a token endpoint response.
         *
         * @throws IllegalArgumentException if the response contains no `access_token`.
         */
        internal fun fromTokenResponse(response: JsonObject, receivedAt: Instant): OIDCTokens {
            fun string(key: String) = (response[key] as? JsonPrimitive)?.contentOrNull

            val accessToken = requireNotNull(string("access_token")) { "Token response contains no access_token" }
            return OIDCTokens(
                accessToken = accessToken,
                tokenType = string("token_type"),
                refreshToken = string("refresh_token"),
                idToken = string("id_token"),
                expiresIn = string("expires_in")?.toLongOrNull()?.seconds,
                scopes = string("scope")?.split(" ")?.filter { it.isNotBlank() },
                receivedAt = receivedAt,
                raw = response,
            )
        }
    }
}

/**
 * Receiver of the [OIDCPluginConfigurationBuilder.onUserInfo] callback.
 *
 * @property tokens The full token response of the provider, including the refresh token and ID token.
 * @property claims The claims of the verified ID token, e.g. `sub`, `email` or `preferred_username`. `null` if the
 * `openid` scope is not requested. Never `null` if no [OIDCPluginConfigurationBuilder.userInfoEndpoint] is set.
 */
class OIDCUserInfoScope internal constructor(
    val tokens: OIDCTokens,
    val claims: JsonObject?,
)
