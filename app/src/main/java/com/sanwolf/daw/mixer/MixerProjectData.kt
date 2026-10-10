package com.sanwolf.daw.mixer

data class MixerProjectData(
    val title: String = "Mixer Session",
    val channels: MutableList<MixerChannel> = mutableListOf()
)
