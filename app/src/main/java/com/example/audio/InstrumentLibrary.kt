package com.example.audio

import com.example.model.SynthWaveform

/**
 * Authoritative, single source of truth for all instrument definitions in the SANWOLF Audio Studio.
 * Every instrument has a stable ID, unique voice synthesis characteristics, and clear taxonomic classification.
 */
object InstrumentLibrary {
    private val mutableDefinitions = mutableListOf(
        // =========================================================================
        // 1. DRUMS - Kicks
        // =========================================================================
        InstrumentDefinition(
            id = "drum.kick.deep",
            name = "Deep Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_deep",
            voiceType = "kick_deep"
        ),
        InstrumentDefinition(
            id = "drum.kick.sub",
            name = "Sub Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_sub",
            voiceType = "kick_sub"
        ),
        InstrumentDefinition(
            id = "drum.kick.punch",
            name = "Punch Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_punch",
            voiceType = "kick_punch"
        ),
        InstrumentDefinition(
            id = "drum.kick.club",
            name = "Club Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_club",
            voiceType = "kick_club"
        ),
        InstrumentDefinition(
            id = "drum.kick.house",
            name = "House Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_house",
            voiceType = "kick_house"
        ),
        InstrumentDefinition(
            id = "drum.kick.tech",
            name = "Tech House Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_tech",
            voiceType = "kick_tech"
        ),
        InstrumentDefinition(
            id = "drum.kick.afro",
            name = "Afro House Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_afro",
            voiceType = "kick_afro"
        ),
        InstrumentDefinition(
            id = "drum.kick.tribal",
            name = "Tribal Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_tribal",
            voiceType = "kick_tribal"
        ),
        InstrumentDefinition(
            id = "drum.kick.electronic",
            name = "Electronic Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_electronic",
            voiceType = "kick_electronic"
        ),
        InstrumentDefinition(
            id = "drum.kick.acoustic",
            name = "Acoustic Kick",
            category = InstrumentCategory.DRUMS,
            subCategory = "Kicks",
            isDrum = true,
            drumType = "kick_acoustic",
            voiceType = "kick_acoustic"
        ),

        // =========================================================================
        // DRUMS - Snares
        // =========================================================================
        InstrumentDefinition(
            id = "drum.snare.tight",
            name = "Tight Snare",
            category = InstrumentCategory.DRUMS,
            subCategory = "Snares",
            isDrum = true,
            drumType = "snare_tight",
            voiceType = "snare_tight"
        ),
        InstrumentDefinition(
            id = "drum.snare.deep",
            name = "Deep Snare",
            category = InstrumentCategory.DRUMS,
            subCategory = "Snares",
            isDrum = true,
            drumType = "snare_deep",
            voiceType = "snare_deep"
        ),
        InstrumentDefinition(
            id = "drum.snare.acoustic",
            name = "Acoustic Snare",
            category = InstrumentCategory.DRUMS,
            subCategory = "Snares",
            isDrum = true,
            drumType = "snare_acoustic",
            voiceType = "snare_acoustic"
        ),
        InstrumentDefinition(
            id = "drum.snare.electronic",
            name = "Electronic Snare",
            category = InstrumentCategory.DRUMS,
            subCategory = "Snares",
            isDrum = true,
            drumType = "snare_electronic",
            voiceType = "snare_electronic"
        ),
        InstrumentDefinition(
            id = "drum.snare.rim",
            name = "Rim Snare",
            category = InstrumentCategory.DRUMS,
            subCategory = "Snares",
            isDrum = true,
            drumType = "snare_rim",
            voiceType = "snare_rim"
        ),
        InstrumentDefinition(
            id = "drum.snare.clap",
            name = "Clap Snare",
            category = InstrumentCategory.DRUMS,
            subCategory = "Snares",
            isDrum = true,
            drumType = "snare_clap",
            voiceType = "snare_clap"
        ),

        // =========================================================================
        // DRUMS - Claps
        // =========================================================================
        InstrumentDefinition(
            id = "drum.clap.clean",
            name = "Clean Clap",
            category = InstrumentCategory.DRUMS,
            subCategory = "Claps",
            isDrum = true,
            drumType = "clap_clean",
            voiceType = "clap_clean"
        ),
        InstrumentDefinition(
            id = "drum.clap.wide",
            name = "Wide Clap",
            category = InstrumentCategory.DRUMS,
            subCategory = "Claps",
            isDrum = true,
            drumType = "clap_wide",
            voiceType = "clap_wide"
        ),
        InstrumentDefinition(
            id = "drum.clap.layered",
            name = "Layered Clap",
            category = InstrumentCategory.DRUMS,
            subCategory = "Claps",
            isDrum = true,
            drumType = "clap_layered",
            voiceType = "clap_layered"
        ),
        InstrumentDefinition(
            id = "drum.clap.room",
            name = "Room Clap",
            category = InstrumentCategory.DRUMS,
            subCategory = "Claps",
            isDrum = true,
            drumType = "clap_room",
            voiceType = "clap_room"
        ),
        InstrumentDefinition(
            id = "drum.clap.electronic",
            name = "Electronic Clap",
            category = InstrumentCategory.DRUMS,
            subCategory = "Claps",
            isDrum = true,
            drumType = "clap_electronic",
            voiceType = "clap_electronic"
        ),
        InstrumentDefinition(
            id = "drum.clap.afro",
            name = "Afro Clap",
            category = InstrumentCategory.DRUMS,
            subCategory = "Claps",
            isDrum = true,
            drumType = "clap_afro",
            voiceType = "clap_afro"
        ),

        // =========================================================================
        // DRUMS - Hi-Hats
        // =========================================================================
        InstrumentDefinition(
            id = "drum.hihat.closed",
            name = "Closed Hi-Hat",
            category = InstrumentCategory.DRUMS,
            subCategory = "Hi-Hats",
            isDrum = true,
            drumType = "hihat_closed",
            voiceType = "hihat_closed"
        ),
        InstrumentDefinition(
            id = "drum.hihat.open",
            name = "Open Hi-Hat",
            category = InstrumentCategory.DRUMS,
            subCategory = "Hi-Hats",
            isDrum = true,
            drumType = "hihat_open",
            voiceType = "hihat_open"
        ),
        InstrumentDefinition(
            id = "drum.hihat.tight",
            name = "Tight Hi-Hat",
            category = InstrumentCategory.DRUMS,
            subCategory = "Hi-Hats",
            isDrum = true,
            drumType = "hihat_tight",
            voiceType = "hihat_tight"
        ),
        InstrumentDefinition(
            id = "drum.hihat.bright",
            name = "Bright Hi-Hat",
            category = InstrumentCategory.DRUMS,
            subCategory = "Hi-Hats",
            isDrum = true,
            drumType = "hihat_bright",
            voiceType = "hihat_bright"
        ),
        InstrumentDefinition(
            id = "drum.hihat.dark",
            name = "Dark Hi-Hat",
            category = InstrumentCategory.DRUMS,
            subCategory = "Hi-Hats",
            isDrum = true,
            drumType = "hihat_dark",
            voiceType = "hihat_dark"
        ),
        InstrumentDefinition(
            id = "drum.hihat.electronic",
            name = "Electronic Hi-Hat",
            category = InstrumentCategory.DRUMS,
            subCategory = "Hi-Hats",
            isDrum = true,
            drumType = "hihat_electronic",
            voiceType = "hihat_electronic"
        ),

        // =========================================================================
        // DRUMS - Toms
        // =========================================================================
        InstrumentDefinition(
            id = "drum.tom.low",
            name = "Low Tom",
            category = InstrumentCategory.DRUMS,
            subCategory = "Toms",
            isDrum = true,
            drumType = "tom_low",
            voiceType = "tom_low"
        ),
        InstrumentDefinition(
            id = "drum.tom.mid",
            name = "Mid Tom",
            category = InstrumentCategory.DRUMS,
            subCategory = "Toms",
            isDrum = true,
            drumType = "tom_mid",
            voiceType = "tom_mid"
        ),
        InstrumentDefinition(
            id = "drum.tom.high",
            name = "High Tom",
            category = InstrumentCategory.DRUMS,
            subCategory = "Toms",
            isDrum = true,
            drumType = "tom_high",
            voiceType = "tom_high"
        ),
        InstrumentDefinition(
            id = "drum.tom.electronic",
            name = "Electronic Tom",
            category = InstrumentCategory.DRUMS,
            subCategory = "Toms",
            isDrum = true,
            drumType = "tom_electronic",
            voiceType = "tom_electronic"
        ),
        InstrumentDefinition(
            id = "drum.tom.tribal",
            name = "Tribal Tom",
            category = InstrumentCategory.DRUMS,
            subCategory = "Toms",
            isDrum = true,
            drumType = "tom_tribal",
            voiceType = "tom_tribal"
        ),

        // =========================================================================
        // DRUMS - Cymbals
        // =========================================================================
        InstrumentDefinition(
            id = "drum.cymbal.crash",
            name = "Crash",
            category = InstrumentCategory.DRUMS,
            subCategory = "Cymbals",
            isDrum = true,
            drumType = "crash",
            voiceType = "crash"
        ),
        InstrumentDefinition(
            id = "drum.cymbal.ride",
            name = "Ride",
            category = InstrumentCategory.DRUMS,
            subCategory = "Cymbals",
            isDrum = true,
            drumType = "ride",
            voiceType = "ride"
        ),
        InstrumentDefinition(
            id = "drum.cymbal.splash",
            name = "Splash",
            category = InstrumentCategory.DRUMS,
            subCategory = "Cymbals",
            isDrum = true,
            drumType = "splash",
            voiceType = "splash"
        ),
        InstrumentDefinition(
            id = "drum.cymbal.china",
            name = "China",
            category = InstrumentCategory.DRUMS,
            subCategory = "Cymbals",
            isDrum = true,
            drumType = "china",
            voiceType = "china"
        ),

        // =========================================================================
        // DRUMS - Percussion (Afro / World / Latin)
        // =========================================================================
        InstrumentDefinition(
            id = "drum.perc.shaker",
            name = "Shaker",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "shaker",
            voiceType = "shaker"
        ),
        InstrumentDefinition(
            id = "drum.perc.tambourine",
            name = "Tambourine",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "tambourine",
            voiceType = "tambourine"
        ),
        InstrumentDefinition(
            id = "drum.perc.conga",
            name = "Conga",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "conga",
            voiceType = "conga"
        ),
        InstrumentDefinition(
            id = "drum.perc.conga.low",
            name = "Low Conga",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "conga_low",
            voiceType = "conga_low"
        ),
        InstrumentDefinition(
            id = "drum.perc.conga.high",
            name = "High Conga",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "conga_high",
            voiceType = "conga_high"
        ),
        InstrumentDefinition(
            id = "drum.perc.bongo",
            name = "Bongo",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "bongo",
            voiceType = "bongo"
        ),
        InstrumentDefinition(
            id = "drum.perc.bongo.low",
            name = "Low Bongo",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "bongo_low",
            voiceType = "bongo_low"
        ),
        InstrumentDefinition(
            id = "drum.perc.bongo.high",
            name = "High Bongo",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "bongo_high",
            voiceType = "bongo_high"
        ),
        InstrumentDefinition(
            id = "drum.perc.djembe",
            name = "Djembe",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "djembe",
            voiceType = "djembe"
        ),
        InstrumentDefinition(
            id = "drum.perc.darbuka",
            name = "Darbuka",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "darbuka",
            voiceType = "darbuka"
        ),
        InstrumentDefinition(
            id = "drum.perc.tabla",
            name = "Tabla",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "tabla",
            voiceType = "tabla"
        ),
        InstrumentDefinition(
            id = "drum.perc.agogo",
            name = "Agogo",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "agogo",
            voiceType = "agogo"
        ),
        InstrumentDefinition(
            id = "drum.perc.cowbell",
            name = "Cowbell",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "cowbell",
            voiceType = "cowbell"
        ),
        InstrumentDefinition(
            id = "drum.perc.claves",
            name = "Claves",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "claves",
            voiceType = "claves"
        ),
        InstrumentDefinition(
            id = "drum.perc.woodblock",
            name = "Woodblock",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "woodblock",
            voiceType = "woodblock"
        ),
        InstrumentDefinition(
            id = "drum.perc.guiro",
            name = "Guiro",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "guiro",
            voiceType = "guiro"
        ),
        InstrumentDefinition(
            id = "drum.perc.cabasa",
            name = "Cabasa",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "cabasa",
            voiceType = "cabasa"
        ),
        InstrumentDefinition(
            id = "drum.perc.maracas",
            name = "Maracas",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "maracas",
            voiceType = "maracas"
        ),
        InstrumentDefinition(
            id = "drum.perc.frame_drum",
            name = "Frame Drum",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "frame_drum",
            voiceType = "frame_drum"
        ),
        InstrumentDefinition(
            id = "drum.perc.tribal",
            name = "Tribal Percussion",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "tribal_perc",
            voiceType = "tribal_perc"
        ),
        InstrumentDefinition(
            id = "drum.perc.afro",
            name = "Afro Percussion",
            category = InstrumentCategory.DRUMS,
            subCategory = "Percussion",
            isDrum = true,
            drumType = "afro_perc",
            voiceType = "afro_perc"
        ),

        // =========================================================================
        // 2. BASS
        // =========================================================================
        InstrumentDefinition(
            id = "bass.sub",
            name = "Sub Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 15f,
            decayMs = 300f,
            sustainLevel = 0.9f,
            releaseMs = 280f,
            filterCutoffHz = 650f,
            filterResonance = 1.0f,
            voiceType = "sub_bass"
        ),
        InstrumentDefinition(
            id = "bass.deep",
            name = "Deep Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 12f,
            decayMs = 240f,
            sustainLevel = 0.85f,
            releaseMs = 220f,
            filterCutoffHz = 900f,
            filterResonance = 1.3f,
            voiceType = "deep_bass"
        ),
        InstrumentDefinition(
            id = "bass.electric",
            name = "Electric Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 8f,
            decayMs = 200f,
            sustainLevel = 0.65f,
            releaseMs = 150f,
            filterCutoffHz = 2200f,
            filterResonance = 1.2f,
            voiceType = "electric_bass"
        ),
        InstrumentDefinition(
            id = "bass.analog",
            name = "Analog Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 6f,
            decayMs = 140f,
            sustainLevel = 0.55f,
            releaseMs = 140f,
            filterCutoffHz = 2400f,
            filterResonance = 3.5f,
            voiceType = "analog_bass"
        ),
        InstrumentDefinition(
            id = "bass.synth",
            name = "Synth Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 5f,
            decayMs = 160f,
            sustainLevel = 0.7f,
            releaseMs = 110f,
            filterCutoffHz = 3200f,
            filterResonance = 2.2f,
            voiceType = "synth_bass"
        ),
        InstrumentDefinition(
            id = "bass.reese",
            name = "Reese Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 35f,
            decayMs = 450f,
            sustainLevel = 0.9f,
            releaseMs = 320f,
            filterCutoffHz = 1600f,
            filterResonance = 2.8f,
            voiceType = "reese_bass"
        ),
        InstrumentDefinition(
            id = "bass.pluck",
            name = "Pluck Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 2f,
            decayMs = 90f,
            sustainLevel = 0.2f,
            releaseMs = 90f,
            filterCutoffHz = 3800f,
            filterResonance = 2.0f,
            voiceType = "pluck_bass"
        ),
        InstrumentDefinition(
            id = "bass.house",
            name = "House Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 8f,
            decayMs = 110f,
            sustainLevel = 0.65f,
            releaseMs = 110f,
            filterCutoffHz = 2800f,
            filterResonance = 3.0f,
            voiceType = "house_bass"
        ),
        InstrumentDefinition(
            id = "bass.afro_house",
            name = "Afro House Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 12f,
            decayMs = 220f,
            sustainLevel = 0.8f,
            releaseMs = 180f,
            filterCutoffHz = 1400f,
            filterResonance = 1.8f,
            voiceType = "afro_house_bass"
        ),
        InstrumentDefinition(
            id = "bass.tech",
            name = "Tech Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 4f,
            decayMs = 95f,
            sustainLevel = 0.45f,
            releaseMs = 95f,
            filterCutoffHz = 3400f,
            filterResonance = 3.8f,
            voiceType = "tech_bass"
        ),
        InstrumentDefinition(
            id = "bass.808",
            name = "808 Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 20f,
            decayMs = 500f,
            sustainLevel = 0.95f,
            releaseMs = 400f,
            filterCutoffHz = 750f,
            filterResonance = 1.1f,
            voiceType = "808_bass"
        ),
        InstrumentDefinition(
            id = "bass.moog",
            name = "Moog Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 10f,
            decayMs = 170f,
            sustainLevel = 0.7f,
            releaseMs = 150f,
            filterCutoffHz = 2100f,
            filterResonance = 4.2f,
            voiceType = "moog_bass"
        ),
        InstrumentDefinition(
            id = "bass.fm",
            name = "FM Bass",
            category = InstrumentCategory.BASS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 3f,
            decayMs = 120f,
            sustainLevel = 0.35f,
            releaseMs = 110f,
            filterCutoffHz = 4200f,
            filterResonance = 1.4f,
            voiceType = "fm_bass"
        ),

        // =========================================================================
        // 3. SYNTHS
        // =========================================================================
        InstrumentDefinition(
            id = "synth.analog.lead",
            name = "Analog Lead",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 12f,
            decayMs = 160f,
            sustainLevel = 0.72f,
            releaseMs = 210f,
            filterCutoffHz = 3600f,
            filterResonance = 2.4f,
            voiceType = "analog_lead"
        ),
        InstrumentDefinition(
            id = "synth.digital.lead",
            name = "Digital Lead",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 4f,
            decayMs = 110f,
            sustainLevel = 0.8f,
            releaseMs = 130f,
            filterCutoffHz = 5200f,
            filterResonance = 1.6f,
            voiceType = "digital_lead"
        ),
        InstrumentDefinition(
            id = "synth.soft.lead",
            name = "Soft Lead",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 45f,
            decayMs = 240f,
            sustainLevel = 0.75f,
            releaseMs = 260f,
            filterCutoffHz = 2300f,
            filterResonance = 1.1f,
            voiceType = "soft_lead"
        ),
        InstrumentDefinition(
            id = "synth.bright.lead",
            name = "Bright Lead",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 6f,
            decayMs = 130f,
            sustainLevel = 0.82f,
            releaseMs = 160f,
            filterCutoffHz = 6500f,
            filterResonance = 3.2f,
            voiceType = "bright_lead"
        ),
        InstrumentDefinition(
            id = "synth.acid.lead",
            name = "Acid Lead",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 3f,
            decayMs = 85f,
            sustainLevel = 0.32f,
            releaseMs = 95f,
            filterCutoffHz = 4200f,
            filterResonance = 5.2f,
            voiceType = "acid_lead"
        ),
        InstrumentDefinition(
            id = "synth.pluck",
            name = "Synth Pluck",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 3f,
            decayMs = 85f,
            sustainLevel = 0.22f,
            releaseMs = 110f,
            filterCutoffHz = 4800f,
            filterResonance = 2.1f,
            voiceType = "synth_pluck"
        ),
        InstrumentDefinition(
            id = "synth.bell",
            name = "Synth Bell",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 2f,
            decayMs = 650f,
            sustainLevel = 0.12f,
            releaseMs = 650f,
            filterCutoffHz = 7200f,
            filterResonance = 1.0f,
            voiceType = "bell"
        ),
        InstrumentDefinition(
            id = "synth.keys",
            name = "Synth Keys",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 8f,
            decayMs = 190f,
            sustainLevel = 0.55f,
            releaseMs = 220f,
            filterCutoffHz = 4600f,
            filterResonance = 1.8f,
            voiceType = "synth_keys"
        ),
        InstrumentDefinition(
            id = "synth.analog.keys",
            name = "Analog Keys",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 10f,
            decayMs = 210f,
            sustainLevel = 0.6f,
            releaseMs = 240f,
            filterCutoffHz = 4100f,
            filterResonance = 2.0f,
            voiceType = "analog_keys"
        ),
        InstrumentDefinition(
            id = "synth.digital.keys",
            name = "Digital Keys",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 5f,
            decayMs = 180f,
            sustainLevel = 0.58f,
            releaseMs = 200f,
            filterCutoffHz = 5400f,
            filterResonance = 1.3f,
            voiceType = "digital_keys"
        ),
        InstrumentDefinition(
            id = "synth.fm.keys",
            name = "FM Keys",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 4f,
            decayMs = 220f,
            sustainLevel = 0.45f,
            releaseMs = 250f,
            filterCutoffHz = 6000f,
            filterResonance = 1.5f,
            voiceType = "fm_keys"
        ),
        InstrumentDefinition(
            id = "synth.brass",
            name = "Synth Brass",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 25f,
            decayMs = 150f,
            sustainLevel = 0.78f,
            releaseMs = 180f,
            filterCutoffHz = 4500f,
            filterResonance = 2.6f,
            voiceType = "synth_brass"
        ),
        InstrumentDefinition(
            id = "synth.strings",
            name = "Synth Strings",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 90f,
            decayMs = 260f,
            sustainLevel = 0.84f,
            releaseMs = 380f,
            filterCutoffHz = 3500f,
            filterResonance = 1.5f,
            voiceType = "synth_strings"
        ),
        InstrumentDefinition(
            id = "synth.arp",
            name = "Arp Synth",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 3f,
            decayMs = 70f,
            sustainLevel = 0.35f,
            releaseMs = 80f,
            filterCutoffHz = 5000f,
            filterResonance = 3.0f,
            voiceType = "arp_synth"
        ),
        InstrumentDefinition(
            id = "synth.sequence",
            name = "Sequence Synth",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 4f,
            decayMs = 80f,
            sustainLevel = 0.4f,
            releaseMs = 90f,
            filterCutoffHz = 4600f,
            filterResonance = 2.5f,
            voiceType = "sequence_synth"
        ),
        InstrumentDefinition(
            id = "synth.mono",
            name = "Mono Synth",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 5f,
            decayMs = 120f,
            sustainLevel = 0.7f,
            releaseMs = 140f,
            filterCutoffHz = 3800f,
            filterResonance = 2.8f,
            voiceType = "mono_synth"
        ),
        InstrumentDefinition(
            id = "synth.poly",
            name = "Poly Synth",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 15f,
            decayMs = 200f,
            sustainLevel = 0.65f,
            releaseMs = 240f,
            filterCutoffHz = 4200f,
            filterResonance = 1.8f,
            voiceType = "poly_synth"
        ),
        InstrumentDefinition(
            id = "synth.retro",
            name = "Retro Synth",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 8f,
            decayMs = 140f,
            sustainLevel = 0.6f,
            releaseMs = 160f,
            filterCutoffHz = 3600f,
            filterResonance = 2.0f,
            voiceType = "retro_synth"
        ),
        InstrumentDefinition(
            id = "synth.house",
            name = "House Synth",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 6f,
            decayMs = 130f,
            sustainLevel = 0.68f,
            releaseMs = 150f,
            filterCutoffHz = 4800f,
            filterResonance = 2.5f,
            voiceType = "house_synth"
        ),
        InstrumentDefinition(
            id = "synth.techno",
            name = "Techno Synth",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 3f,
            decayMs = 90f,
            sustainLevel = 0.45f,
            releaseMs = 100f,
            filterCutoffHz = 5500f,
            filterResonance = 3.8f,
            voiceType = "techno_synth"
        ),
        InstrumentDefinition(
            id = "synth.afro_house",
            name = "Afro House Synth",
            category = InstrumentCategory.SYNTHS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 14f,
            decayMs = 200f,
            sustainLevel = 0.72f,
            releaseMs = 220f,
            filterCutoffHz = 3200f,
            filterResonance = 1.9f,
            voiceType = "afro_house_synth"
        ),

        // =========================================================================
        // 4. PADS
        // =========================================================================
        InstrumentDefinition(
            id = "pad.warm",
            name = "Warm Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 220f,
            decayMs = 400f,
            sustainLevel = 0.82f,
            releaseMs = 600f,
            filterCutoffHz = 1900f,
            filterResonance = 1.1f,
            voiceType = "warm_pad"
        ),
        InstrumentDefinition(
            id = "pad.soft",
            name = "Soft Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 280f,
            decayMs = 450f,
            sustainLevel = 0.8f,
            releaseMs = 700f,
            filterCutoffHz = 1500f,
            filterResonance = 1.0f,
            voiceType = "soft_pad"
        ),
        InstrumentDefinition(
            id = "pad.dark",
            name = "Dark Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 300f,
            decayMs = 550f,
            sustainLevel = 0.75f,
            releaseMs = 750f,
            filterCutoffHz = 850f,
            filterResonance = 1.2f,
            voiceType = "dark_pad"
        ),
        InstrumentDefinition(
            id = "pad.deep",
            name = "Deep Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 260f,
            decayMs = 500f,
            sustainLevel = 0.85f,
            releaseMs = 720f,
            filterCutoffHz = 1100f,
            filterResonance = 1.3f,
            voiceType = "deep_pad"
        ),
        InstrumentDefinition(
            id = "pad.analog",
            name = "Analog Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 200f,
            decayMs = 380f,
            sustainLevel = 0.8f,
            releaseMs = 580f,
            filterCutoffHz = 2400f,
            filterResonance = 1.7f,
            voiceType = "analog_pad"
        ),
        InstrumentDefinition(
            id = "pad.digital",
            name = "Digital Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 180f,
            decayMs = 360f,
            sustainLevel = 0.82f,
            releaseMs = 550f,
            filterCutoffHz = 3200f,
            filterResonance = 1.2f,
            voiceType = "digital_pad"
        ),
        InstrumentDefinition(
            id = "pad.air",
            name = "Air Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 250f,
            decayMs = 450f,
            sustainLevel = 0.78f,
            releaseMs = 680f,
            filterCutoffHz = 2800f,
            filterResonance = 1.0f,
            voiceType = "air_pad"
        ),
        InstrumentDefinition(
            id = "pad.ambient",
            name = "Ambient Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 320f,
            decayMs = 520f,
            sustainLevel = 0.8f,
            releaseMs = 820f,
            filterCutoffHz = 1700f,
            filterResonance = 1.2f,
            voiceType = "ambient_pad"
        ),
        InstrumentDefinition(
            id = "pad.atmospheric",
            name = "Atmospheric Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 350f,
            decayMs = 600f,
            sustainLevel = 0.85f,
            releaseMs = 900f,
            filterCutoffHz = 2100f,
            filterResonance = 1.3f,
            voiceType = "atmospheric_pad"
        ),
        InstrumentDefinition(
            id = "pad.cinematic",
            name = "Cinematic Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 300f,
            decayMs = 550f,
            sustainLevel = 0.88f,
            releaseMs = 850f,
            filterCutoffHz = 2300f,
            filterResonance = 1.5f,
            voiceType = "cinematic_pad"
        ),
        InstrumentDefinition(
            id = "pad.choir",
            name = "Choir Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 260f,
            decayMs = 480f,
            sustainLevel = 0.86f,
            releaseMs = 740f,
            filterCutoffHz = 2200f,
            filterResonance = 2.0f,
            voiceType = "choir_pad"
        ),
        InstrumentDefinition(
            id = "pad.string",
            name = "String Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 210f,
            decayMs = 400f,
            sustainLevel = 0.86f,
            releaseMs = 620f,
            filterCutoffHz = 2800f,
            filterResonance = 1.4f,
            voiceType = "string_pad"
        ),
        InstrumentDefinition(
            id = "pad.synth",
            name = "Synth Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 170f,
            decayMs = 350f,
            sustainLevel = 0.8f,
            releaseMs = 540f,
            filterCutoffHz = 3100f,
            filterResonance = 1.6f,
            voiceType = "synth_pad"
        ),
        InstrumentDefinition(
            id = "pad.evolving",
            name = "Evolving Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 320f,
            decayMs = 650f,
            sustainLevel = 0.85f,
            releaseMs = 850f,
            filterCutoffHz = 2200f,
            filterResonance = 2.2f,
            voiceType = "evolving_pad"
        ),
        InstrumentDefinition(
            id = "pad.pulsing",
            name = "Pulsing Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 190f,
            decayMs = 420f,
            sustainLevel = 0.8f,
            releaseMs = 620f,
            filterCutoffHz = 2600f,
            filterResonance = 1.8f,
            voiceType = "pulsing_pad"
        ),
        InstrumentDefinition(
            id = "pad.wide",
            name = "Wide Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 240f,
            decayMs = 460f,
            sustainLevel = 0.84f,
            releaseMs = 700f,
            filterCutoffHz = 2700f,
            filterResonance = 1.3f,
            voiceType = "wide_pad"
        ),
        InstrumentDefinition(
            id = "pad.dream",
            name = "Dream Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 300f,
            decayMs = 520f,
            sustainLevel = 0.82f,
            releaseMs = 780f,
            filterCutoffHz = 2000f,
            filterResonance = 1.1f,
            voiceType = "dream_pad"
        ),
        InstrumentDefinition(
            id = "pad.space",
            name = "Space Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 340f,
            decayMs = 580f,
            sustainLevel = 0.83f,
            releaseMs = 860f,
            filterCutoffHz = 1800f,
            filterResonance = 1.5f,
            voiceType = "space_pad"
        ),
        InstrumentDefinition(
            id = "pad.texture",
            name = "Texture Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 280f,
            decayMs = 500f,
            sustainLevel = 0.8f,
            releaseMs = 720f,
            filterCutoffHz = 2300f,
            filterResonance = 1.4f,
            voiceType = "texture_pad"
        ),
        InstrumentDefinition(
            id = "pad.organic",
            name = "Organic Pad",
            category = InstrumentCategory.PADS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 260f,
            decayMs = 460f,
            sustainLevel = 0.82f,
            releaseMs = 680f,
            filterCutoffHz = 2100f,
            filterResonance = 1.2f,
            voiceType = "organic_pad"
        ),

        // =========================================================================
        // 5. KEYS / PIANO
        // =========================================================================
        InstrumentDefinition(
            id = "keys.grand.piano",
            name = "Grand Piano",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 4f,
            decayMs = 320f,
            sustainLevel = 0.38f,
            releaseMs = 420f,
            filterCutoffHz = 6200f,
            filterResonance = 1.0f,
            voiceType = "grand_piano"
        ),
        InstrumentDefinition(
            id = "keys.upright.piano",
            name = "Upright Piano",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 6f,
            decayMs = 260f,
            sustainLevel = 0.42f,
            releaseMs = 360f,
            filterCutoffHz = 5200f,
            filterResonance = 1.1f,
            voiceType = "upright_piano"
        ),
        InstrumentDefinition(
            id = "keys.electric.piano",
            name = "Electric Piano",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 9f,
            decayMs = 380f,
            sustainLevel = 0.5f,
            releaseMs = 480f,
            filterCutoffHz = 4200f,
            filterResonance = 1.1f,
            voiceType = "electric_piano"
        ),
        InstrumentDefinition(
            id = "keys.rhodes",
            name = "Rhodes",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 10f,
            decayMs = 420f,
            sustainLevel = 0.55f,
            releaseMs = 520f,
            filterCutoffHz = 3900f,
            filterResonance = 1.2f,
            voiceType = "rhodes"
        ),
        InstrumentDefinition(
            id = "keys.wurlitzer",
            name = "Wurlitzer",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 7f,
            decayMs = 340f,
            sustainLevel = 0.5f,
            releaseMs = 400f,
            filterCutoffHz = 4400f,
            filterResonance = 1.3f,
            voiceType = "wurlitzer"
        ),
        InstrumentDefinition(
            id = "keys.soft.piano",
            name = "Soft Piano",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 18f,
            decayMs = 380f,
            sustainLevel = 0.35f,
            releaseMs = 480f,
            filterCutoffHz = 3200f,
            filterResonance = 1.0f,
            voiceType = "soft_piano"
        ),
        InstrumentDefinition(
            id = "keys.bright.piano",
            name = "Bright Piano",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 3f,
            decayMs = 260f,
            sustainLevel = 0.44f,
            releaseMs = 320f,
            filterCutoffHz = 7200f,
            filterResonance = 1.2f,
            voiceType = "bright_piano"
        ),
        InstrumentDefinition(
            id = "keys.dark.piano",
            name = "Dark Piano",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 14f,
            decayMs = 360f,
            sustainLevel = 0.34f,
            releaseMs = 440f,
            filterCutoffHz = 2600f,
            filterResonance = 1.0f,
            voiceType = "dark_piano"
        ),
        InstrumentDefinition(
            id = "keys.house.piano",
            name = "House Piano",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 4f,
            decayMs = 210f,
            sustainLevel = 0.58f,
            releaseMs = 260f,
            filterCutoffHz = 5800f,
            filterResonance = 1.5f,
            voiceType = "house_piano"
        ),
        InstrumentDefinition(
            id = "keys.gospel.piano",
            name = "Gospel Piano",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 5f,
            decayMs = 310f,
            sustainLevel = 0.48f,
            releaseMs = 390f,
            filterCutoffHz = 6400f,
            filterResonance = 1.2f,
            voiceType = "gospel_piano"
        ),
        InstrumentDefinition(
            id = "keys.synth.piano",
            name = "Synth Piano",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 5f,
            decayMs = 190f,
            sustainLevel = 0.5f,
            releaseMs = 230f,
            filterCutoffHz = 4900f,
            filterResonance = 1.4f,
            voiceType = "synth_piano"
        ),
        InstrumentDefinition(
            id = "keys.organ",
            name = "Organ",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 3f,
            decayMs = 110f,
            sustainLevel = 0.88f,
            releaseMs = 60f,
            filterCutoffHz = 7800f,
            filterResonance = 1.1f,
            voiceType = "organ"
        ),
        InstrumentDefinition(
            id = "keys.hammond.organ",
            name = "Hammond Organ",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 3f,
            decayMs = 120f,
            sustainLevel = 0.85f,
            releaseMs = 65f,
            filterCutoffHz = 7400f,
            filterResonance = 1.2f,
            voiceType = "hammond_organ"
        ),
        InstrumentDefinition(
            id = "keys.electric.organ",
            name = "Electric Organ",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 4f,
            decayMs = 140f,
            sustainLevel = 0.82f,
            releaseMs = 80f,
            filterCutoffHz = 6600f,
            filterResonance = 1.3f,
            voiceType = "electric_organ"
        ),
        InstrumentDefinition(
            id = "keys.church.organ",
            name = "Church Organ",
            category = InstrumentCategory.KEYS_PIANO,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 15f,
            decayMs = 480f,
            sustainLevel = 0.94f,
            releaseMs = 320f,
            filterCutoffHz = 8800f,
            filterResonance = 1.1f,
            voiceType = "church_organ"
        ),

        // =========================================================================
        // 6. STRINGS
        // =========================================================================
        InstrumentDefinition(
            id = "strings.violin",
            name = "Violin",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 70f,
            decayMs = 200f,
            sustainLevel = 0.82f,
            releaseMs = 280f,
            filterCutoffHz = 4200f,
            filterResonance = 1.5f,
            voiceType = "violin"
        ),
        InstrumentDefinition(
            id = "strings.viola",
            name = "Viola",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 90f,
            decayMs = 220f,
            sustainLevel = 0.84f,
            releaseMs = 320f,
            filterCutoffHz = 3400f,
            filterResonance = 1.4f,
            voiceType = "viola"
        ),
        InstrumentDefinition(
            id = "strings.cello",
            name = "Cello",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 110f,
            decayMs = 250f,
            sustainLevel = 0.86f,
            releaseMs = 380f,
            filterCutoffHz = 2700f,
            filterResonance = 1.3f,
            voiceType = "cello"
        ),
        InstrumentDefinition(
            id = "strings.double_bass",
            name = "Double Bass",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 130f,
            decayMs = 280f,
            sustainLevel = 0.88f,
            releaseMs = 420f,
            filterCutoffHz = 1900f,
            filterResonance = 1.2f,
            voiceType = "double_bass"
        ),
        InstrumentDefinition(
            id = "strings.ensemble",
            name = "String Ensemble",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 160f,
            decayMs = 340f,
            sustainLevel = 0.9f,
            releaseMs = 480f,
            filterCutoffHz = 3300f,
            filterResonance = 1.4f,
            voiceType = "strings_ensemble"
        ),
        InstrumentDefinition(
            id = "strings.short",
            name = "Short Strings",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 12f,
            decayMs = 110f,
            sustainLevel = 0.2f,
            releaseMs = 120f,
            filterCutoffHz = 4000f,
            filterResonance = 1.8f,
            voiceType = "strings_short"
        ),
        InstrumentDefinition(
            id = "strings.long",
            name = "Long Strings",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 180f,
            decayMs = 400f,
            sustainLevel = 0.9f,
            releaseMs = 600f,
            filterCutoffHz = 3200f,
            filterResonance = 1.2f,
            voiceType = "strings_long"
        ),
        InstrumentDefinition(
            id = "strings.staccato",
            name = "Staccato Strings",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 6f,
            decayMs = 80f,
            sustainLevel = 0.1f,
            releaseMs = 90f,
            filterCutoffHz = 4500f,
            filterResonance = 2.0f,
            voiceType = "strings_staccato"
        ),
        InstrumentDefinition(
            id = "strings.plucked",
            name = "Plucked Strings",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 2f,
            decayMs = 150f,
            sustainLevel = 0.05f,
            releaseMs = 160f,
            filterCutoffHz = 4800f,
            filterResonance = 1.8f,
            voiceType = "strings_pizzicato"
        ),
        InstrumentDefinition(
            id = "strings.pad",
            name = "String Pad",
            category = InstrumentCategory.STRINGS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 220f,
            decayMs = 420f,
            sustainLevel = 0.88f,
            releaseMs = 640f,
            filterCutoffHz = 2800f,
            filterResonance = 1.3f,
            voiceType = "string_pad"
        ),

        // =========================================================================
        // 7. BRASS / WINDS
        // =========================================================================
        InstrumentDefinition(
            id = "brass.trumpet",
            name = "Trumpet",
            category = InstrumentCategory.BRASS_WINDS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 25f,
            decayMs = 130f,
            sustainLevel = 0.78f,
            releaseMs = 160f,
            filterCutoffHz = 5200f,
            filterResonance = 2.2f,
            voiceType = "trumpet"
        ),
        InstrumentDefinition(
            id = "brass.trombone",
            name = "Trombone",
            category = InstrumentCategory.BRASS_WINDS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 35f,
            decayMs = 150f,
            sustainLevel = 0.8f,
            releaseMs = 180f,
            filterCutoffHz = 3800f,
            filterResonance = 2.0f,
            voiceType = "trombone"
        ),
        InstrumentDefinition(
            id = "brass.french_horn",
            name = "French Horn",
            category = InstrumentCategory.BRASS_WINDS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 50f,
            decayMs = 180f,
            sustainLevel = 0.82f,
            releaseMs = 220f,
            filterCutoffHz = 3100f,
            filterResonance = 1.8f,
            voiceType = "french_horn"
        ),
        InstrumentDefinition(
            id = "brass.saxophone",
            name = "Saxophone",
            category = InstrumentCategory.BRASS_WINDS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 22f,
            decayMs = 140f,
            sustainLevel = 0.74f,
            releaseMs = 190f,
            filterCutoffHz = 4300f,
            filterResonance = 2.0f,
            voiceType = "saxophone"
        ),
        InstrumentDefinition(
            id = "brass.flute",
            name = "Flute",
            category = InstrumentCategory.BRASS_WINDS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 35f,
            decayMs = 140f,
            sustainLevel = 0.85f,
            releaseMs = 170f,
            filterCutoffHz = 5000f,
            filterResonance = 1.1f,
            voiceType = "flute"
        ),
        InstrumentDefinition(
            id = "brass.clarinet",
            name = "Clarinet",
            category = InstrumentCategory.BRASS_WINDS,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 28f,
            decayMs = 150f,
            sustainLevel = 0.8f,
            releaseMs = 180f,
            filterCutoffHz = 3600f,
            filterResonance = 1.4f,
            voiceType = "clarinet"
        ),
        InstrumentDefinition(
            id = "brass.oboe",
            name = "Oboe",
            category = InstrumentCategory.BRASS_WINDS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 20f,
            decayMs = 130f,
            sustainLevel = 0.76f,
            releaseMs = 160f,
            filterCutoffHz = 4600f,
            filterResonance = 2.5f,
            voiceType = "oboe"
        ),
        InstrumentDefinition(
            id = "brass.ensemble",
            name = "Brass Ensemble",
            category = InstrumentCategory.BRASS_WINDS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 45f,
            decayMs = 200f,
            sustainLevel = 0.85f,
            releaseMs = 250f,
            filterCutoffHz = 4400f,
            filterResonance = 2.2f,
            voiceType = "brass_ensemble"
        ),
        InstrumentDefinition(
            id = "brass.synth",
            name = "Synth Brass",
            category = InstrumentCategory.BRASS_WINDS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 25f,
            decayMs = 160f,
            sustainLevel = 0.78f,
            releaseMs = 180f,
            filterCutoffHz = 4800f,
            filterResonance = 2.8f,
            voiceType = "synth_brass"
        ),

        // =========================================================================
        // 8. PLUCKS / MALLETS
        // =========================================================================
        InstrumentDefinition(
            id = "pluck.acoustic",
            name = "Acoustic Pluck",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 2f,
            decayMs = 220f,
            sustainLevel = 0.15f,
            releaseMs = 220f,
            filterCutoffHz = 4500f,
            filterResonance = 1.3f,
            voiceType = "acoustic_pluck"
        ),
        InstrumentDefinition(
            id = "pluck.synth",
            name = "Synth Pluck",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.SQUARE,
            attackMs = 3f,
            decayMs = 90f,
            sustainLevel = 0.2f,
            releaseMs = 100f,
            filterCutoffHz = 5000f,
            filterResonance = 2.0f,
            voiceType = "synth_pluck"
        ),
        InstrumentDefinition(
            id = "pluck.guitar",
            name = "Guitar Pluck",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.SAWTOOTH,
            attackMs = 2f,
            decayMs = 260f,
            sustainLevel = 0.12f,
            releaseMs = 240f,
            filterCutoffHz = 4200f,
            filterResonance = 1.5f,
            voiceType = "guitar_pluck"
        ),
        InstrumentDefinition(
            id = "pluck.kalimba",
            name = "Kalimba",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 1f,
            decayMs = 380f,
            sustainLevel = 0.08f,
            releaseMs = 380f,
            filterCutoffHz = 5500f,
            filterResonance = 1.8f,
            voiceType = "kalimba"
        ),
        InstrumentDefinition(
            id = "pluck.marimba",
            name = "Marimba",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 2f,
            decayMs = 280f,
            sustainLevel = 0.05f,
            releaseMs = 280f,
            filterCutoffHz = 4000f,
            filterResonance = 2.2f,
            voiceType = "marimba"
        ),
        InstrumentDefinition(
            id = "pluck.xylophone",
            name = "Xylophone",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 1f,
            decayMs = 180f,
            sustainLevel = 0.04f,
            releaseMs = 180f,
            filterCutoffHz = 6500f,
            filterResonance = 2.5f,
            voiceType = "xylophone"
        ),
        InstrumentDefinition(
            id = "pluck.glockenspiel",
            name = "Glockenspiel",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 1f,
            decayMs = 500f,
            sustainLevel = 0.1f,
            releaseMs = 500f,
            filterCutoffHz = 8000f,
            filterResonance = 2.0f,
            voiceType = "glockenspiel"
        ),
        InstrumentDefinition(
            id = "pluck.vibraphone",
            name = "Vibraphone",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 3f,
            decayMs = 600f,
            sustainLevel = 0.2f,
            releaseMs = 600f,
            filterCutoffHz = 6000f,
            filterResonance = 1.6f,
            voiceType = "vibraphone"
        ),
        InstrumentDefinition(
            id = "pluck.music_box",
            name = "Music Box",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 1f,
            decayMs = 450f,
            sustainLevel = 0.08f,
            releaseMs = 450f,
            filterCutoffHz = 7500f,
            filterResonance = 1.8f,
            voiceType = "music_box"
        ),
        InstrumentDefinition(
            id = "pluck.bell",
            name = "Bell",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.SINE,
            attackMs = 1f,
            decayMs = 700f,
            sustainLevel = 0.12f,
            releaseMs = 700f,
            filterCutoffHz = 7500f,
            filterResonance = 1.5f,
            voiceType = "bell"
        ),
        InstrumentDefinition(
            id = "pluck.digital_bell",
            name = "Digital Bell",
            category = InstrumentCategory.PLUCKS_MALLETS,
            defaultWaveform = SynthWaveform.TRIANGLE,
            attackMs = 2f,
            decayMs = 650f,
            sustainLevel = 0.15f,
            releaseMs = 650f,
            filterCutoffHz = 8200f,
            filterResonance = 1.8f,
            voiceType = "digital_bell"
        ),

        // =========================================================================
        // 9. WORLD / AFRO PERCUSSION
        // =========================================================================
        InstrumentDefinition(
            id = "perc.djembe",
            name = "Djembe",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "djembe",
            voiceType = "djembe"
        ),
        InstrumentDefinition(
            id = "perc.conga",
            name = "Conga",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "conga",
            voiceType = "conga"
        ),
        InstrumentDefinition(
            id = "perc.bongo",
            name = "Bongo",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "bongo",
            voiceType = "bongo"
        ),
        InstrumentDefinition(
            id = "perc.talking_drum",
            name = "Talking Drum",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "talking_drum",
            voiceType = "talking_drum"
        ),
        InstrumentDefinition(
            id = "perc.darbuka",
            name = "Darbuka",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "darbuka",
            voiceType = "darbuka"
        ),
        InstrumentDefinition(
            id = "perc.tabla",
            name = "Tabla",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "tabla",
            voiceType = "tabla"
        ),
        InstrumentDefinition(
            id = "perc.shekere",
            name = "Shekere",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "shekere",
            voiceType = "shekere"
        ),
        InstrumentDefinition(
            id = "perc.udu",
            name = "Udu",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "udu",
            voiceType = "udu"
        ),
        InstrumentDefinition(
            id = "perc.agogo",
            name = "Agogo",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "agogo",
            voiceType = "agogo"
        ),
        InstrumentDefinition(
            id = "perc.cowbell",
            name = "Cowbell",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "cowbell",
            voiceType = "cowbell"
        ),
        InstrumentDefinition(
            id = "perc.claves",
            name = "Claves",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "claves",
            voiceType = "claves"
        ),
        InstrumentDefinition(
            id = "perc.cabasa",
            name = "Cabasa",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "cabasa",
            voiceType = "cabasa"
        ),
        InstrumentDefinition(
            id = "perc.maracas",
            name = "Maracas",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "maracas",
            voiceType = "maracas"
        ),
        InstrumentDefinition(
            id = "perc.shaker",
            name = "Shaker",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "shaker",
            voiceType = "shaker"
        ),
        InstrumentDefinition(
            id = "perc.tambourine",
            name = "Tambourine",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "tambourine",
            voiceType = "tambourine"
        ),
        InstrumentDefinition(
            id = "perc.woodblock",
            name = "Woodblock",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "woodblock",
            voiceType = "woodblock"
        ),
        InstrumentDefinition(
            id = "perc.guiro",
            name = "Guiro",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "guiro",
            voiceType = "guiro"
        ),
        InstrumentDefinition(
            id = "perc.frame_drum",
            name = "Frame Drum",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "frame_drum",
            voiceType = "frame_drum"
        ),
        InstrumentDefinition(
            id = "perc.tribal_drum",
            name = "Tribal Drum",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "tribal_drum",
            voiceType = "tribal_drum"
        ),
        InstrumentDefinition(
            id = "perc.afro",
            name = "Afro Percussion",
            category = InstrumentCategory.WORLD_PERCUSSION,
            isDrum = true,
            drumType = "afro_perc",
            voiceType = "afro_perc"
        ),

        // =========================================================================
        // 10. SOUND EFFECTS / TEXTURES
        // =========================================================================
        InstrumentDefinition(
            id = "fx.impact",
            name = "Impact",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "impact",
            voiceType = "impact"
        ),
        InstrumentDefinition(
            id = "fx.rise",
            name = "Rise",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "rise",
            voiceType = "rise"
        ),
        InstrumentDefinition(
            id = "fx.downlifter",
            name = "Downlifter",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "downlifter",
            voiceType = "downlifter"
        ),
        InstrumentDefinition(
            id = "fx.sweep",
            name = "Sweep",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "sweep",
            voiceType = "sweep"
        ),
        InstrumentDefinition(
            id = "fx.noise",
            name = "Noise",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "noise",
            voiceType = "noise"
        ),
        InstrumentDefinition(
            id = "fx.vinyl",
            name = "Vinyl Texture",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "vinyl",
            voiceType = "vinyl"
        ),
        InstrumentDefinition(
            id = "fx.atmosphere",
            name = "Atmosphere",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "atmosphere",
            voiceType = "atmosphere"
        ),
        InstrumentDefinition(
            id = "fx.transition",
            name = "Transition",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "transition",
            voiceType = "transition"
        ),
        InstrumentDefinition(
            id = "fx.reverse_cymbal",
            name = "Reverse Cymbal",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "reverse_cymbal",
            voiceType = "reverse_cymbal"
        ),
        InstrumentDefinition(
            id = "fx.whoosh",
            name = "Whoosh",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "whoosh",
            voiceType = "whoosh"
        ),
        InstrumentDefinition(
            id = "fx.glitch",
            name = "Glitch",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "glitch",
            voiceType = "glitch"
        ),
        InstrumentDefinition(
            id = "fx.drone",
            name = "Drone",
            category = InstrumentCategory.SOUND_EFFECTS_TEXTURES,
            isDrum = true,
            drumType = "drone",
            voiceType = "drone"
        )
    )

