package es.jvbabi.authentikt.core.config

import es.jvbabi.authentikt.core.AuthentiktUser
import io.ktor.server.application.ApplicationCall
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ValidateDeviceFlowAuthorizationCallbackScope {
    fun generateUserCode(): String = (1..6).joinToString("") { ((1..9).toList() + ('A'..'Z').toList() + ('a'..'z').toList()).random().toString() }
}

/**
 * Receiver of the [OAuthConfigurationBuilder.onAuthorize] callback.
 *
 * @param scopes the scopes requested with the `scope` parameter, empty if none were requested.
 */
class ValidateAuthorizationCallbackScope(
    val scopes: List<String>,
)

class OAuthConfigurationBuilder<USER> {

    typealias ValidateAuthorizationCallback = suspend ValidateAuthorizationCallbackScope.(clientId: String, redirectUri: String) -> OAuthAuthorizationResult
    typealias ValidateDeviceFlowAuthorizationCallback = ValidateDeviceFlowAuthorizationCallbackScope.(clientId: String) -> OAuthDeviceFlowAuthorizationResult
    typealias AuthenticateClientCallback = suspend (clientId: String, clientSecret: String) -> Boolean
    typealias LoggedInUserCallback<USER> = suspend (call: ApplicationCall, application: OAuthAuthorizationResult.Application) -> AuthentiktUser<USER>?

    private var onAuthorize: ValidateAuthorizationCallback? = null
    private var onDeviceFlow: ValidateDeviceFlowAuthorizationCallback? = null
    private var authenticateClient: AuthenticateClientCallback? = null
    private var loggedInUser: LoggedInUserCallback<USER>? = null

    /**
     * How long a device code can be redeemed after it was issued. Sent to the device as `expires_in`.
     */
    var deviceCodeLifetime: Duration = 10.minutes

    /**
     * How long an authorization code issued at the end of an authorization code flow can be exchanged at
     * `POST /oauth/token`.
     */
    var authorizationCodeLifetime: Duration = 1.minutes

    /**
     * Enables the authorization code grant (`GET /oauth/authorize`). [block] validates the client ID and the
     * redirect URI. Only accept redirect URIs that are registered for the client.
     */
    fun onAuthorize(
        block: ValidateAuthorizationCallback
    ) {
        this.onAuthorize = block
    }

    fun onDeviceFlow(
        block: ValidateDeviceFlowAuthorizationCallback
    ) {
        this.onDeviceFlow = block
    }

    /**
     * Verifies the credentials of confidential clients at `POST /oauth/token` (HTTP Basic or the `client_secret`
     * form parameter). Without this callback, only public clients using PKCE can use the authorization code grant.
     */
    fun authenticateClient(
        block: AuthenticateClientCallback
    ) {
        this.authenticateClient = block
    }

    /**
     * Recognizes a user who is already logged in to your app at `GET /oauth/authorize`, for example through the
     * cookie set in `DonePlugin.onSuccess`. If [block] returns a user, the authorization code is issued directly and
     * the browser is redirected back to the client without showing the login UI (single sign-on).
     *
     * Return `null` to show the login UI, for example if there is no valid login or [application] should not be
     * authorized silently. The `prompt` parameter is honored: `prompt=login` always shows the login UI and
     * `prompt=none` never does (`error=login_required` if [block] returns `null`).
     */
    fun loggedInUser(
        block: LoggedInUserCallback<USER>
    ) {
        this.loggedInUser = block
    }

    internal fun build(): OAuthConfiguration {
        require(deviceCodeLifetime.isPositive()) { "deviceCodeLifetime must be positive" }
        require(authorizationCodeLifetime.isPositive()) { "authorizationCodeLifetime must be positive" }
        return OAuthConfiguration(
            onAuthorize = this.onAuthorize,
            onDeviceFlowAuthorize = this.onDeviceFlow,
            authenticateClient = this.authenticateClient,
            loggedInUser = this.loggedInUser,
            deviceCodeLifetime = deviceCodeLifetime,
            authorizationCodeLifetime = authorizationCodeLifetime,
        )
    }
}
