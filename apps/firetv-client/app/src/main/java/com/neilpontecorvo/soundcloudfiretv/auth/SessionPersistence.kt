package com.neilpontecorvo.soundcloudfiretv.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores only the opaque backend sessionId on device so the app can probe
 * the backend at launch for a valid existing session instead of requiring
 * the user to trigger debug auth from Settings on every cold start.
 *
 * Provider access tokens, refresh tokens, and any other secrets stay
 * server-side only — the sessionId alone is not a credential.
 */
class SessionPersistence(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getPersistedSessionId(): String? =
        prefs.getString(KEY_SESSION_ID, null)?.takeIf { it.isNotBlank() }

    fun saveSessionId(sessionId: String) {
        prefs.edit().putString(KEY_SESSION_ID, sessionId).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION_ID).apply()
    }

    companion object {
        private const val PREF_NAME = "session_persistence"
        private const val KEY_SESSION_ID = "last_session_id"
    }
}
