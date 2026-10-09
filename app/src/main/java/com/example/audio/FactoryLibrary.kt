package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * One sound in the factory library (app/src/main/assets/factory/manifest.json).
 * [file] is relative to assets/factory, e.g. "house_afro/kick_01.wav".
 */
data class FactorySound(
    val id: String,
    val displayName: String,
    val category: String,
    val type: String,
    val genre: String,
    val file: String,
    val rootNote: String?,
    val rootMidi: Int?,
    val bpm: Int?,
    val durationMs: Int,
    val channels: Int,
    val bars: Int?
) {
    val assetPath: String get() = "${FactoryLibrary.ASSET_DIR}/$file"
    val isLoop: Boolean get() = category.equals("Loops", ignoreCase = true) || bpm != null
    /** Drum / percussion one-shots can also drive a step-sequencer (drum) track. */
    val isDrumOneShot: Boolean
        get() = !isLoop && (category.equals("Drums", true) || category.equals("Percussion", true))
    val genreLabel: String get() = FactoryGenre.labelFor(genre)
    val color: Long get() = FactoryLibrary.colorFor(category)

    /** Short detail line: BPM for loops, root note for tonal sounds, otherwise the sound type. */
    fun detail(includeGenre: Boolean = false): String {
        val secs = String.format(Locale.US, "%.1fs", durationMs / 1000.0)
        val main = when {
            isLoop -> listOfNotNull(bpm?.let { "$it BPM" }, bars?.let { "$it bars" }).joinToString(" • ").ifEmpty { secs }
            rootNote != null -> "Root $rootNote • $secs"
            else -> "${prettyType()} • $secs"
        }
        return if (includeGenre) "$genreLabel • $main" else main
    }

    fun prettyType(): String = type.split('_').joinToString(" ") { w ->
        when (w.lowercase()) {
            "808" -> "808"
            "fx" -> "FX"
            else -> w.replaceFirstChar { it.uppercaseChar() }
        }
    }
}

/** Genre folders in display order: House first, then the rest. */
enum class FactoryGenre(val key: String, val label: String) {
    AFRO_HOUSE("house_afro", "Afro House"),
    DEEP_HOUSE("house_deep", "Deep House"),
    TECH_HOUSE("house_tech", "Tech House"),
    AMAPIANO("amapiano", "Amapiano"),
    GQOM("gqom", "Gqom"),
    HIPHOP_TRAP("hiphop_trap", "Hip Hop / Trap"),
    RNB("rnb", "R&B"),
    POP("pop", "Pop"),
    ROCK_LIVE("rock_live", "Rock / Live"),
    JAZZ_LOFI("jazz_lofi", "Jazz / Lo-fi");

    companion object {
        fun labelFor(key: String): String = values().firstOrNull { it.key == key }?.label
            ?: key.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
    }
}

/**
 * The bundled factory sample library: manifest parsing, asset → file copies (so tracks keep a
 * stable file:// URI that survives restarts), and a single shared preview player.
 */
object FactoryLibrary {
    private const val TAG = "FactoryLibrary"
    const val ASSET_DIR = "factory"

    /** Category headings in display order. */
    val CATEGORY_ORDER = listOf("Drums", "Percussion", "Bass", "Keys", "Synths", "FX", "Loops")

    @Volatile
    private var cached: List<FactorySound>? = null

    /** True when the manifest could not be read (callers fall back to synth placeholders). */
    @Volatile
    var loadFailed: Boolean = false
        private set

    /** Parses assets/factory/manifest.json once (call off the main thread). Empty on failure. */
    fun sounds(context: Context): List<FactorySound> {
        cached?.let { return it }
        val result = runCatching {
            val text = context.assets.open("$ASSET_DIR/manifest.json").bufferedReader().use { it.readText() }
            parseManifest(text)
        }.onFailure { Log.e(TAG, "Factory manifest failed to load", it) }.getOrDefault(emptyList())
        loadFailed = result.isEmpty()
        if (result.isNotEmpty()) cached = result
        return result
    }

