package com.sanwolf.daw.state

import com.sanwolf.daw.model.ProjectData

data class StudioProjectState(
    val currentProject: ProjectData = ProjectData(),
    val isPlaying: Boolean = false,
    val selectedTrackIndex: Int = 0
)
