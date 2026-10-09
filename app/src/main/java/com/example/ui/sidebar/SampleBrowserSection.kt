package com.example.ui.sidebar

import android.media.MediaPlayer
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.FactoryGenre
import com.example.audio.FactoryLibrary
import com.example.audio.FactorySound
import com.example.audio.UserSampleRepository
import com.example.model.ProjectData
import com.example.model.TrackType
import com.example.studio.RecordingEngine
import com.example.ui.FactorySampleCatalog
import com.example.ui.FactoryWavSample
import com.example.ui.SanwolfTextField
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

enum class SampleSource(val label: String) {
    FACTORY("Factory"),
    MY_SAMPLES("My samples"),
    RECORDINGS("Recordings"),
    IMPORTED("Imported")
}

/** One row in the sample browser, whatever tab it came from. */
private data class BrowserItem(
    val key: String,
    val name: String,
    val detail: String,
    val color: Color,
    val factory: FactoryWavSample? = null,
    val uri: Uri? = null,
    val sound: FactorySound? = null,
    val category: String? = null
)

/** Genre filter value meaning "every genre". */
private const val ALL_GENRES = "all"


/**
 * SAMPLES section of the drawer, modeled on Cubasis' Media Bay: source tabs, search,
 * per-row preview (play/stop) and Add (adds the sample as a new track).
 */
