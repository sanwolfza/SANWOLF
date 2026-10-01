package com.example.ai

import com.example.model.ProjectData

data class AiMusicResult(
    val success: Boolean,
    val generatedProject: ProjectData?,
    val error: String? = null
)