    val definitions: List<InstrumentDefinition>
        get() = mutableDefinitions

    // Resolved lookups (id/name/alias -> definition). getById is called from the real-time audio
    // thread for every sequencer hit; the fuzzy fallback below allocates and scans the whole
    // library, so results are memoised. Cleared whenever the library changes.
    private val resolveCache = java.util.concurrent.ConcurrentHashMap<String, InstrumentDefinition>()

    fun registerUserSample(definition: InstrumentDefinition) {
        synchronized(mutableDefinitions) {
            resolveCache.clear()
            val existingIdx = mutableDefinitions.indexOfFirst { it.id == definition.id }
            if (existingIdx >= 0) {
                mutableDefinitions[existingIdx] = definition
            } else {
                mutableDefinitions.add(definition)
            }
        }
    }

    fun getById(id: String): InstrumentDefinition {
        resolveCache[id]?.let { return it }
        // Resolve and memoise under the same lock registerUserSample uses, so a concurrent
        // registration can never leave a stale entry behind.
        synchronized(mutableDefinitions) {
            resolveCache[id]?.let { return it }
            val resolved = resolveById(id)
            resolveCache[id] = resolved
            return resolved
        }
    }

    private fun resolveById(id: String): InstrumentDefinition {
        synchronized(mutableDefinitions) {
            // 1. Direct match on exact ID (e.g. "drum.kick.deep")
            mutableDefinitions.find { it.id == id }?.let { return it }

            // 2. Case-insensitive exact ID match
            mutableDefinitions.find { it.id.equals(id, ignoreCase = true) }?.let { return it }

            // 3. Exact name match
            mutableDefinitions.find { it.name.equals(id, ignoreCase = true) }?.let { return it }

            // 4. Substring / alias match
            val lower = id.lowercase()
            mutableDefinitions.find {
                it.id.lowercase().contains(lower) || lower.contains(it.id.lowercase()) ||
                it.name.lowercase().contains(lower) || lower.contains(it.name.lowercase())
            }?.let { return it }

            // 5. Fallback - return Grand Piano if melodic or Deep Kick if drum
            return if (id.contains("drum", true) || id.contains("kick", true) || id.contains("snare", true)) {
                mutableDefinitions.firstOrNull { it.isDrum } ?: mutableDefinitions.first()
            } else {
                mutableDefinitions.firstOrNull { it.id == "keys.grand.piano" } ?: mutableDefinitions.first()
            }
        }
    }

    fun getByCategory(category: InstrumentCategory): List<InstrumentDefinition> {
        synchronized(mutableDefinitions) {
            return mutableDefinitions.filter { it.category == category }
        }
    }
}
