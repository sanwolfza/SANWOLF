package com.sanwolf.daw.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sanwolf.daw.audio.ProjectManager
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Shareable, editable project files: `.sanwolf` = a zip with
 * ```
 * sanwolf.json    small manifest (format version, app, export time)
 * project.json    the project (same Gson model as internal saves)
 * audio/<name>    every audio file the project's tracks reference
 * ```
 * Inside the bundle, track audio URIs are rewritten to `sanwolf-audio://<name>`; on import they
 * are extracted into the new project's own audio folder and pointed there again.
 *
 * Everything here is blocking: call from a background thread.
 */
object ProjectBundle {
    private const val TAG = "ProjectBundle"
    const val EXTENSION = "sanwolf"
    const val MIME = "application/zip"
    private const val AUDIO_SCHEME = "sanwolf-audio://"
    private const val FORMAT_VERSION = 1
    /** Refuse absurd bundles (zip bombs). */
    private const val MAX_TOTAL_BYTES = 1_500_000_000L

    private val gson = GsonBuilder().create()

    /** Writes [projectJson] and its audio into cacheDir/exports/<title>.sanwolf. */
    fun export(context: Context, projectJson: String): File {
        val obj = JsonParser.parseString(projectJson).asJsonObject
        val title = obj.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: "SANWOLF project"
        val outDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val outFile = File(outDir, safeFileName(title) + "." + EXTENSION)

        // Collect audio (dedupe by source URI) and rewrite the references
        val sources = LinkedHashMap<String, String>() // uri -> entry name
        val usedNames = HashSet<String>()
        ProjectManager.rewriteAudioUris(obj) { uri ->
            if (uri.startsWith(AUDIO_SCHEME)) return@rewriteAudioUris uri
            sources[uri]?.let { return@rewriteAudioUris AUDIO_SCHEME + it }
            if (!canOpen(context, uri)) {
                Log.w(TAG, "Audio not readable, left as-is: $uri")
                return@rewriteAudioUris uri
            }
            val base = safeFileName(Uri.parse(uri).lastPathSegment?.substringAfterLast('/') ?: "audio")
            var name = base
            var n = 2
            while (!usedNames.add(name)) {
                name = base.substringBeforeLast('.') + "_" + n + (if (base.contains('.')) "." + base.substringAfterLast('.') else "")
                n++
            }
            sources[uri] = name
            AUDIO_SCHEME + name
        }

        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            val manifest = JsonObject().apply {
                addProperty("format", FORMAT_VERSION)
                addProperty("app", "SANWOLF DAW")
                addProperty("exportedAt", System.currentTimeMillis())
                addProperty("title", title)
            }
            zip.putNextEntry(ZipEntry("sanwolf.json"))
            zip.write(gson.toJson(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ProjectManager.PROJECT_JSON))
            zip.write(gson.toJson(obj).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for ((uri, name) in sources) {
                runCatching {
                    open(context, uri)?.use { input ->
                        zip.putNextEntry(ZipEntry("audio/$name"))
                        input.copyTo(zip)
                        zip.closeEntry()
                    }
                }.onFailure { Log.w(TAG, "Couldn't add $uri to bundle", it) }
            }
        }
        return outFile
    }

    /**
     * Imports a `.sanwolf` from [uri] as a new, editable project. Returns the new file id.
     * Throws with a readable message when the file isn't a SANWOLF project.
     */
    fun importBundle(context: Context, pm: ProjectManager, uri: Uri): String {
        val newId = pm.newFileId()
        val audioDir = pm.audioDir(newId)
        var projectText: String? = null
        val extracted = HashMap<String, File>()
        var total = 0L
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Couldn't open the file")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/')
                    when {
                        name == ProjectManager.PROJECT_JSON -> {
                            val bytes = zip.readBytes()
                            total += bytes.size
                            projectText = String(bytes, Charsets.UTF_8)
                        }
                        name.startsWith("audio/") -> {
                            // Only the base name is used (no paths from the archive: zip-slip safe)
                            val base = safeFileName(name.substringAfterLast('/'))
                            if (base.isBlank()) continue
                            val dest = File(audioDir, base)
                            dest.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = zip.read(buf)
                                    if (r < 0) break
                                    total += r
                                    if (total > MAX_TOTAL_BYTES) throw IllegalArgumentException("Project file is too large")
                                    out.write(buf, 0, r)
                                }
                            }
                            extracted[name.substringAfterLast('/')] = dest
                            extracted[base] = dest
                        }
                        else -> Unit
                    }
                    if (total > MAX_TOTAL_BYTES) throw IllegalArgumentException("Project file is too large")
                }
            }
            val text = projectText ?: throw IllegalArgumentException("Not a SANWOLF project (project.json missing)")
            val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                ?: throw IllegalArgumentException("Not a SANWOLF project (unreadable project.json)")
            if (obj.get("tracks")?.isJsonArray != true) throw IllegalArgumentException("Not a SANWOLF project (no tracks)")
            obj.addProperty("id", UUID.randomUUID().toString())
            obj.addProperty("lastSavedTimestamp", System.currentTimeMillis())
            obj.addProperty("isCloudSynced", false)
            ProjectManager.rewriteAudioUris(obj) { u ->
                if (!u.startsWith(AUDIO_SCHEME)) return@rewriteAudioUris u
                val f = extracted[u.removePrefix(AUDIO_SCHEME)]
                if (f != null) Uri.fromFile(f).toString() else u
            }
            return pm.importJson(newId, obj).getOrThrow()
        } catch (t: Throwable) {
            runCatching { pm.projectDir(newId).deleteRecursively() }
            throw t
        }
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Copies a built bundle to a document the user picked (SAF CreateDocument). */
    fun copyTo(context: Context, file: File, target: Uri) {
        val out = context.contentResolver.openOutputStream(target) ?: throw IllegalStateException("Couldn't write to the chosen location")
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    private fun canOpen(context: Context, uri: String): Boolean = runCatching {
        open(context, uri)?.use { true } ?: false
    }.getOrDefault(false)

    private fun open(context: Context, uri: String): InputStream? {
        val parsed = Uri.parse(uri)
        return when (parsed.scheme) {
            "file" -> parsed.path?.let { p -> File(p).takeIf { it.isFile }?.inputStream() }
            "content" -> context.contentResolver.openInputStream(parsed)
            null -> File(uri).takeIf { it.isFile }?.inputStream()
            else -> null
        }
    }

    fun safeFileName(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_").replace(' ', '_').take(80).ifBlank { "SANWOLF" }
}
