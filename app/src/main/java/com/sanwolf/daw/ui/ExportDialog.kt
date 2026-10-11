package com.sanwolf.daw.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sanwolf.daw.audio.MidiExporter
import com.sanwolf.daw.audio.WavExporter
import com.sanwolf.daw.export.AudioExport
import com.sanwolf.daw.export.ExportAudioFormat
import com.sanwolf.daw.export.ExportTags
import com.sanwolf.daw.model.ProjectData
import com.sanwolf.daw.ui.theme.SanwolfBlack
import com.sanwolf.daw.ui.theme.SanwolfCyan
import com.sanwolf.daw.ui.theme.SanwolfGold
import com.sanwolf.daw.ui.theme.SanwolfLime
import com.sanwolf.daw.ui.theme.SanwolfPanel
import com.sanwolf.daw.ui.theme.SanwolfPanelBorder
import com.sanwolf.daw.ui.theme.SanwolfPanelElevated
import com.sanwolf.daw.ui.theme.SanwolfTextMuted
import com.sanwolf.daw.ui.theme.SanwolfTextPrimary
import com.sanwolf.daw.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ExportFormat {
    WAV,
    MIDI
}

enum class MidiScope {
    FULL_PROJECT,
    CURRENT_SEQUENCE
}

@Composable
fun ExportDialog(
    project: ProjectData,
    selectedTrack: com.sanwolf.daw.model.TrackData? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope { com.sanwolf.daw.CrashReporter.coroutineHandler }

    var format by remember { mutableStateOf(ExportFormat.WAV) }
    var audioFormat by remember { mutableStateOf(ExportAudioFormat.WAV_24) }
    var availableAudio by remember { mutableStateOf(listOf(ExportAudioFormat.WAV_24, ExportAudioFormat.WAV_16)) }
    var midiScope by remember { mutableStateOf(MidiScope.FULL_PROJECT) }
    var barCount by remember { mutableStateOf(16) }
    var isRendering by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportedMime by remember { mutableStateOf("audio/wav") }
    var statusText by remember { mutableStateOf<String?>(null) }

    // MediaCodecList / encoder probing touches the codec service: do it off the main thread
    LaunchedEffect(Unit) {
        availableAudio = withContext(Dispatchers.IO) { runCatching { AudioExport.availableFormats() }.getOrDefault(availableAudio) }
        if (audioFormat !in availableAudio) audioFormat = ExportAudioFormat.WAV_24
    }

    fun openShare(file: File, mime: String) {
        runCatching {
            val intent = if (format == ExportFormat.MIDI) {
                MidiExporter.createShareIntent(context, file, "Share SANWOLF MIDI: ${file.name}")
            } else {
                AudioExport.shareIntent(context, file, mime)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
        }.onFailure { err ->
            android.util.Log.e("ExportDialog", "Share sheet failed", err)
            android.widget.Toast.makeText(context, "Couldn't open the share sheet", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    ScaledDialog(
        onDismissRequest = { if (!isRendering) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, SanwolfGold, RoundedCornerShape(12.dp)),
            color = SanwolfPanel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.IosShare, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "EXPORT SONG",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = SanwolfGold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Saved to Music/SANWOLF • share anywhere • WAV / FLAC / AAC / MP3 / MIDI",
                                fontSize = 10.sp,
                                color = SanwolfTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = { if (!isRendering) onDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Audio vs MIDI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExportFormatCard(
                            title = "Audio mixdown",
                            subtitle = "WAV • FLAC • AAC • MP3 • 44.1 kHz stereo",
                            isSelected = format == ExportFormat.WAV,
                            accentColor = SanwolfGold,
                            modifier = Modifier.weight(1f)
                        ) {
                            format = ExportFormat.WAV
                            exportedFile = null
                        }

                        ExportFormatCard(
                            title = "Standard MIDI (.mid)",
                            subtitle = "Type 1 SMF • Piano Roll & Drums",
                            isSelected = format == ExportFormat.MIDI,
                            accentColor = SanwolfCyan,
                            modifier = Modifier.weight(1f)
                        ) {
                            format = ExportFormat.MIDI
                            exportedFile = null
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val activeTrack = selectedTrack ?: project.tracks.firstOrNull()

                    if (format == ExportFormat.WAV) {
                        // Quick presets
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExportChip("Distributor master · WAV 24-bit", audioFormat == ExportAudioFormat.WAV_24, SanwolfGold) {
                                audioFormat = ExportAudioFormat.WAV_24
                            }
                            if (ExportAudioFormat.MP3_320 in availableAudio) {
                                ExportChip("Quick share · MP3 320", audioFormat == ExportAudioFormat.MP3_320, SanwolfCyan) {
                                    audioFormat = ExportAudioFormat.MP3_320
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("FORMAT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfTextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        // Two columns of format cards
                        availableAudio.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                pair.forEach { f ->
                                    ExportFormatCard(
                                        title = f.label,
                                        subtitle = f.detail,
                                        isSelected = audioFormat == f,
                                        accentColor = if (f.isLossless) SanwolfGold else SanwolfCyan,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        audioFormat = f
                                        exportedFile = null
                                    }
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("LENGTH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfTextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(4, 8, 16, 32, 64).forEach { bars ->
                                ExportChip("$bars bars", barCount == bars, SanwolfGold) { barCount = bars }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tags: \"${project.title}\" by ${project.artist.ifBlank { "SANWOLF" }} (set the artist in Song info).",
                            fontSize = 10.sp,
                            color = SanwolfTextMuted
                        )
                    } else {
                        // Scope selector for MIDI export
                        Text("MIDI EXPORT SCOPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfTextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExportFormatCard(
                                title = "FULL PROJECT",
                                subtitle = "${project.tracks.size} Multi-Track SMF",
                                isSelected = midiScope == MidiScope.FULL_PROJECT,
                                accentColor = SanwolfCyan,
                                modifier = Modifier.weight(1f)
                            ) { midiScope = MidiScope.FULL_PROJECT }
                            ExportFormatCard(
                                title = "CURRENT SEQUENCE",
                                subtitle = "${activeTrack?.name ?: "Piano Roll"} (${activeTrack?.notes?.size ?: 0} notes)",
                                isSelected = midiScope == MidiScope.CURRENT_SEQUENCE,
                                accentColor = SanwolfGold,
                                modifier = Modifier.weight(1f)
                            ) { midiScope = MidiScope.CURRENT_SEQUENCE }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isRendering) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = SanwolfGold,
                        trackColor = SanwolfPanelElevated
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Status Info
                if (statusText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SanwolfBlack)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = statusText!!,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SanwolfLime
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Render / encode / save
                    Button(
                        onClick = {
                            val activeTrack = selectedTrack ?: project.tracks.firstOrNull()
                            val chosenFormat = format
                            val chosenAudio = audioFormat
                            val bars = barCount
                            val tags = ExportTags(title = project.title, artist = project.artist.ifBlank { "SANWOLF" })
                            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
                            val baseName = com.sanwolf.daw.data.ProjectBundle.safeFileName(project.title) + "_" + stamp
                            com.sanwolf.daw.CrashReporter.breadcrumb("export ${chosenFormat.name} ${if (chosenFormat == ExportFormat.WAV) chosenAudio.label else ""} (${project.tracks.size} tracks)")
                            isRendering = true
                            progress = 0f
                            exportedFile = null
                            statusText = if (chosenFormat == ExportFormat.WAV) "Rendering ${chosenAudio.label}…" else "Writing MIDI…"
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    runCatching {
                                        if (chosenFormat == ExportFormat.WAV) {
                                            val pcm = WavExporter.renderMaster(context, project, bars) { p -> progress = p * 0.55f }
                                            val file = AudioExport.encode(context, pcm, chosenAudio, tags, baseName) { p -> progress = 0.55f + p * 0.4f }
                                            val where = withContext(Dispatchers.IO) {
                                                runCatching { AudioExport.saveToMusic(context, file, chosenAudio.mime) }
                                                    .onFailure { android.util.Log.e("ExportDialog", "Save to Music failed", it) }
                                                    .getOrNull()
                                            }
                                            Triple(file, chosenAudio.mime, where)
                                        } else {
                                            val file = withContext(Dispatchers.IO) {
                                                if (midiScope == MidiScope.CURRENT_SEQUENCE && activeTrack != null) {
                                                    MidiExporter.exportTrackSequenceToMidi(context, activeTrack, project.bpm, project.title)
                                                } else {
                                                    MidiExporter.exportProjectToMidi(context, project)
                                                }
                                            }
                                            Triple(file, "audio/midi", null as String?)
                                        }
                                    }
                                }
                                isRendering = false
                                progress = 1f
                                val (file, mime, where) = result.getOrElse { err ->
                                    android.util.Log.e("ExportDialog", "Export failed", err)
                                    statusText = "Export failed: ${err.message ?: err.javaClass.simpleName}"
                                    android.widget.Toast.makeText(context, "Export failed: ${err.message ?: "unknown error"}", android.widget.Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                exportedFile = file
                                exportedMime = mime
                                statusText = if (chosenFormat == ExportFormat.WAV) {
                                    val loud = "%.1f LUFS integrated".format(WavExporter.lastExportIntegratedLufs)
                                    val size = "${file.length() / 1024} KB"
                                    if (where != null) "Saved to $where ($size) · $loud. Tap Share to send it anywhere."
                                    else "Rendered ${file.name} ($size) · $loud. Couldn't save to Music; use Share."
                                } else {
                                    "Rendered '${file.name}' (${file.length() / 1024} KB). Tap Share or save to Downloads."
                                }
                            }
                        },
                        enabled = !isRendering,
                        modifier = Modifier.weight(1f).height(44.dp).testTag("render_export_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                    ) {
                        if (isRendering) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RENDERING ${(progress * 100).toInt()}%", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (format == ExportFormat.MIDI) "EXPORT MIDI" else "EXPORT ${audioFormat.label.uppercase()}",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                    val ready = exportedFile
                    Button(
                        onClick = { ready?.let { openShare(it, exportedMime) } },
                        enabled = !isRendering && ready != null,
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfCyan)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }

                if (format == ExportFormat.MIDI) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val activeTrack = selectedTrack ?: project.tracks.firstOrNull()
                            scope.launch {
                                val res = runCatching {
                                    val file = exportedFile ?: withContext(Dispatchers.IO) {
                                        if (midiScope == MidiScope.CURRENT_SEQUENCE && activeTrack != null) {
                                            MidiExporter.exportTrackSequenceToMidi(context, activeTrack, project.bpm, project.title)
                                        } else {
                                            MidiExporter.exportProjectToMidi(context, project)
                                        }
                                    }
                                    exportedFile = file
                                    exportedMime = "audio/midi"
                                    withContext(Dispatchers.IO) { MidiExporter.saveMidiToDownloads(context, file) }
                                    file
                                }
                                statusText = res.fold(
                                    onSuccess = { "Saved '${it.name}' to Downloads/SanwolfDaw folder!" },
                                    onFailure = { "Couldn't save MIDI: ${it.message ?: "error"}" }
                                )
                            }
                        },
                        enabled = !isRendering,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfPanelElevated)
                    ) {
                        Text("SAVE MIDI TO DOWNLOADS", color = SanwolfCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) accent else SanwolfPanelElevated)
            .border(1.dp, if (selected) accent else SanwolfPanelBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            color = if (selected) Color.Black else SanwolfTextPrimary
        )
    }
}

@Composable
private fun ExportFormatCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SanwolfPanelElevated else SanwolfBlack)
            .border(
                1.5.dp,
                if (isSelected) accentColor else SanwolfPanelBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accentColor else SanwolfTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = SanwolfTextSecondary
            )
        }
    }
}

// Intent helper import
private typealias Intent = android.content.Intent
