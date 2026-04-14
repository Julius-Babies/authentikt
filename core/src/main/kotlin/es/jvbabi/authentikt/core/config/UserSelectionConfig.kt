package es.jvbabi.authentikt.core.config

class UserSelectionConfig {
    internal var emailConfig: UserSelectionEmailConfig = UserSelectionEmailConfig.Disabled
    internal var usernameConfig: UserSelectionUsernameConfig = UserSelectionUsernameConfig.Disabled

    fun email(withUsername: Boolean = false) {
        this.emailConfig = UserSelectionEmailConfig.Enabled(
            withUsername = withUsername,
        )
    }

    fun username(withEmail: Boolean = false) {
        this.usernameConfig = UserSelectionUsernameConfig.Enabled(
            withEmail = withEmail,
        )
    }

    internal fun validate() {
        assert(listOf(
            emailConfig is UserSelectionEmailConfig.Enabled,
            usernameConfig is UserSelectionUsernameConfig.Enabled,
        ).any()) { "At least one user selection mode is required!" }
    }
}

sealed class UserSelectionEmailConfig {
    data object Disabled : UserSelectionEmailConfig()

    data class Enabled(
        val withUsername: Boolean,
    ) : UserSelectionEmailConfig()
}

sealed class UserSelectionUsernameConfig {
    data object Disabled : UserSelectionUsernameConfig()

    data class Enabled(
        val withEmail: Boolean,
    ): UserSelectionUsernameConfig()
}