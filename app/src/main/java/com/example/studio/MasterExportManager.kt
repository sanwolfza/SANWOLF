package com.example.studio

import android.content.Context
import com.example.audio.AudioExporter
import com.example.model.ProjectData
import java.io.File

class MasterExportManager(private val context: Context) {
    private val exporter = AudioExporter(context)

    fun exportMaster(project: ProjectData, file: File) {
        exporter.exportProject(project)
    }
}
