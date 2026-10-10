package com.sanwolf.daw.piano

data class PianoRenderResult(
    val pcmBuffer: ShortArray,
    val sampleRate: Int
)
