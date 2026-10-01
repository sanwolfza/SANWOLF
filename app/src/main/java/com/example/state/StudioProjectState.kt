package com.example.state

import com.example.model.ProjectData

data class StudioProjectState(
    val currentProject: ProjectData = ProjectData(),
    val isPlaying: Boolean = false,
    val selectedTrackIndex: Int = 0
)
