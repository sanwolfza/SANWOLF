package com.example.mixer

data class MixerSession(
    val sessionId: String,
    val projectName: String,
    val tracks: List<MixerAudioTrack>
)
