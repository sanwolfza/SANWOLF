package com.sanwolf.daw.ai

import com.sanwolf.daw.model.ProjectData

data class AiMusicResult(
    val success: Boolean,
    val generatedProject: ProjectData?,
    val error: String? = null
)
