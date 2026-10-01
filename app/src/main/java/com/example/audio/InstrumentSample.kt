package com.example.audio

/**
 * Authoritative Instrument Sample representation for SANWOLF Audio Studio.
 * Supports bundled samples, synthesized procedural audio samples, and user-imported WAV samples.
 */
data class InstrumentSample(
    val sampleId: String = "",
    val name: String = "",
    val rootPitch: Int = 60,
    val samplePath: String = "",
    val volume: Float = 1.0f,
    val isAvailable: Boolean = true,
    val isSynthesized: Boolean = false,
    val pcmData: ShortArray? = null,
    val sampleRate: Int = 44100,
    val durationSec: Float = 0.5f
)
