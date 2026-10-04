package es.jvbabi.authentikt.core.step.plugins

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.BaseState

/**
 * The result of the authorization callback: the step the session enters next.
 *
 * Return a [StepEntry] (a plugin, or a plugin started with a prepared state like `junctionPlugin(options)`),
 * or use [alternative] to also offer other steps the user can switch to instead.
 */
sealed interface NextStep<USER>

/**
 * A single step that can be entered: either a [BasePlugin], which creates its initial state via
 * [BasePlugin.createState], or a [PreparedStep], which starts the plugin with a given state.
 */
sealed interface StepEntry<USER> : NextStep<USER>

/**
 * A plugin started with a prepared initial state, like calling a constructor.
 *
 * Plugins create these via [BasePlugin.prepare], usually from an `operator fun invoke(...)`.
 */
class PreparedStep<USER> internal constructor(
    internal val plugin: BasePlugin<USER, *>,
    internal val createState: suspend (session: Session<USER>) -> BaseState,
) : StepEntry<USER>

/**
 * A step together with the steps the user may take instead of it.
 *
 * Switching to an alternative replaces the active step on the session's step stack. The replaced
 * step becomes an alternative itself, so the user can switch back.
 *
 * @param step the step that is entered first.
 * @param alternatives the steps the user can switch to instead of [step].
 */
class StepWithAlternatives<USER> internal constructor(
    val step: StepEntry<USER>,
    val alternatives: List<StepEntry<USER>>,
) : NextStep<USER>

/**
 * Offers [alternatives] that the user can choose instead of this step.
 *
 * ```kotlin
 * authorization { session ->
 *     when {
 *         !session.has(emailPlugin) && !session.has(oidcPlugin) -> emailPlugin alternative listOf(oidcPlugin)
 *         else -> donePlugin
 *     }
 * }
 * ```
 */
infix fun <USER> StepEntry<USER>.alternative(alternatives: List<StepEntry<USER>>): NextStep<USER> =
    StepWithAlternatives(this, alternatives)

/**
 * The plugin this entry enters.
 */
internal val <USER> StepEntry<USER>.plugin: BasePlugin<USER, *>
    get() = when (this) {
        is BasePlugin<USER, *> -> this
        is PreparedStep<USER> -> plugin
    }

/**
 * Creates the initial state of this entry, either prepared or via [BasePlugin.createState].
 */
internal suspend fun <USER> StepEntry<USER>.createInitialState(session: Session<USER>): BaseState = when (this) {
    is BasePlugin<USER, *> -> createState(session)
    is PreparedStep<USER> -> createState(session)
}
