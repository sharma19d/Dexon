package com.charon.dexon.data.consent

import android.content.Context

/**
 * Records whether the user has accepted the authorized-use agreement that gates the active
 * testing tools (provider probing, deep-link mutation, system checks). The agreement is shown
 * on first use of those tools, never on app launch, so Standard Mode stays frictionless.
 */
class AuthorizationManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val hasAccepted: Boolean
        get() = prefs.getLong(KEY_ACCEPTED_AT, 0L) > 0L

    fun accept() {
        prefs.edit().putLong(KEY_ACCEPTED_AT, System.currentTimeMillis()).apply()
    }

    /** Lets the user revoke consent from settings; the agreement will be required again. */
    fun revoke() {
        prefs.edit().remove(KEY_ACCEPTED_AT).apply()
    }

    private companion object {
        const val PREFS = "dexon_authorization"
        const val KEY_ACCEPTED_AT = "accepted_at"
    }
}
