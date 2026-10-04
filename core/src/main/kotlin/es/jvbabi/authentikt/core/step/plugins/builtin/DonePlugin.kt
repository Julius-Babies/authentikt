package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.AuthentiktInstance
import es.jvbabi.authentikt.core.config.OAuthAccessToken
import es.jvbabi.authentikt.core.routes.flow.respondStepNotActive
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionDestination
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.BaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.http.*
import io.ktor.server.routing.*

class DonePlugin<USER>(
    configuration: DonePluginConfigurationBuilder<USER>.() -> Unit,
) : BasePlugin<USER, DoneState>(
    namespace = "authentikt-builtin/done",
) {
    internal val configuration = DonePluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    override suspend fun createState(session: Session<*>): DoneState {
        return DoneState()
    }

    override fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {
        with(inRoute) {
            get {
                val session = call.attributes[SessionKey] as Session<USER>
                if (!session.isActive(this@DonePlugin)) return@get call.respondStepNotActive()
                if (session.destination is SessionDestination.DeviceFlow) {
                    call.respondGson(buildGenericMap {
                        put("type", "device_flow_success")
                    })
                    return@get
                }
                val user = session.identifiedUser!!.user

                val step = session.authenticationSteps[session.authenticationSteps.lastIndex].second as DoneState

                if (!step.isCompleted()) {
                    val scope = DonePluginScope()
                    configuration.onSuccess(scope, session, user)

                    for (cookie in scope.cookies) {
                        call.response.cookies.append(cookie)
                    }

                    val cookieNames = scope.cookies.map { it.name }

                    session.authenticationSteps[session.authenticationSteps.lastIndex] = this@DonePlugin to DoneState(completed = true)
                    session.invalidate()

                    if (scope.redirectTo != null) {
                        call.respondGson(buildGenericMap {
                            put("type", "redirect")
                            put("to", scope.redirectTo)
                            if (cookieNames.isNotEmpty()) put("cookies", cookieNames)
                        })
                        return@get
                    }

                    if (cookieNames.isNotEmpty()) {
                        call.respondGson(buildGenericMap {
                            put("type", "success")
                            put("cookies", cookieNames)
                        })
                        return@get
                    }
                }

                call.respondGson(buildGenericMap {
                    put("type", "success")
                })
            }
        }
    }
}

class DonePluginScope {
    private val _cookies = mutableListOf<Cookie>()
    private var _redirectTo: String? = null

    val cookies: List<Cookie> get() = _cookies.toList()
    val redirectTo: String? get() = _redirectTo

    fun cookie(
        cookie: Cookie
    ) {
        _cookies.add(cookie)
    }

    fun redirect(to: String) {
        _redirectTo = to
    }
}

class DonePluginConfigurationBuilder<USER> {
    private var onSuccess: OnSuccess<USER>? = null
    private var onOAuthSuccess: OnOAuthSuccess<USER>? = null

    fun onSuccess(block: OnSuccess<USER>) {
        this.onSuccess = block
    }

    fun onOAuthSuccess(block: OnOAuthSuccess<USER>) {
        this.onOAuthSuccess = block
    }

    internal fun build(): DonePluginConfiguration<USER> {
        requireNotNull(this.onSuccess) { "onSuccess callback is required" }
        return DonePluginConfiguration(
            onSuccess = this.onSuccess!!,
            onOAuthSuccess = this.onOAuthSuccess,
        )
    }
}

data class DoneState(
    private val completed: Boolean = false,
) : BaseState {
    override suspend fun isCompleted(): Boolean = completed
    override suspend fun createClientState(session: Session<*>): Map<String, Any?> = emptyMap()
}

internal data class DonePluginConfiguration<USER>(
    val onSuccess: OnSuccess<USER>,
    val onOAuthSuccess: OnOAuthSuccess<USER>?,
)

typealias OnSuccess<USER> = suspend DonePluginScope.(session: Session<USER>, user: USER) -> Unit
typealias OnOAuthSuccess<USER> = suspend (session: Session<USER>, user: USER) -> OAuthAccessToken