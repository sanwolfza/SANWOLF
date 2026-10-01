package com.example.melody

data class MelodyProjectData(
    val title: String = "Untitled Melody",
    val notes: MutableList<MelodyNote> = mutableListOf()
)
