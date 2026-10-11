package com.sanwolf.daw.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sanwolf.daw.firebase.FirebaseDawManager
import com.sanwolf.daw.model.ProjectData
import com.sanwolf.daw.model.normalizeTracks
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
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
 * Project state & persistence manager: saves whole projects in app-internal storage so they
 * survive app restarts without a cloud account.
 *
 * Layout (one folder per project):
 * ```
 * filesDir/projects/<fileId>/project.json      the project (Gson, the whole ProjectData model)
 * filesDir/projects/<fileId>/project.json.bak  the previous good write (one backup)
 * filesDir/projects/<fileId>/audio/             audio the project owns (imported from a .sanwolf,
 *                                               or copied from a content:// URI on save)
 * ```
 * Older builds wrote flat files (filesDir/projects/<fileId>.json); they are migrated into a folder
 * the first time they are listed or opened.
 *
 * Writes are atomic: the JSON goes to a temp file which is fsynced, the current file becomes the
 * backup, and the temp file is renamed into place. [load] falls back to the backup when the main
 * file is missing or unreadable.
 *
 * The file id is deliberately separate from [ProjectData.id] (which is immutable), so "Save As"
 * can fork the working project into a new file. Serialization is generic over [ProjectData], so
 * fields added to the model later (e.g. effect racks) are saved without changes here.
 *
 * File I/O here is blocking: call it from a background thread.
 */
