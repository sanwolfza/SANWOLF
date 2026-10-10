package com.sanwolf.daw.audio

import android.content.Context
import com.sanwolf.daw.model.ProjectData
import java.io.File

class AudioExporter(private val context: Context) {
    fun exportProject(project: ProjectData): File {
        return WavExporter.exportProjectToWav(context, project)
    }
}
