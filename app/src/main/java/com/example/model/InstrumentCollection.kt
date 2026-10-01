package com.example.model

object InstrumentCollection {
    val allPresets: List<InstrumentPreset> = listOf(
        // =========================================================================
        // 1. KEYS & PIANO (Concert Grands, Uprights, Vintage E-Pianos, Tonewheel Organs)
        // =========================================================================
        InstrumentPreset(
            id = "grand_piano",
            name = "Grand Piano",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 6500f,
            resonance = 1.0f,
            attackMs = 4f,
            decayMs = 350f,
            sustainLevel = 0.35f,
            releaseMs = 450f,
            description = "Rich concert grand piano with dynamic hammer attack and acoustic chime resonance",
            colorHex = 0xFFFFD700
        ),
        InstrumentPreset(
            id = "upright_piano",
            name = "Concert Upright Piano",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 5200f,
            resonance = 1.1f,
            attackMs = 6f,
            decayMs = 280f,
            sustainLevel = 0.4f,
            releaseMs = 360f,
            description = "Intimate studio acoustic upright piano with warm woody cabinet tone",
            colorHex = 0xFFFFC107
        ),
        InstrumentPreset(
            id = "rhodes_mk1",
            name = "Rhodes Mk1 Tines",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SINE,
            cutoffHz = 4200f,
            resonance = 1.2f,
            attackMs = 8f,
            decayMs = 400f,
            sustainLevel = 0.5f,
            releaseMs = 500f,
            description = "Neo-soul and jazz electric piano with metallic tine bell chime and velvety body",
            colorHex = 0xFF80D8FF
        ),
        InstrumentPreset(
            id = "wurlitzer_200",
            name = "Wurlitzer 200A",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 4500f,
            resonance = 1.3f,
            attackMs = 6f,
            decayMs = 300f,
            sustainLevel = 0.45f,
            releaseMs = 400f,
            description = "Vibrant reed-driven vintage electric piano with subtle tremolo warmth",
            colorHex = 0xFFFFAB00
        ),
        InstrumentPreset(
            id = "upright_felt_piano",
            name = "Upright Felt Piano",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 3200f,
            resonance = 1.0f,
            attackMs = 12f,
            decayMs = 280f,
            sustainLevel = 0.4f,
            releaseMs = 380f,
            description = "Intimate felt upright piano ideal for emotional chords and lofi ballads",
            colorHex = 0xFFB0BEC5
        ),
        InstrumentPreset(
            id = "bright_house_piano",
            name = "Bright House Piano",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 5800f,
            resonance = 1.4f,
            attackMs = 4f,
            decayMs = 220f,
            sustainLevel = 0.6f,
            releaseMs = 260f,
            description = "Cutting 90s dance and house M1-style piano stab for driving anthems",
            colorHex = 0xFFFFE082
        ),
        InstrumentPreset(
            id = "gospel_grand",
            name = "Gospel Grand Piano",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 6200f,
            resonance = 1.2f,
            attackMs = 5f,
            decayMs = 320f,
            sustainLevel = 0.5f,
            releaseMs = 420f,
            description = "Expansive church and gospel grand piano with wide stereo dynamics",
            colorHex = 0xFFFFD54F
        ),
        InstrumentPreset(
            id = "synth_piano",
            name = "Vintage Synth Piano",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 4800f,
            resonance = 1.3f,
            attackMs = 5f,
            decayMs = 200f,
            sustainLevel = 0.5f,
            releaseMs = 240f,
            description = "Classic 80s digital FM & analog hybrid piano with sparkling transient",
            colorHex = 0xFF81D4FA
        ),
        InstrumentPreset(
            id = "hammond_b3",
            name = "Hammond B3 Organ",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 7500f,
            resonance = 1.0f,
            attackMs = 2f,
            decayMs = 100f,
            sustainLevel = 0.95f,
            releaseMs = 60f,
            description = "Classic drawbar tonewheel organ with rich harmonics and rotary modulation",
            colorHex = 0xFFFF9100
        ),
        InstrumentPreset(
            id = "church_organ",
            name = "Church Pipe Organ",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 8500f,
            resonance = 1.1f,
            attackMs = 18f,
            decayMs = 400f,
            sustainLevel = 0.95f,
            releaseMs = 350f,
            description = "Majestic cathedral pipe organ with deep multi-octave acoustic body",
            colorHex = 0xFFE040FB
        ),
        InstrumentPreset(
            id = "electric_organ",
            name = "Electric Gospel Organ",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 6800f,
            resonance = 1.2f,
            attackMs = 3f,
            decayMs = 140f,
            sustainLevel = 0.9f,
            releaseMs = 90f,
            description = "Punchy transistor electric organ for RnB, Gospel, and Reggae skanks",
            colorHex = 0xFFFF7043
        ),

        // =========================================================================
        // 2. STRINGS & ORCHESTRAL (Violin, Cello, Symphonic Ensemble, Chamber)
        // =========================================================================
        InstrumentPreset(
            id = "violin_solo",
            name = "Violin Solo",
            category = InstrumentCategory.ORCHESTRAL_STRINGS,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 4800f,
            resonance = 2.0f,
            attackMs = 60f,
            decayMs = 200f,
            sustainLevel = 0.85f,
            releaseMs = 320f,
            description = "Expressive bowed solo violin with natural acoustic vibrato and warmth",
            colorHex = 0xFF76FF03
        ),
        InstrumentPreset(
            id = "cello_solo",
            name = "Legato Cello Solo",
            category = InstrumentCategory.ORCHESTRAL_STRINGS,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 2800f,
            resonance = 1.8f,
            attackMs = 110f,
            decayMs = 240f,
            sustainLevel = 0.9f,
            releaseMs = 400f,
            description = "Deep resonant solo cello with rich low-mid string body and bowing friction",
            colorHex = 0xFF64DD17
        ),
        InstrumentPreset(
            id = "string_ensemble",
            name = "Symphonic String Ensemble",
            category = InstrumentCategory.ORCHESTRAL_STRINGS,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 3800f,
            resonance = 1.5f,
            attackMs = 150f,
            decayMs = 350f,
            sustainLevel = 0.92f,
            releaseMs = 500f,
            description = "Full symphonic strings section with multi-voice detuned ensemble chorus",
            colorHex = 0xFF00E676
        ),
        InstrumentPreset(
            id = "acoustic_chamber_strings",
            name = "Chamber Strings Quartet",
            category = InstrumentCategory.ORCHESTRAL_STRINGS,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 3400f,
            resonance = 1.6f,
            attackMs = 90f,
            decayMs = 260f,
            sustainLevel = 0.88f,
            releaseMs = 380f,
            description = "Crisp and intimate 4-piece string quartet for film score and acoustic pop",
            colorHex = 0xFF69F0AE
        ),
        InstrumentPreset(
            id = "dark_violin_marcato",
            name = "Dark Violin Marcato",
            category = InstrumentCategory.ORCHESTRAL_STRINGS,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 4200f,
            resonance = 2.2f,
            attackMs = 35f,
            decayMs = 180f,
            sustainLevel = 0.8f,
            releaseMs = 250f,
            description = "Aggressive accent marcato violin bowing for dramatic cinematic tension",
            colorHex = 0xFFB2FF59
        ),

        // =========================================================================
        // 3. BRASS & WINDS (Trumpet, Saxophone, Horn Section, Flutes)
        // =========================================================================
        InstrumentPreset(
            id = "trumpet_solo",
            name = "Trumpet Solo",
            category = InstrumentCategory.BRASS_WINDS,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 5200f,
            resonance = 2.2f,
            attackMs = 25f,
            decayMs = 120f,
            sustainLevel = 0.8f,
            releaseMs = 160f,
            description = "Crisp bright trumpet with punchy acoustic brass bite and formant swell",
            colorHex = 0xFFFFEA00
        ),
        InstrumentPreset(
            id = "tenor_saxophone",
            name = "Tenor Saxophone",
            category = InstrumentCategory.BRASS_WINDS,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 4200f,
            resonance = 2.4f,
            attackMs = 30f,
            decayMs = 150f,
            sustainLevel = 0.75f,
            releaseMs = 200f,
            description = "Warm smoky jazz and afrobeat saxophone with breath tone and reed bite",
            colorHex = 0xFFFF9100
        ),
        InstrumentPreset(
            id = "brass_horn_section",
            name = "Brass Horn Section",
            category = InstrumentCategory.BRASS_WINDS,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 4800f,
            resonance = 1.8f,
            attackMs = 40f,
            decayMs = 180f,
            sustainLevel = 0.85f,
            releaseMs = 240f,
            description = "Heavy brass section stabs and swells with wide stereo power",
            colorHex = 0xFFFF6D00
        ),
        InstrumentPreset(
            id = "pure_sine_flute",
            name = "Sine Solo Flute",
            category = InstrumentCategory.BRASS_WINDS,
            waveform = SynthWaveform.SINE,
            cutoffHz = 4800f,
            resonance = 1.1f,
            attackMs = 40f,
            decayMs = 100f,
            sustainLevel = 0.9f,
            releaseMs = 220f,
            description = "Silky pure breathy solo flute for emotional melodies and top lines",
            colorHex = 0xFF00B0FF
        ),
        InstrumentPreset(
            id = "shakuhachi_flute",
            name = "Shakuhachi Bamboo Flute",
            category = InstrumentCategory.BRASS_WINDS,
            waveform = SynthWaveform.SINE,
            cutoffHz = 3800f,
            resonance = 1.4f,
            attackMs = 50f,
            decayMs = 130f,
            sustainLevel = 0.85f,
            releaseMs = 240f,
            description = "Authentic Japanese bamboo flute with heavy breath chiff and pitch slide",
            colorHex = 0xFF40C4FF
        ),

        // =========================================================================
        // 4. BASSES & 808s (Sub 808s, Moog Ladder, Reese, Acid 303, Log Bass, Slap)
        // =========================================================================
        InstrumentPreset(
            id = "deep_808",
            name = "808 Sub Boom",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SINE,
            cutoffHz = 450f,
            resonance = 1.1f,
            attackMs = 5f,
            decayMs = 300f,
            sustainLevel = 0.8f,
            releaseMs = 350f,
            description = "Ground-shaking sine sub bass with clean harmonic punch and fast pitch dive",
            colorHex = 0xFFFF5722
        ),
        InstrumentPreset(
            id = "moog_model_d",
            name = "Moog Model D Bass",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 1800f,
            resonance = 3.8f,
            attackMs = 10f,
            decayMs = 160f,
            sustainLevel = 0.7f,
            releaseMs = 150f,
            description = "Legendary 24dB ladder-filtered analog synth bass with warm harmonic grit",
            colorHex = 0xFFFFAB00
        ),
        InstrumentPreset(
            id = "reese_dark",
            name = "Reese Dark Sub",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 850f,
            resonance = 2.0f,
            attackMs = 25f,
            decayMs = 220f,
            sustainLevel = 0.9f,
            releaseMs = 350f,
            description = "Thick detuned double-saw bass for DnB, Trap, Drill, and Darkwave",
            colorHex = 0xFFD500F9
        ),
        InstrumentPreset(
            id = "acid_303",
            name = "Acid 303 Resonator",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 1600f,
            resonance = 4.8f,
            attackMs = 6f,
            decayMs = 120f,
            sustainLevel = 0.4f,
            releaseMs = 140f,
            description = "Iconic resonant acid bass with screaming envelope filter sweeps",
            colorHex = 0xFF76FF03
        ),
        InstrumentPreset(
            id = "amapiano_log_bass",
            name = "Amapiano Log Bass",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SINE,
            cutoffHz = 1200f,
            resonance = 1.5f,
            attackMs = 6f,
            decayMs = 140f,
            sustainLevel = 0.6f,
            releaseMs = 160f,
            description = "Resonant woody FM log bass that delivers iconic Amapiano and Afro House punch",
            colorHex = 0xFFFFAB00
        ),
        InstrumentPreset(
            id = "slap_synth_bass",
            name = "Funk Slap Bass",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 1400f,
            resonance = 2.0f,
            attackMs = 4f,
            decayMs = 110f,
            sustainLevel = 0.3f,
            releaseMs = 120f,
            description = "Snappy funk synth bass with fast transient attack and resonant slap tone",
            colorHex = 0xFFFFD600
        ),
        InstrumentPreset(
            id = "gqom_heavy_sub",
            name = "Gqom Heavy Sub",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 800f,
            resonance = 1.8f,
            attackMs = 12f,
            decayMs = 200f,
            sustainLevel = 0.8f,
            releaseMs = 250f,
            description = "Aggressive detuned and saturated bass with heavy Durban Gqom punch",
            colorHex = 0xFFD500F9
        ),
        InstrumentPreset(
            id = "sub_zero_triangle",
            name = "Sub Zero Triangle",
            category = InstrumentCategory.BASS_808,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 650f,
            resonance = 1.2f,
            attackMs = 15f,
            decayMs = 220f,
            sustainLevel = 0.85f,
            releaseMs = 240f,
            description = "Clean sub-bass with gentle upper harmonics for small mobile speakers",
            colorHex = 0xFF00E5FF
        ),

        // =========================================================================
        // 5. LEADS & SYNTHS (SuperSaws, Cyber Lead, Plucks, Bells, Chiptune)
        // =========================================================================
        InstrumentPreset(
            id = "cyber_lead",
            name = "Cyber Lead",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 3800f,
            resonance = 1.3f,
            attackMs = 10f,
            decayMs = 120f,
            sustainLevel = 0.8f,
            releaseMs = 150f,
            description = "Punchy cutting saw lead for aggressive hooks and EDM drops",
            colorHex = 0xFF00E5FF
        ),
        InstrumentPreset(
            id = "supersaw_arena",
            name = "SuperSaw Anthem",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 5500f,
            resonance = 1.6f,
            attackMs = 12f,
            decayMs = 180f,
            sustainLevel = 0.85f,
            releaseMs = 260f,
            description = "Massive stadium supersaw with 5-oscillator stereo unison spread",
            colorHex = 0xFFFF007F
        ),
        InstrumentPreset(
            id = "analog_pluck",
            name = "Neon Pluck",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 3200f,
            resonance = 1.8f,
            attackMs = 3f,
            decayMs = 90f,
            sustainLevel = 0.15f,
            releaseMs = 100f,
            description = "Snappy percussive square pluck ideal for arpeggios and fast runs",
            colorHex = 0xFFFFD700
        ),
        InstrumentPreset(
            id = "nostalgic_8bit_chip",
            name = "8-Bit Nostalgic Chip",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SQUARE,
            cutoffHz = 6000f,
            resonance = 1.0f,
            attackMs = 2f,
            decayMs = 70f,
            sustainLevel = 0.7f,
            releaseMs = 70f,
            description = "Retro gaming sound with snappy transients and clean pulse modulation",
            colorHex = 0xFF00E676
        ),
        InstrumentPreset(
            id = "glass_bell",
            name = "Glass Crystal Bell",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SINE,
            cutoffHz = 6500f,
            resonance = 1.4f,
            attackMs = 3f,
            decayMs = 350f,
            sustainLevel = 0.2f,
            releaseMs = 500f,
            description = "Shimmering crystalline bell chime with rich harmonic ring",
            colorHex = 0xFF80D8FF
        ),
        InstrumentPreset(
            id = "afro_house_pluck",
            name = "Afro House Pluck Lead",
            category = InstrumentCategory.SYNTH_LEAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 3000f,
            resonance = 1.5f,
            attackMs = 5f,
            decayMs = 110f,
            sustainLevel = 0.25f,
            releaseMs = 130f,
            description = "Resonant syncopated pluck designed for modern Afro House melodic patterns",
            colorHex = 0xFFFFAB00
        ),

        // =========================================================================
        // 6. PADS & ATMOSPHERES (Lush Pads, Warm Analog Swells, Granular Textures)
        // =========================================================================
        InstrumentPreset(
            id = "lush_pad",
            name = "Lush Ambient Pad",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.TRIANGLE,
            cutoffHz = 2400f,
            resonance = 1.2f,
            attackMs = 220f,
            decayMs = 300f,
            sustainLevel = 0.9f,
            releaseMs = 600f,
            description = "Cinematic slow-swell pad with warm analog drift and lush tail",
            colorHex = 0xFF1DE9B6
        ),
        InstrumentPreset(
            id = "warm_analog_pad",
            name = "Warm Analog Pad",
            category = InstrumentCategory.KEYS_PAD,
            waveform = SynthWaveform.SAWTOOTH,
            cutoffHz = 1900f,
            resonance = 1.1f,
            attackMs = 180f,
            decayMs = 280f,
            sustainLevel = 0.85f,
            releaseMs = 550f,
            description = "Velvety warm filtered sawtooth pad for lush chord beds and ballads",
            colorHex = 0xFFFF7043
        ),

        // =========================================================================
        // 7. DRUM KITS & INDIVIDUAL DRUMS (808s, 909s, Boom Bap, Kicks, Snares, Hats)
        // =========================================================================
        InstrumentPreset(
            id = "trap_808_kit",
            name = "Trap 808 Heavy Kit",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.SAWTOOTH,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Heavy 808 sub kick, snappy snare, clap, and rolled hi-hats",
            colorHex = 0xFFFF1744
        ),
        InstrumentPreset(
            id = "electro_909_kit",
            name = "Electro 909 Dance Kit",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.SAWTOOTH,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Punchy 909 4-on-the-floor kick, open hat, and crisp handclap",
            colorHex = 0xFF00E676
        ),
        InstrumentPreset(
            id = "boom_bap_kit",
            name = "Boom Bap Vintage Kit",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.SQUARE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Classic 90s sampled acoustic drums with dusty punch and warmth",
            colorHex = 0xFFFF9100
        ),
        InstrumentPreset(
            id = "amapiano_kit",
            name = "Amapiano Log & Shaker",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.TRIANGLE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "South African log drum bass hits, shakers, and rim percussions",
            colorHex = 0xFFFFD700
        ),
        InstrumentPreset(
            id = "drum_kick_deep",
            name = "Deep Sub Kick",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.SINE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Deep low-end sub kick drum with clean pitch envelope punch",
            colorHex = 0xFFFF5722
        ),
        InstrumentPreset(
            id = "drum_kick_tech",
            name = "Tech House Kick",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.SAWTOOTH,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Clicky transient kick designed for driving Techno and Tech House grooves",
            colorHex = 0xFFFF3D00
        ),
        InstrumentPreset(
            id = "drum_snare_trap",
            name = "Snare Trap Crack",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.NOISE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Snappy high-frequency trap snare with tight noise crack",
            colorHex = 0xFFFF2A6D
        ),
        InstrumentPreset(
            id = "drum_clap_clean",
            name = "Clean Hand Clap",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.NOISE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Clean multi-tap stereo handclap for pop, house, and trap",
            colorHex = 0xFFFF4081
        ),
        InstrumentPreset(
            id = "drum_hihat_closed",
            name = "Closed Hi-Hat",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.NOISE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Crisp and fast closed hi-hat with metallic high frequencies",
            colorHex = 0xFF76FF03
        ),
        InstrumentPreset(
            id = "drum_hihat_open",
            name = "Open Hi-Hat Sizzle",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.NOISE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Sizzling long open hi-hat for off-beat house rhythms",
            colorHex = 0xFF64DD17
        ),
        InstrumentPreset(
            id = "drum_crash_cymbal",
            name = "Crash Cymbal",
            category = InstrumentCategory.DRUM_KIT,
            waveform = SynthWaveform.NOISE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Explosive brass crash cymbal with long decaying stereo tail",
            colorHex = 0xFFFFD700
        ),

        // =========================================================================
        // 8. WORLD PERCUSSION (Djembe, Congas, Bongos, Talking Drum, Darbuka, Tabla)
        // =========================================================================
        InstrumentPreset(
            id = "djembe_solo",
            name = "Djembe African Drum",
            category = InstrumentCategory.WORLD_PERCUSSION,
            waveform = SynthWaveform.TRIANGLE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "West African djembe with deep bass thud and sharp rim slap",
            colorHex = 0xFFFF6D00
        ),
        InstrumentPreset(
            id = "conga_bongo_set",
            name = "Congas & Bongos",
            category = InstrumentCategory.WORLD_PERCUSSION,
            waveform = SynthWaveform.TRIANGLE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Afro-Cuban conga open tones and bongo slaps with natural skin resonance",
            colorHex = 0xFFFFAB00
        ),
        InstrumentPreset(
            id = "talking_drum",
            name = "Talking Drum",
            category = InstrumentCategory.WORLD_PERCUSSION,
            waveform = SynthWaveform.SINE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Hourglass Yoruba pitch-bending pressure drum with organic inflection",
            colorHex = 0xFFFFD600
        ),
        InstrumentPreset(
            id = "darbuka_tabla",
            name = "Darbuka & Tabla",
            category = InstrumentCategory.WORLD_PERCUSSION,
            waveform = SynthWaveform.TRIANGLE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Middle-Eastern & Indian percussion with ringing metallic rim harmonics",
            colorHex = 0xFF00E5FF
        ),
        InstrumentPreset(
            id = "shakers_shekere",
            name = "Shakers & Shekere",
            category = InstrumentCategory.WORLD_PERCUSSION,
            waveform = SynthWaveform.NOISE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Gourd beads, maracas, and organic shakers for syncopated groove layers",
            colorHex = 0xFF76FF03
        ),
        InstrumentPreset(
            id = "afro_percussion_layer",
            name = "Afro Percussion Layer",
            category = InstrumentCategory.WORLD_PERCUSSION,
            waveform = SynthWaveform.TRIANGLE,
            defaultTrackType = TrackType.DRUM_MACHINE,
            description = "Organic syncopated percussion kit featuring agogo bells, woodblocks, and udu",
            colorHex = 0xFFFFAB00
        ),

        // =========================================================================
        // 9. FX & TEXTURES (Risers, Textures, Drones, Impacts)
        // =========================================================================
        InstrumentPreset(
            id = "cyber_texture",
            name = "Cyber Cinematic Texture",
            category = InstrumentCategory.ATMOSPHERE_FX,
            waveform = SynthWaveform.NOISE,
            cutoffHz = 1800f,
            resonance = 2.0f,
            attackMs = 300f,
            decayMs = 500f,
            sustainLevel = 0.8f,
            releaseMs = 800f,
            defaultTrackType = TrackType.RISER_TEXTURE,
            description = "Granular drone texture with slow moving frequency modulation",
            colorHex = 0xFF00E5FF
        ),
        InstrumentPreset(
            id = "noise_riser",
            name = "White Noise Riser",
            category = InstrumentCategory.ATMOSPHERE_FX,
            waveform = SynthWaveform.NOISE,
            cutoffHz = 3500f,
            resonance = 1.8f,
            attackMs = 400f,
            sustainLevel = 0.95f,
            releaseMs = 200f,
            defaultTrackType = TrackType.RISER_TEXTURE,
            description = "Rising white noise tension builder for energetic drop transitions",
            colorHex = 0xFFE040FB
        ),
        InstrumentPreset(
            id = "sub_drop_impact",
            name = "Sub Drop Impact",
            category = InstrumentCategory.ATMOSPHERE_FX,
            waveform = SynthWaveform.SINE,
            cutoffHz = 1200f,
            resonance = 1.5f,
            attackMs = 2f,
            decayMs = 600f,
            sustainLevel = 0.1f,
            releaseMs = 600f,
            defaultTrackType = TrackType.RISER_TEXTURE,
            description = "Massive cinematic downward pitch drop impact for song section starts",
            colorHex = 0xFFFF1744
        )
    )
}
