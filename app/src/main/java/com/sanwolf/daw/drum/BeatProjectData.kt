package com.sanwolf.daw.drum

data class BeatProjectData(
    val title: String = "Untitled Beat",
    val bpm: Int = 120,
    val steps: BooleanArray = BooleanArray(32)
)
