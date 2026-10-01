package com.example.mixer

data class MixerProjectData(
    val title: String = "Mixer Session",
    val channels: MutableList<MixerChannel> = mutableListOf()
)