    fun parseManifest(text: String): List<FactorySound> {
        val trimmed = text.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed)
        else JSONObject(trimmed).let { it.optJSONArray("samples") ?: it.optJSONArray("items") ?: JSONArray() }
        val out = ArrayList<FactorySound>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val file = o.optString("file").takeIf { it.isNotBlank() } ?: continue
            out.add(
                FactorySound(
                    id = o.optString("id").ifBlank { file.removeSuffix(".wav").replace('/', '_') },
                    displayName = o.optString("displayName").ifBlank { file.substringAfterLast('/').removeSuffix(".wav") },
                    category = o.optString("category").ifBlank { "FX" },
                    type = o.optString("type"),
                    genre = o.optString("genre").ifBlank { file.substringBefore('/') },
                    file = file,
                    rootNote = o.nullableString("rootNote"),
                    rootMidi = o.nullableInt("rootMidi"),
                    bpm = o.nullableInt("bpm"),
                    durationMs = o.optInt("durationMs", 0),
                    channels = o.optInt("channels", 1),
                    bars = o.nullableInt("bars")
                )
            )
        }
        return out
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

    fun colorFor(category: String): Long = when (category.lowercase()) {
        "drums" -> 0xFFFF5722
        "percussion" -> 0xFFFFD700
        "bass" -> 0xFFFFAB00
        "keys" -> 0xFF00B0FF
        "synths" -> 0xFFFF007F
        "fx" -> 0xFFE040FB
        "loops" -> 0xFF76FF03
        else -> 0xFF00E5FF
    }

    /** Sort key so categories follow [CATEGORY_ORDER] (unknown ones last). */
    fun categoryRank(category: String): Int =
        CATEGORY_ORDER.indexOfFirst { it.equals(category, ignoreCase = true) }.let { if (it < 0) CATEGORY_ORDER.size else it }

    /**
     * Copies the sound's asset into app storage (filesDir/factory_samples/<id>.wav) and returns the
     * file, reusing an existing copy. Null if the asset can't be read. Call off the main thread.
     */
    fun materialize(context: Context, sound: FactorySound): File? = runCatching {
        val dir = File(context.filesDir, "factory_samples").apply { if (!exists()) mkdirs() }
        val safeId = sound.id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val target = File(dir, "$safeId.wav")
        if (target.exists() && target.length() > 44) return@runCatching target
        val tmp = File(dir, "$safeId.wav.tmp")
        context.assets.open(sound.assetPath).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        target
    }.onFailure { Log.e(TAG, "Couldn't copy ${sound.assetPath}", it) }.getOrNull()

    /**
     * Synth stand-in (from the old placeholder catalogue) used only if a sound's WAV can't be loaded.
     * Returns a name the synth drum engine understands.
     */
    fun placeholderSynthName(sound: FactorySound): String {
        val t = sound.type.lowercase()
        return when {
            t.contains("log") -> "Amapiano Log Kick"
            t.contains("kick") || t.contains("808") || t.contains("sub") || t == "bass" -> "808 Heavy Kick"
            t.contains("snare") || t.contains("rim") || t.contains("snap") -> "Trap Snap Snare"
            t.contains("clap") -> "Gqom Heavy Clap"
            t.contains("open_hat") -> "Open Hi-Hat"
            t.contains("hat") -> "Tight Trap HiHat"
            t.contains("shaker") || t.contains("tamb") -> "Shaker"
            t.contains("crash") || t.contains("ride") || t.contains("cymbal") -> "Crash Cymbal"
            t.contains("tom") -> "Tom"
            t.contains("riser") || t.contains("downlifter") || t.contains("impact") || t.contains("texture") -> "White Noise Riser FX"
            else -> sound.prettyType().ifBlank { sound.displayName }
        }
    }

    // ---------------------------------------------------------------- preview

    private var previewPlayer: MediaPlayer? = null

    /** Id of the sound currently previewing (Compose-observable), or null. */
    val previewingId = mutableStateOf<String?>(null)

    /**
     * Plays the real WAV for [sound], stopping any previous preview. Main thread.
     * Returns false if the asset couldn't be played (caller may fall back to a synth hit).
     */
    fun preview(context: Context, sound: FactorySound): Boolean {
        stopPreview()
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                context.assets.openFd(sound.assetPath).use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            } catch (e: IOException) {
                // Asset stored compressed (openFd needs it uncompressed): play an app-storage copy.
                val f = materialize(context, sound) ?: throw e
                mp.setDataSource(f.absolutePath)
            }
            mp.setOnCompletionListener { done -> if (previewPlayer === done) stopPreview() }
            mp.prepare()
            mp.start()
            previewPlayer = mp
            previewingId.value = sound.id
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Preview failed for ${sound.assetPath}", t)
            runCatching { mp.release() }
            false
        }
    }

    fun stopPreview() {
        previewPlayer?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        previewPlayer = null
        previewingId.value = null
    }
}