@Composable
fun SampleBrowserSection(
    project: ProjectData,
    refreshKey: Int,
    onPreviewFactory: (FactoryWavSample) -> Unit,
    onAddFactorySample: (FactoryWavSample) -> Unit,
    onPreviewFactorySynthFallback: (FactorySound) -> Unit,
    onAddFactorySound: (FactorySound) -> Unit,
    onAddFactorySoundToSteps: (FactorySound) -> Unit,
    onAddAudioFile: (name: String, uri: Uri) -> Unit,
    onImportAudio: () -> Unit,
    onOpenFullSampleManager: () -> Unit
) {
    val context = LocalContext.current
    var sourceName by rememberSaveable { mutableStateOf(SampleSource.FACTORY.name) }
    val source = runCatching { SampleSource.valueOf(sourceName) }.getOrDefault(SampleSource.FACTORY)
    var query by rememberSaveable { mutableStateOf("") }
    var previewingKey by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<BrowserItem>>(emptyList()) }
    var genreKey by rememberSaveable { mutableStateOf(FactoryGenre.AFRO_HOUSE.key) }
    // Real factory WAVs currently previewing (shared player, also used by the full manager dialog)
    val factoryPreviewId = FactoryLibrary.previewingId.value

    // File preview player (recordings / user samples / imported). Released when the section leaves.
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun stopPreview() {
        player[0]?.let { mp -> runCatching { mp.stop() }; runCatching { mp.release() } }
        player[0] = null
        previewingKey = null
        FactoryLibrary.stopPreview()
    }
    DisposableEffect(Unit) {
        onDispose {
            player[0]?.let { runCatching { it.release() } }; player[0] = null
            FactoryLibrary.stopPreview()
        }
    }

    // Snapshot of the project's imported audio (read on the UI thread, which owns the project).
    val importedTracks = project.tracks
        .filter { it.type == TrackType.AUDIO_IMPORT && !it.audioUri.isNullOrBlank() }
        .map { Triple(it.id, it.audioFileName ?: it.name, it.audioUri!!) }

    LaunchedEffect(source, refreshKey, importedTracks.size) {
        items = withContext(Dispatchers.IO) {
            runCatching {
                when (source) {
                    SampleSource.FACTORY -> {
                        val sounds = FactoryLibrary.sounds(context.applicationContext)
                        if (sounds.isNotEmpty()) {
                            sounds.map { snd ->
                                BrowserItem(
                                    key = "wav:${snd.id}",
                                    name = snd.displayName,
                                    detail = snd.detail(),
                                    color = Color(snd.color),
                                    sound = snd,
                                    category = snd.category
                                )
                            }
                        } else {
                            // Fallback: the bundled WAVs couldn't be read, show the synth placeholders.
                            FactorySampleCatalog.samples.map { s ->
                                BrowserItem(
                                    key = "factory:${s.id}",
                                    name = s.name.removeSuffix(".wav"),
                                    detail = "${s.category} • ${s.genre} (synth)",
                                    color = Color(s.colorHex),
                                    factory = s
                                )
                            }
                        }
                    }
                    SampleSource.MY_SAMPLES -> UserSampleRepository(context.applicationContext)
                        .getImportedSamples()
                        .filter { File(it.filePath).exists() }
                        .map { m ->
                            BrowserItem(
                                key = "user:${m.sampleId}",
                                name = m.name,
                                detail = "Saved sample",
                                color = SanwolfCyan,
                                uri = Uri.fromFile(File(m.filePath))
                            )
                        }
                    SampleSource.RECORDINGS -> {
                        val dir = File(context.filesDir, "recordings")
                        val bytesPerSecond = RecordingEngine.SAMPLE_RATE * RecordingEngine.CHANNEL_COUNT * RecordingEngine.BITS_PER_SAMPLE / 8
                        (dir.listFiles { f: File -> f.isFile && f.name.endsWith(".wav") } ?: emptyArray())
                            .sortedByDescending { it.lastModified() }
                            .map { f ->
                                val secs = ((f.length() - 44).coerceAtLeast(0L)).toDouble() / bytesPerSecond
                                val whenText = DateUtils.getRelativeTimeSpanString(
                                    f.lastModified(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                                )
                                BrowserItem(
                                    key = "rec:${f.name}",
                                    name = "Mic take " + DateUtils.formatDateTime(
                                        context, f.lastModified(),
                                        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
                                    ),
                                    detail = String.format("%.1fs • %s", secs, whenText),
                                    color = SanwolfMagenta,
                                    uri = Uri.fromFile(f)
                                )
                            }
                    }
                    SampleSource.IMPORTED -> importedTracks
                        .distinctBy { it.third }
                        .map { (id, name, uri) ->
                            BrowserItem(
                                key = "imp:$id",
                                name = name,
                                detail = "Audio in this project",
                                color = SanwolfGold,
                                uri = Uri.parse(uri)
                            )
                        }
                }
            }.getOrDefault(emptyList())
        }
    }

    val isFactoryWav = source == SampleSource.FACTORY && items.any { it.sound != null }
    val filtered = if (isFactoryWav) {
        // Factory: genre chip filters; a search looks across every genre by sound name.
        val q = query.trim()
        items.filter { item ->
            val snd = item.sound ?: return@filter false
            if (q.isNotEmpty()) snd.displayName.contains(q, ignoreCase = true)
            else genreKey == ALL_GENRES || snd.genre == genreKey
        }.sortedWith(
            compareBy<BrowserItem>(
                { FactoryLibrary.categoryRank(it.category ?: "") },
                { FactoryGenre.values().indexOfFirst { g -> g.key == it.sound?.genre }.let { i -> if (i < 0) 99 else i } },
                { it.name }
            )
        ).map { item ->
            if (q.isNotEmpty() || genreKey == ALL_GENRES) item.copy(detail = item.sound!!.detail(includeGenre = true)) else item
        }
    } else if (query.isBlank()) items else items.filter {
        it.name.contains(query, ignoreCase = true) || it.detail.contains(query, ignoreCase = true)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SidebarActionRow(
            label = "Import audio file…",
            icon = Icons.Default.FileUpload,
            tint = SanwolfLime,
            subtitle = "WAV / MP3 from your device, added as a track",
            onClick = onImportAudio
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SampleSource.values().forEach { s ->
                SidebarChip(
                    label = s.label,
                    selected = s == source,
                    onClick = {
                        stopPreview()
                        sourceName = s.name
                    }
                )
            }
        }

        if (isFactoryWav) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FactoryGenre.values().forEach { g ->
                    SidebarChip(
                        label = g.label,
                        selected = genreKey == g.key && query.isBlank(),
                        onClick = { genreKey = g.key; query = "" }
                    )
                }
                SidebarChip(
                    label = "All genres",
                    selected = genreKey == ALL_GENRES && query.isBlank(),
                    onClick = { genreKey = ALL_GENRES; query = "" }
                )
            }
        }

        SanwolfTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search ${source.label.lowercase()}", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth()
        )

        if (filtered.isEmpty()) {
            SidebarEmptyState(
                when {
                    query.isNotBlank() -> "Nothing matches \"$query\"."
                    source == SampleSource.MY_SAMPLES -> "No saved samples yet — tap Import audio file to add one."
                    source == SampleSource.RECORDINGS -> "No mic takes yet — hit Record to capture one."
                    source == SampleSource.IMPORTED -> "No imported audio in this project yet."
                    else -> "No samples."
                }
            )
        } else {
            var lastCategory: String? = null
            filtered.forEach { item ->
                if (isFactoryWav && item.category != lastCategory) {
                    lastCategory = item.category
                    SidebarSectionHeader(title = (item.category ?: "").uppercase())
                }
                val snd = item.sound
                val isPreviewing = if (snd != null) factoryPreviewId == snd.id else previewingKey == item.key
                SampleRow(
                    item = item,
                    isPreviewing = isPreviewing,
                    onAddToSteps = if (snd != null && snd.isDrumOneShot) {
                        { stopPreview(); onAddFactorySoundToSteps(snd) }
                    } else null,
                    onTogglePreview = {
                        if (isPreviewing) {
                            stopPreview()
                        } else {
                            stopPreview()
                            val f = item.factory
                            val u = item.uri
                            if (snd != null) {
                                if (!FactoryLibrary.preview(context, snd)) {
                                    // Asset missing/unreadable: fall back to the synth stand-in.
                                    onPreviewFactorySynthFallback(snd)
                                }
                            } else if (f != null) {
                                previewingKey = item.key
                                onPreviewFactory(f)
                            } else if (u != null) {
                                val mp = runCatching {
                                    MediaPlayer().apply {
                                        setDataSource(context, u)
                                        setOnCompletionListener { if (previewingKey == item.key) stopPreview() }
                                        prepare()
                                        start()
                                    }
                                }.getOrNull()
                                if (mp != null) {
                                    player[0] = mp
                                    previewingKey = item.key
                                } else {
                                    android.widget.Toast.makeText(context, "Can't play this file", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onAdd = {
                        stopPreview()
                        val f = item.factory
                        val u = item.uri
                        when {
                            snd != null -> onAddFactorySound(snd)
                            f != null -> onAddFactorySample(f)
                            u != null -> onAddAudioFile(item.name, u)
                        }
                    }
                )
            }
        }

        // Factory previews are one-shot synth hits: clear the "playing" state after a moment.
        LaunchedEffect(previewingKey) {
            val k = previewingKey
            if (k != null && k.startsWith("factory:")) {
                delay(700)
                if (previewingKey == k) previewingKey = null
            }
        }

        SidebarActionRow(
            label = "Full sample manager",
            icon = Icons.Default.LibraryMusic,
            tint = SanwolfGold,
            subtitle = "Bigger browser with every genre",
            onClick = onOpenFullSampleManager
        )
    }
}

@Composable
private fun SampleRow(
    item: BrowserItem,
    isPreviewing: Boolean,
    onTogglePreview: () -> Unit,
    onAdd: () -> Unit,
    onAddToSteps: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPreviewing) item.color.copy(alpha = 0.14f) else SanwolfPanelElevated)
            .padding(start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onTogglePreview,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = if (isPreviewing) "Stop preview of ${item.name}" else "Preview ${item.name}" }
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(item.color.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = item.color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SanwolfTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.detail,
                fontSize = 12.sp,
                color = SanwolfTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        if (onAddToSteps != null) {
            IconButton(
                onClick = onAddToSteps,
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = "Add ${item.name} as a step-sequencer drum track" }
            ) {
                Icon(Icons.Default.GridOn, contentDescription = null, tint = SanwolfCyan, modifier = Modifier.size(20.dp))
            }
        }
        IconButton(
            onClick = onAdd,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Add ${item.name} as a new track" }
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = SanwolfLime)
        }
    }
}
