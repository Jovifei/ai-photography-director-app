package com.jovi.photoai.data.reference

import android.content.Context

/** Stores only the opaque ID needed to restore a valid private reference after startup recovery. */
internal class ReferenceLibraryPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun lastActiveReferenceId(): String? {
        val storedId = preferences.getString(LAST_ACTIVE_REFERENCE_ID, null) ?: return null
        return if (isSafeOpaqueReferenceId(storedId)) {
            storedId
        } else {
            clearLastActiveReferenceId()
            null
        }
    }

    fun hasStoredLastActiveReferenceId(): Boolean = preferences.contains(LAST_ACTIVE_REFERENCE_ID)

    fun saveLastActiveReferenceId(referenceId: String) {
        require(isSafeOpaqueReferenceId(referenceId)) { "Active reference ID must be opaque" }
        check(preferences.edit().putString(LAST_ACTIVE_REFERENCE_ID, referenceId).commit()) {
            "Active reference ID could not be persisted"
        }
    }

    fun clearLastActiveReferenceId(referenceId: String? = null) {
        val storedId = preferences.getString(LAST_ACTIVE_REFERENCE_ID, null)
        if (referenceId == null || storedId == referenceId) {
            preferences.edit().remove(LAST_ACTIVE_REFERENCE_ID).apply()
        }
    }

    companion object {
        const val FILE_NAME = "ui1_preferences"
        private const val LAST_ACTIVE_REFERENCE_ID = "last_active_reference_id"

        internal fun isSafeOpaqueReferenceId(value: String): Boolean =
            value.matches(Regex("^[a-zA-Z0-9_-]{1,128}$"))
    }
}
