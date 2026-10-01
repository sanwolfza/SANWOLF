package com.example.mixer

/**
 * Authoritative MixerChannel for SANWOLF Audio Studio.
 * Models an individual channel strip on the mixer console with full routing parameters.
 */
data class MixerChannel(
    val channelId: String,
    var instrumentId: String = "",
    var trackName: String = "",
    var category: String = "",
    var sampleSource: String = "",
    var volume: Float = 0.8f,
    var gain: Float = 1.0f,
    var pan: Float = 0f,
    var muted: Boolean = false,
    var solo: Boolean = false,
    var effects: MutableList<String> = mutableListOf()
)
