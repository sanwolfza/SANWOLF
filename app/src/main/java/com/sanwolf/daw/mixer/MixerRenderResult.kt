package com.sanwolf.daw.mixer

data class MixerRenderResult(
    val masterPcm: ShortArray,
    val sampleRate: Int
)
