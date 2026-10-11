package com.sanwolf.daw.audio

data class AudioTrackInfo(
    val id: String,
    val name: String,
    val durationMs: Long,
    val sampleRate: Int
)
