package com.sanwolf.daw.studio

import android.content.Context
import com.sanwolf.daw.audio.AudioExporter
import com.sanwolf.daw.model.ProjectData
import java.io.File

class MasterExportManager(private val context: Context) {
    private val exporter = AudioExporter(context)

    fun exportMaster(project: ProjectData, file: File) {
        exporter.exportProject(project)
    }
}
