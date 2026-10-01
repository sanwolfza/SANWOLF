package com.example.melody

data class MelodyRenderResult(
    val pcmBuffer: ShortArray,
    val sampleRate: Int
)
