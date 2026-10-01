package com.example.state

import com.example.model.ProjectData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StudioProjectManager {
    private val _state = MutableStateFlow(StudioProjectState())
    val state: StateFlow<StudioProjectState> = _state.asStateFlow()

    fun updateProject(project: ProjectData) {
        _state.value = _state.value.copy(currentProject = project)
    }

    fun setPlaying(playing: Boolean) {
        _state.value = _state.value.copy(isPlaying = playing)
    }

    fun setSelectedTrackIndex(index: Int) {
        _state.value = _state.value.copy(selectedTrackIndex = index)
    }
}
