package com.example.mixer

/**
 * Authoritative MixerAudioTrack for SANWOLF Audio Studio.
 * Represents an audio track feeding into a MixerChannel.
 */
data class MixerAudioTrack(
    val id: String,
    var name: String,
    var instrumentId: String = "",
    var category: String = "",
    var sampleSource: String = "",
    var volume: Float = 0.8f,
    var gain: Float = 1.0f,
    var pan: Float = 0f,
    var muted: Boolean = false,
    var solo: Boolean = false,
    var pcmData: ShortArray? = null
)
