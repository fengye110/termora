package app.termora

import app.termora.database.DatabaseManager

/**
 * User-facing switches controlling outbound network behaviour.
 *
 * All switches default to disabled so that Termora never sends data
 * (analytics, version checks, plugin-marketplace refreshes, etc.) before
 * the user has explicitly opted in.
 */
object PrivacySettings {
    private const val PREFIX = "Privacy."

    private fun getBoolean(key: String): Boolean {
        return DatabaseManager.getInstance().properties.getString(PREFIX + key)?.toBooleanStrictOrNull() ?: false
    }

    private fun setBoolean(key: String, value: Boolean) {
        DatabaseManager.getInstance().properties.putString(PREFIX + key, value.toString())
    }

    /** Mixpanel usage feedback (launch / plugin install / uninstall). */
    var feedbackEnabled: Boolean
        get() = getBoolean("Feedback")
        set(value) = setBoolean("Feedback", value)

    /** Automatic version update checks against GitHub releases. */
    var updateCheckEnabled: Boolean
        get() = getBoolean("UpdateCheck")
        set(value) = setBoolean("UpdateCheck", value)

    /** Automatic plugin-marketplace refresh. */
    var pluginMarketplaceEnabled: Boolean
        get() = getBoolean("PluginMarketplace")
        set(value) = setBoolean("PluginMarketplace", value)
}
