package com.sanwolf.daw.melody

data class MelodyRenderResult(
    val pcmBuffer: ShortArray,
    val sampleRate: Int
)
