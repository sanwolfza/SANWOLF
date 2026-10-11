package com.sanwolf.daw.mixer

data class MixerSession(
    val sessionId: String,
    val projectName: String,
    val tracks: List<MixerAudioTrack>
)
