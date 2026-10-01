package com.example.audio

/**
 * Authoritative Instrument Categories for the SANWOLF Audio Studio.
 */
enum class InstrumentCategory(val displayName: String) {
    DRUMS("Drums"),
    BASS("Bass"),
    SYNTHS("Synths"),
    PADS("Pads"),
    KEYS_PIANO("Keys / Piano"),
    STRINGS("Strings"),
    BRASS_WINDS("Brass / Winds"),
    PLUCKS_MALLETS("Plucks / Mallets"),
    WORLD_PERCUSSION("World / Afro Percussion"),
    SOUND_EFFECTS_TEXTURES("Sound Effects / Textures"),

    // Backward-compatibility aliases for legacy code
    KEYBOARDS("Keyboards"),
    PIANO_KEYS("Keys / Piano"),
    SYNTHESIZERS("Synths"),
    ORCHESTRAL_STRINGS("Strings"),
    DRUMS_PERCUSSION("Drums"),
    AMBIENT_TEXTURES("Ambient Textures"),
    VOX_FX("Vox FX")
}
