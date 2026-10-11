package com.sanwolf.daw.melody

data class MelodyProjectData(
    val title: String = "Untitled Melody",
    val notes: MutableList<MelodyNote> = mutableListOf()
)
