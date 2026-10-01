package com.example.audio

import android.content.Context
import com.example.model.ProjectData
import java.io.File

class AudioExporter(private val context: Context) {
    fun exportProject(project: ProjectData): File {
        return WavExporter.exportProjectToWav(context, project)
    }
}