class ProjectManager(
    context: Context,
    @Suppress("unused") private val firebaseManager: FirebaseDawManager? = null
) {
    private val appContext: Context = context.applicationContext
    private val gson: Gson = GsonBuilder().create()
    private val projectsDir: File = File(appContext.filesDir, "projects")

    private fun dir(): File = projectsDir.apply { if (!exists()) mkdirs() }

    /** The folder that holds one project. */
    fun projectDir(fileId: String): File = File(dir(), sanitize(fileId))

    /** The project's own audio folder (created on demand). */
    fun audioDir(fileId: String): File = File(projectDir(fileId), AUDIO_DIR).apply { if (!exists()) mkdirs() }

    private fun mainFile(fileId: String): File = File(projectDir(fileId), PROJECT_JSON)
    private fun backupFile(fileId: String): File = File(projectDir(fileId), PROJECT_JSON + ".bak")
    private fun legacyFile(fileId: String): File = File(dir(), "${sanitize(fileId)}.json")

    fun newFileId(): String = UUID.randomUUID().toString()

    /** Serializes a project. Call on the thread that owns the project (the UI thread). */
    fun toJson(project: ProjectData): String = gson.toJson(project)

    fun fromJson(json: String): ProjectData =
        (gson.fromJson(json, ProjectData::class.java) ?: throw IllegalStateException("Project file is empty"))
            .normalizeTracks()

    /**
     * Writes already-serialized JSON for [fileId]. Audio referenced through content:// URIs is
     * copied into the project's audio folder first and the stored JSON points at the copy, so the
     * project still opens after the picker's temporary permission is gone. Blocking.
     */
    fun writeJson(fileId: String, json: String): Result<Unit> = runCatching {
        synchronized(WRITE_LOCK) { writeJsonLocked(fileId, json) }
    }

    private fun writeJsonLocked(fileId: String, json: String) {
        migrateLegacy(fileId)
        val folder = projectDir(fileId).apply { if (!exists()) mkdirs() }
        val toWrite = runCatching { localizeContentAudio(fileId, json) }
            .onFailure { Log.w(TAG, "Couldn't copy audio into project $fileId", it) }
            .getOrDefault(json)
        atomicWrite(folder, toWrite)
    }

    private fun atomicWrite(folder: File, text: String) {
        val target = File(folder, PROJECT_JSON)
        val backup = File(folder, PROJECT_JSON + ".bak")
        val tmp = File(folder, PROJECT_JSON + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            runCatching { out.fd.sync() }
        }
        if (target.exists()) {
            if (backup.exists()) backup.delete()
            if (!target.renameTo(backup)) {
                // Keep going: worst case there is no backup of this one write.
                Log.w(TAG, "Couldn't rotate backup in ${folder.name}")
                target.delete()
            }
        }
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    /** Loads a saved project, falling back to the backup copy. Blocking. */
    fun load(fileId: String): Result<ProjectData> = runCatching {
        migrateLegacy(fileId)
        val main = mainFile(fileId)
        val primary = if (main.exists()) runCatching { fromJson(main.readText()) } else Result.failure<ProjectData>(IllegalStateException("Project file missing"))
        primary.getOrElse { err ->
            val bak = backupFile(fileId)
            if (!bak.exists()) throw err
            Log.w(TAG, "Project $fileId unreadable, opening the backup", err)
            fromJson(bak.readText())
        }
    }

    /** Raw stored JSON (main file, else backup). Blocking. */
    fun readJson(fileId: String): String? {
        migrateLegacy(fileId)
        val main = mainFile(fileId)
        if (main.exists()) {
            val text = runCatching { main.readText() }.getOrNull()
            if (text != null && runCatching { JsonParser.parseString(text).asJsonObject }.isSuccess) return text
        }
        val bak = backupFile(fileId)
        return if (bak.exists()) runCatching { bak.readText() }.getOrNull() else null
    }

    fun exists(fileId: String): Boolean = mainFile(fileId).exists() || backupFile(fileId).exists() || legacyFile(fileId).exists()

    /** Lists saved projects, newest first. Unreadable entries are skipped. Blocking. */
    fun listProjects(): List<LocalProjectSummary> {
        // Migrate flat files from older builds first
        dir().listFiles { f: File -> f.isFile && f.name.endsWith(".json") }?.forEach { f ->
            migrateLegacy(f.name.removeSuffix(".json"))
        }
        val folders = dir().listFiles { f: File -> f.isDirectory } ?: return emptyList()
        return folders.mapNotNull { folder ->
            runCatching {
                val fileId = folder.name
                val file = File(folder, PROJECT_JSON).takeIf { it.exists() } ?: File(folder, PROJECT_JSON + ".bak")
                val obj = JsonParser.parseString(file.readText()).asJsonObject
                val title = obj.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: "Untitled"
                val bpm = obj.get("bpm")?.takeIf { it.isJsonPrimitive }?.asInt ?: 120
                val tracks = obj.get("tracks")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
                LocalProjectSummary(
                    fileId = fileId,
                    title = title,
                    bpm = bpm,
                    trackCount = tracks,
                    lastModified = file.lastModified()
                )
            }.getOrNull()
        }.sortedByDescending { it.lastModified }
    }

    /** "Untitled N" with N one past the highest existing "Untitled <number>". Blocking. */
    fun nextUntitledName(): String {
        val re = Regex("^Untitled (\\d+)$")
        val max = listProjects().mapNotNull { re.find(it.title.trim())?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "Untitled ${max + 1}"
    }

    /** Changes the stored title of a saved project. Blocking. */
    fun rename(fileId: String, newTitle: String): Result<Unit> = runCatching {
        val text = readJson(fileId) ?: throw IllegalStateException("Project not found")
        val obj = JsonParser.parseString(text).asJsonObject
        obj.addProperty("title", newTitle)
        synchronized(WRITE_LOCK) { atomicWrite(projectDir(fileId), gson.toJson(obj)) }
    }

    /**
     * Copies a saved project (JSON and its own audio folder) into a new file with [newTitle].
     * Returns the new file id. Blocking.
     */
    fun duplicate(fileId: String, newTitle: String): Result<String> = runCatching {
        val text = readJson(fileId) ?: throw IllegalStateException("Project not found")
        val newId = newFileId()
        val oldAudio = File(projectDir(fileId), AUDIO_DIR)
        val newAudio = audioDir(newId)
        if (oldAudio.isDirectory) oldAudio.copyRecursively(newAudio, overwrite = true)
        val obj = JsonParser.parseString(text).asJsonObject
        obj.addProperty("title", newTitle)
        obj.addProperty("lastSavedTimestamp", System.currentTimeMillis())
        rewriteAudioUris(obj) { uri ->
            val path = runCatching { Uri.parse(uri).path }.getOrNull()
            if (uri.startsWith("file:") && path != null && path.startsWith(oldAudio.absolutePath)) {
                Uri.fromFile(File(newAudio, File(path).name)).toString()
            } else uri
        }
        synchronized(WRITE_LOCK) { atomicWrite(projectDir(newId), gson.toJson(obj)) }
        newId
    }

    /** Deletes a saved project (its folder, audio included). Blocking. */
    fun delete(fileId: String): Result<Unit> = runCatching {
        val folder = projectDir(fileId)
        val legacy = legacyFile(fileId)
        if (legacy.exists()) legacy.delete()
        val ok = synchronized(WRITE_LOCK) { !folder.exists() || folder.deleteRecursively() }
        if (!ok) throw IllegalStateException("Couldn't delete project files")
    }

    /** Stores an imported project as a new entry. Returns the new file id. Blocking. */
    fun importJson(newId: String, obj: JsonObject): Result<String> = runCatching {
        synchronized(WRITE_LOCK) { atomicWrite(projectDir(newId).apply { if (!exists()) mkdirs() }, gson.toJson(obj)) }
        newId
    }

    private fun migrateLegacy(fileId: String) {
        val legacy = legacyFile(fileId)
        if (!legacy.exists()) return
        runCatching {
            val folder = projectDir(fileId).apply { if (!exists()) mkdirs() }
            val target = File(folder, PROJECT_JSON)
            if (!target.exists()) {
                if (!legacy.renameTo(target)) {
                    legacy.copyTo(target, overwrite = true)
                    legacy.delete()
                }
                target.setLastModified(System.currentTimeMillis())
            } else {
                legacy.delete()
            }
        }.onFailure { Log.w(TAG, "Couldn't migrate legacy project $fileId", it) }
    }

    /** Copies content:// audio into the project's audio folder; returns JSON pointing at the copies. */
    private fun localizeContentAudio(fileId: String, json: String): String {
        if (!json.contains("content:")) return json
        val obj = JsonParser.parseString(json).asJsonObject
        var changed = false
        rewriteAudioUris(obj) { uri ->
            if (!uri.startsWith("content:")) return@rewriteAudioUris uri
            val name = "c_" + Integer.toHexString(uri.hashCode()) + "_" +
                (Uri.parse(uri).lastPathSegment?.substringAfterLast('/')?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.takeLast(48) ?: "audio")
            val dest = File(audioDir(fileId), name)
            if (!dest.exists() || dest.length() == 0L) {
                val ok = runCatching {
                    appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    } != null
                }.getOrDefault(false)
                if (!ok) { dest.delete(); return@rewriteAudioUris uri }
            }
            changed = true
            Uri.fromFile(dest).toString()
        }
        return if (changed) gson.toJson(obj) else json
    }

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        private const val TAG = "ProjectManager"
        const val PROJECT_JSON = "project.json"
        const val AUDIO_DIR = "audio"

        /** One lock for every writer (several ProjectManager instances share the folder). */
        private val WRITE_LOCK = Any()

        /**
         * Applies [transform] to every track's `audioUri` in a serialized project tree. Kept
         * generic (JSON level) so new model fields never need changes here.
         */
        fun rewriteAudioUris(obj: JsonObject, transform: (String) -> String) {
            val tracks = obj.get("tracks")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
            for (el in tracks) {
                if (!el.isJsonObject) continue
                val t = el.asJsonObject
                val u = t.get("audioUri")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                if (u.isBlank()) continue
                val nu = transform(u)
                if (nu != u) t.addProperty("audioUri", nu)
            }
        }
    }
}
