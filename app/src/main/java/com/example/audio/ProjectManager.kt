package com.example.audio

import android.content.Context
import com.example.firebase.FirebaseDawManager
import com.example.model.ProjectData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.File
import java.util.UUID

/** Lightweight row for the "Open / Recent projects" list. */
data class LocalProjectSummary(
    val fileId: String,
    val title: String,
    val bpm: Int,
    val trackCount: Int,
    val lastModified: Long
)

/**
 * Project state & persistence manager: saves whole projects as JSON files on the device
 * (filesDir/projects/<fileId>.json) so they survive app restarts without a cloud account.
 *
 * The file id is deliberately separate from [ProjectData.id] (which is immutable), so
 * "Save As" can fork the working project into a new file. Serialization uses Gson, the
 * same format the undo/redo history already uses; [ProjectData] has defaults for every
 * field, so files written by older versions load with sensible defaults for new fields.
 *
 * File I/O here is blocking: call it from a background dispatcher.
 */
class ProjectManager(
    context: Context,
    @Suppress("unused") private val firebaseManager: FirebaseDawManager? = null
) {
    private val gson: Gson = GsonBuilder().create()
    private val projectsDir: File = File(context.applicationContext.filesDir, "projects")

    private fun dir(): File = projectsDir.apply { if (!exists()) mkdirs() }

    private fun fileFor(fileId: String): File = File(dir(), "${sanitize(fileId)}.json")

    fun newFileId(): String = UUID.randomUUID().toString()

    /** Serializes a project. Call on the thread that owns the project (the UI thread). */
    fun toJson(project: ProjectData): String = gson.toJson(project)

    /** Writes already-serialized JSON for [fileId]. Blocking. */
    fun writeJson(fileId: String, json: String): Result<Unit> = runCatching {
        val target = fileFor(fileId)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(json)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            target.writeText(json)
            tmp.delete()
        }
    }

    /** Loads a saved project. Blocking. */
    fun load(fileId: String): Result<ProjectData> = runCatching {
        val text = fileFor(fileId).readText()
        gson.fromJson(text, ProjectData::class.java)
            ?: throw IllegalStateException("Project file is empty")
    }

    /** Lists saved projects, newest first. Unreadable files are skipped. Blocking. */
    fun listProjects(): List<LocalProjectSummary> {
        val files = dir().listFiles { f: File -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { f ->
            runCatching {
                val obj = JsonParser.parseString(f.readText()).asJsonObject
                val title = obj.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: "Untitled"
                val bpm = obj.get("bpm")?.takeIf { it.isJsonPrimitive }?.asInt ?: 120
                val tracks = obj.get("tracks")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
                LocalProjectSummary(
                    fileId = f.name.removeSuffix(".json"),
                    title = title,
                    bpm = bpm,
                    trackCount = tracks,
                    lastModified = f.lastModified()
                )
            }.getOrNull()
        }.sortedByDescending { it.lastModified }
    }

    /** Changes the stored title of a saved project. Blocking. */
    fun rename(fileId: String, newTitle: String): Result<Unit> = runCatching {
        val file = fileFor(fileId)
        val obj = JsonParser.parseString(file.readText()).asJsonObject
        obj.addProperty("title", newTitle)
        writeJson(fileId, gson.toJson(obj)).getOrThrow()
    }

    /** Copies a saved project into a new file with [newTitle]. Returns the new file id. Blocking. */
    fun duplicate(fileId: String, newTitle: String): Result<String> = runCatching {
        val obj = JsonParser.parseString(fileFor(fileId).readText()).asJsonObject
        obj.addProperty("title", newTitle)
        obj.addProperty("lastSavedTimestamp", System.currentTimeMillis())
        val newId = newFileId()
        writeJson(newId, gson.toJson(obj)).getOrThrow()
        newId
    }

    /** Deletes a saved project file. Blocking. */
    fun delete(fileId: String): Result<Unit> = runCatching {
        val file = fileFor(fileId)
        if (file.exists() && !file.delete()) throw IllegalStateException("Couldn't delete project file")
    }

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
