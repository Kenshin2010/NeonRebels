package com.manroid.neonrebels

import com.badlogic.gdx.Preferences
import com.manroid.neonrebels.progress.SaveStore

/**
 * libGDX Preferences behind [SaveStore]. One [write] puts every key of the batch and flushes
 * once. Preferences rewrites its whole file on flush; that is not an atomic, power-loss-safe
 * transaction, which is why the progress snapshot carries a checksum and a backup copy.
 */
class PreferencesStore(private val preferences: Preferences) : SaveStore {
    /**
     * Returns any stored value as text. Older builds saved typed values (Boolean, Int, Float); on
     * Android `getString` on such a key throws ClassCastException, so read the raw entry instead.
     */
    override fun read(key: String): String? {
        if (!preferences.contains(key)) return null
        return preferences.get()[key]?.toString()
    }

    override fun write(values: Map<String, String?>) {
        for ((key, value) in values) {
            if (value == null) preferences.remove(key) else preferences.putString(key, value)
        }
        preferences.flush()
    }
}
