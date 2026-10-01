package com.example.model

data class Preset(
    val id: String,
    val name: String,
    val genre: String,
    val style: String,
    val synthPresetName: String,
    val synthWaveform: String, // Store as String to match SynthWaveform.name
    val filterCutoffHz: Float,
    val filterResonance: Float,
    val attackMs: Float,
    val decayMs: Float,
    val sustainLevel: Float,
    val releaseMs: Float
)
