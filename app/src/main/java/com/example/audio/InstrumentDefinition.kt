package com.example.audio

import com.example.model.SynthWaveform

/**
 * Authoritative Instrument Definition in SANWOLF Audio Studio.
 * Serves as the single source of truth across all engines, tracks, mixers, and renderers.
 */
data class InstrumentDefinition(
    val id: String,
    val name: String,
    val category: InstrumentCategory,
    val subCategory: String = "",
    val defaultWaveform: SynthWaveform = SynthWaveform.SAWTOOTH,
    val attackMs: Float = 15f,
    val decayMs: Float = 120f,
    val sustainLevel: Float = 0.65f,
    val releaseMs: Float = 180f,
    val filterCutoffHz: Float = 2500f,
    val filterResonance: Float = 1.2f,
    val isDrum: Boolean = false,
    val drumType: String = "",
    val voiceType: String = "",
    val sample: InstrumentSample? = null,
    val isUserImported: Boolean = false
)
