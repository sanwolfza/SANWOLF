package com.sanwolf.daw.ui

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Height of the piano keys under the fixed transport strip. HIDDEN keeps only the strip. */
enum class PianoKeysSize(val keysHeightDp: Int, val label: String) {
    HIDDEN(0, "Hidden"),
    COMPACT(64, "Small"),
    MEDIUM(120, "Medium"),
    LARGE(204, "Large")
}

/**
 * Live-piano keyboard settings, shared by the bottom piano (which plays with them) and the
 * right panel's Keys tab (which edits them). Saved in `sanwolf_daw_prefs`; the octave, range
 * and size keys are the ones the old piano dock used, so existing choices carry over.
 */
class PianoKeyboardSettings(private val prefs: SharedPreferences) {
    var baseOctave by mutableIntStateOf(prefs.getInt(PREF_OCTAVE, 3).coerceIn(1, 6))
        private set
    var octaveCount by mutableIntStateOf(prefs.getInt(PREF_OCTAVES, 2).coerceIn(1, 4))
        private set
    var keysSize by mutableStateOf(readSize())
        private set
    /** Last visible size, restored when the keys are shown again. */
    var lastOpenSize by mutableStateOf(if (keysSize == PianoKeysSize.HIDDEN) PianoKeysSize.MEDIUM else keysSize)
        private set
    /** Velocity of notes played on the on-screen keys (0.1..1). */
    var velocity by mutableFloatStateOf(prefs.getFloat(PREF_VELOCITY, 0.85f).coerceIn(0.1f, 1f))
        private set

    val keysVisible: Boolean get() = keysSize != PianoKeysSize.HIDDEN

    fun changeOctave(delta: Int) {
        baseOctave = (baseOctave + delta).coerceIn(1, 6)
        prefs.edit().putInt(PREF_OCTAVE, baseOctave).apply()
    }

    fun changeOctaveCount(delta: Int) {
        octaveCount = (octaveCount + delta).coerceIn(1, 4)
        prefs.edit().putInt(PREF_OCTAVES, octaveCount).apply()
    }

    fun selectSize(size: PianoKeysSize) {
        keysSize = size
        if (size != PianoKeysSize.HIDDEN) lastOpenSize = size
        prefs.edit().putInt(PREF_KEYS_SIZE, size.ordinal).apply()
    }

    fun toggleKeys() {
        selectSize(if (keysVisible) PianoKeysSize.HIDDEN else lastOpenSize)
    }

    fun changeVelocity(value: Float) {
        velocity = value.coerceIn(0.1f, 1f)
    }

    fun commitVelocity() {
        prefs.edit().putFloat(PREF_VELOCITY, velocity).apply()
    }

    private fun readSize(): PianoKeysSize {
        val stored = prefs.getInt(PREF_KEYS_SIZE, -1)
        if (stored >= 0) return PianoKeysSize.values().getOrNull(stored) ?: PianoKeysSize.MEDIUM
        // Migrate the old dock size (0 collapsed, 1 compact, 2 medium, 3 large): same order
        return PianoKeysSize.values().getOrNull(prefs.getInt(PREF_OLD_DOCK_SIZE, 2)) ?: PianoKeysSize.MEDIUM
    }

    private companion object {
        const val PREF_OCTAVE = "piano_dock_base_octave"
        const val PREF_OCTAVES = "piano_dock_octaves"
        const val PREF_OLD_DOCK_SIZE = "piano_dock_size"
        const val PREF_KEYS_SIZE = "piano_keys_size"
        const val PREF_VELOCITY = "piano_key_velocity"
    }
}
