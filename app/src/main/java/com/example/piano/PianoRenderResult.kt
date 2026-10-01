package com.example.piano

data class PianoRenderResult(
    val pcmBuffer: ShortArray,
    val sampleRate: Int
)
