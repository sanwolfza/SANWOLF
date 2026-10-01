package com.example.audio

data class AudioEditRange(
    val startSample: Int,
    val endSample: Int,
    val fadeInSamples: Int = 0,
    val fadeOutSamples: Int = 0
)
