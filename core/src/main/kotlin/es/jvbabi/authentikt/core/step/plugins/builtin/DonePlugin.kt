package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.BaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.server.routing.*
import kotlin.time.Duration

class DonePlugin<USER>(
    configuration: DonePluginConfigurationBuilder<USER>.() -> Unit,
) : BasePlugin<DoneState>(
    namespace = "authentikt-builtin/done",
) {
    private val configuration = DonePluginConfigurationBuilder<USER>()
        .apply(configuration)
        .build()

    override suspend fun createState(session: Session<*>): DoneState {
        return DoneState()
    }

    override fun installRoutes(inRoute: Route) {
        with(inRoute) {
            get {
                val session = call.attributes[SessionKey] as Session<USER>
                val user = session.identifiedUser!!.user

                val step = session.authenticationSteps[session.authenticationSteps.lastIndex].second as DoneState

                if (!step.isCompleted()) {
                    val scope = DonePluginScope()
                    configuration.onSuccess(scope, session, user)

                    for (cookie in scope.cookies) {
                        call.response.cookies.append(
                            name = cookie.name,
                            value = cookie.value,
                            maxAge = cookie.validFor.inWholeSeconds,
                            secure = cookie.secure,
                            httpOnly = cookie.httpOnly,
                            path = cookie.path
                        )
                    }

                    val cookieNames = scope.cookies.map { it.name }

                    session.authenticationSteps[session.authenticationSteps.lastIndex] = this@DonePlugin to DoneState(completed = true)

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
        name: String,
        value: String,
        validFor: Duration,
        httpOnly: Boolean = true,
        path: String = "/",
        secure: Boolean = true,
    ) {
        _cookies.add(Cookie(name, value, validFor, httpOnly, path, secure))
    }

    fun redirect(to: String) {
        _redirectTo = to
    }
}

data class Cookie(
    val name: String,
    val value: String,
    val validFor: Duration,
    val httpOnly: Boolean,
    val path: String,
    val secure: Boolean,
)

class DonePluginConfigurationBuilder<USER> {
    private var onSuccess: OnSuccess<USER>? = null

    fun onSuccess(block: OnSuccess<USER>) {
        this.onSuccess = block
    }

    internal fun build(): DonePluginConfiguration<USER> {
        requireNotNull(this.onSuccess) { "onSuccess callback is required" }
        return DonePluginConfiguration(onSuccess = this.onSuccess!!)
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
)

typealias OnSuccess<USER> = suspend DonePluginScope.(session: Session<USER>, user: USER) -> Unit
