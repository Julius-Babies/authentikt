package es.jvbabi.authentikt.core

/**
 * Abstract wrapper around the application-specific user type.
 *
 * Implement this class to bridge between your application's user model and
 * the authentikt framework. The library calls [getEmail], [getUsername],
 * and [getDisplayName] to populate session metadata for the frontend.
 *
 * @param user your application's user object.
 */
abstract class AuthentiktUser<USER>(val user: USER) {

    /**
     * Returns the user's email address, or `null` if not available.
     *
     * Used by built-in user-selection plugins like
 * [es.jvbabi.authentikt.core.userselection.plugins.builtin.EmailUserSelectionPlugin].
     */
    abstract suspend fun getEmail(): String?

    /**
     * Returns the user's login name, or `null` if not available.
     */
    abstract suspend fun getUsername(): String?

    /**
     * Returns the user's display name, or `null` if not available.
     *
     * This value is sent to the frontend in the flow state response.
     */
    abstract suspend fun getDisplayName(): String?
}
