package es.jvbabi.authentikt.core.step.plugins.builtin

import es.jvbabi.authentikt.core.AuthentiktInstance
import es.jvbabi.authentikt.core.routes.flow.respondStepNotActive
import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.session.SessionKey
import es.jvbabi.authentikt.core.step.BaseState
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.authentikt.core.step.plugins.StepEntry
import es.jvbabi.authentikt.core.step.plugins.plugin
import es.jvbabi.authentikt.core.utils.buildGenericMap
import es.jvbabi.authentikt.core.utils.respondGson
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lets the user choose which step to take next.
 *
 * The junction is a shell for the options it is started with from the authorization callback via
 * [invoke]. The selected option is stored in the step's state and can be read in the authorization callback
 * via [selectedOption].
 *
 * ### Usage
 * ```kotlin
 * val junctionPlugin = JunctionPlugin<User>()
 * install(junctionPlugin)
 *
 * authorization { session ->
 *     when {
 *         !session.has(junctionPlugin) -> junctionPlugin(listOf(totpPlugin, passkeyPlugin))
 *         !session.has(totpPlugin) && !session.has(passkeyPlugin) -> junctionPlugin.selectedOption(session)!!
 *         else -> donePlugin
 *     }
 * }
 * ```
 *
 * Install several junctions with different [namespace]s to use more than one in a flow.
 *
 * @param namespace the namespace of this junction.
 */
class JunctionPlugin<USER>(
    namespace: String = "authentikt-builtin/junction",
) : BasePlugin<USER, JunctionState<USER>>(
    namespace = namespace,
) {
    /**
     * Starts the junction with the given [options] to choose from.
     */
    operator fun invoke(options: List<StepEntry<USER>>): StepEntry<USER> =
        prepare { JunctionState(options = options, selected = null) }

    /**
     * A junction entered without [invoke] has no options.
     */
    override suspend fun createState(session: Session<*>): JunctionState<USER> {
        return JunctionState(options = emptyList(), selected = null)
    }

    /**
     * Returns the option the user selected in this junction, or `null` if the junction has not been completed.
     */
    fun selectedOption(session: Session<USER>): StepEntry<USER>? {
        val state = session.authenticationSteps.firstOrNull { it.first == this }?.second as? JunctionState<USER>
        return state?.selected
    }

    override fun installRoutes(inRoute: Route, authentiktInstance: AuthentiktInstance<USER>) {
        with(inRoute) {
            post {
                val session = call.attributes[SessionKey] as Session<USER>
                if (!session.isActive(this@JunctionPlugin)) return@post call.respondStepNotActive()
                val request = call.receive<JunctionRequest>()

                val state = session.authenticationSteps.last().second as JunctionState<USER>
                val selected = state.options.find { it.plugin.namespace == request.namespace }
                if (selected == null) {
                    call.respondGson(
                        value = buildGenericMap {
                            put("error", "option_not_available")
                            put("error_description", "The step is not an option of this junction.")
                        },
                        status = HttpStatusCode.BadRequest,
                    )
                    return@post
                }

                val completed = session.completeStep(this@JunctionPlugin, state.copy(selected = selected))
                if (!completed) return@post call.respondStepNotActive()

                call.respondGson(buildGenericMap {
                    put("type", "success")
                })
            }
        }
    }
}

/**
 * State for a junction step.
 *
 * @param options the steps the user can choose from.
 * @param selected the step the user chose, or `null` while the junction is active.
 */
data class JunctionState<USER>(
    val options: List<StepEntry<USER>>,
    val selected: StepEntry<USER>?,
) : BaseState {
    override suspend fun isCompleted(): Boolean = selected != null

    override suspend fun createClientState(session: Session<*>): Map<String, Any?> = buildGenericMap {
        put("options", options.map { it.plugin.namespace })
        put("selected", selected?.plugin?.namespace)
    }
}

@Serializable
internal data class JunctionRequest(
    @SerialName("namespace") val namespace: String,
)
