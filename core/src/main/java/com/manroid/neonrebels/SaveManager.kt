package com.manroid.neonrebels

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Device settings. They share the Preferences file with the campaign snapshot (see
 * [PreferencesStore]) but use their own keys, so starting a new campaign never touches them.
 */
class SaveManager {
    // Storage key predates the NEON REBELS name; keep it so existing settings and progress survive.
    val preferences: Preferences = Gdx.app.getPreferences("nova-light-core-v1")

    fun showControls(): Boolean = preferences.getBoolean("showControls", true)

    fun setShowControls(value: Boolean) {
        preferences.putBoolean("showControls", value).flush()
    }

    /** Collider/hitbox/AI/FPS overlay for testing; off by default. */
    fun debugOverlay(): Boolean = preferences.getBoolean("debugOverlay", false)

    fun setDebugOverlay(value: Boolean) {
        preferences.putBoolean("debugOverlay", value).flush()
    }
}
