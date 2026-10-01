package com.example.ai

data class AiMusicRequest(
    val prompt: String,
    val genre: String,
    val bpm: Int = 120
)
