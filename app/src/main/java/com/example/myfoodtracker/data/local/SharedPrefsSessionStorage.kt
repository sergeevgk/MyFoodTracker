package com.example.myfoodtracker.data.local

import android.content.SharedPreferences
import com.example.myfoodtracker.domain.repository.SessionStorage

class SharedPrefsSessionStorage(
    private val prefs: SharedPreferences
) : SessionStorage {

    override fun getRememberedProfileId(): String? =
        prefs.getString(KEY_REMEMBERED_PROFILE_ID, null)

    override fun setRememberedProfileId(profileId: String?) {
        prefs.edit().apply {
            if (profileId != null) {
                putString(KEY_REMEMBERED_PROFILE_ID, profileId)
            } else {
                remove(KEY_REMEMBERED_PROFILE_ID)
            }
        }.commit()
    }

    override fun clear() {
        // commit() (not apply()) so a logout revocation survives abrupt process death.
        prefs.edit().remove(KEY_REMEMBERED_PROFILE_ID).commit()
    }

    companion object {
        const val PREFS_NAME = "auth_session_prefs"
        private const val KEY_REMEMBERED_PROFILE_ID = "remembered_profile_id"
    }
}
