package com.example.ui

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.audio.MidiExporter
import com.example.audio.WavExporter
import com.example.model.ProjectData
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary
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
    selectedTrack: com.example.model.TrackData? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var format by remember { mutableStateOf(ExportFormat.WAV) }
    var midiScope by remember { mutableStateOf(MidiScope.FULL_PROJECT) }
    var barCount by remember { mutableStateOf(4) }
    var isRendering by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .fillMaxHeight(0.80f)
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
                                text = "EXPORT DAW PROJECT",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = SanwolfGold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Standard WAV 44.1kHz / 16-bit PCM • SMF Standard MIDI (.mid)",
                                fontSize = 10.sp,
                                color = SanwolfTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Format Selector (WAV vs MIDI)
                Text("EXPORT FORMAT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportFormatCard(
                        title = "Lossless Master WAV",
                        subtitle = "16-Bit • 44.1kHz • Stereo PCM",
                        isSelected = format == ExportFormat.WAV,
                        accentColor = SanwolfGold,
                        modifier = Modifier.weight(1f)
                    ) {
                        format = ExportFormat.WAV
                    }

                    ExportFormatCard(
                        title = "Standard MIDI (.mid)",
                        subtitle = "Type 1 SMF • Piano Roll & Drums",
                        isSelected = format == ExportFormat.MIDI,
                        accentColor = SanwolfCyan,
                        modifier = Modifier.weight(1f)
                    ) {
                        format = ExportFormat.MIDI
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val activeTrack = selectedTrack ?: project.tracks.firstOrNull()

                if (format == ExportFormat.WAV) {
                    // Length selector for audio render
                    Text("RENDER RANGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 4, 8, 16).forEach { bars ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (barCount == bars) SanwolfGold else SanwolfPanelElevated)
                                    .border(1.dp, if (barCount == bars) SanwolfGold else SanwolfPanelBorder, RoundedCornerShape(6.dp))
                                    .clickable { barCount = bars }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "$bars Bars",
                                    fontSize = 11.sp,
                                    fontWeight = if (barCount == bars) FontWeight.Black else FontWeight.Medium,
                                    color = if (barCount == bars) Color.Black else SanwolfTextPrimary
                                )
                            }
                        }
                    }
                } else {
                    // Scope selector for MIDI export
                    Text("MIDI EXPORT SCOPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (midiScope == MidiScope.FULL_PROJECT) SanwolfCyan.copy(alpha = 0.25f) else SanwolfPanelElevated)
                                .border(1.dp, if (midiScope == MidiScope.FULL_PROJECT) SanwolfCyan else SanwolfPanelBorder, RoundedCornerShape(6.dp))
                                .clickable { midiScope = MidiScope.FULL_PROJECT }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "FULL PROJECT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (midiScope == MidiScope.FULL_PROJECT) SanwolfCyan else SanwolfTextPrimary
                                )
                                Text(
                                    text = "${project.tracks.size} Multi-Track SMF",
                                    fontSize = 9.sp,
                                    color = SanwolfTextMuted
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (midiScope == MidiScope.CURRENT_SEQUENCE) SanwolfGold.copy(alpha = 0.25f) else SanwolfPanelElevated)
                                .border(1.dp, if (midiScope == MidiScope.CURRENT_SEQUENCE) SanwolfGold else SanwolfPanelBorder, RoundedCornerShape(6.dp))
                                .clickable { midiScope = MidiScope.CURRENT_SEQUENCE }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "CURRENT SEQUENCE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (midiScope == MidiScope.CURRENT_SEQUENCE) SanwolfGold else SanwolfTextPrimary
                                )
                                Text(
                                    text = "${activeTrack?.name ?: "Piano Roll"} (${activeTrack?.notes?.size ?: 0} notes)",
                                    fontSize = 9.sp,
                                    color = SanwolfTextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

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
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Render & Share Action
                Button(
                    onClick = {
                        isRendering = true
                        statusText = "Rendering export..."
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                if (format == ExportFormat.WAV) {
                                    WavExporter.exportProjectToWav(context, project, barCount)
                                } else {
                                    if (midiScope == MidiScope.CURRENT_SEQUENCE && activeTrack != null) {
                                        MidiExporter.exportTrackSequenceToMidi(context, activeTrack, project.bpm, project.title)
                                    } else {
                                        MidiExporter.exportProjectToMidi(context, project)
                                    }
                                }
                            }
                            exportedFile = file
                            isRendering = false
                            statusText = "Rendered '${file.name}' (${file.length() / 1024} KB). Opening Share Sheet..."

                            // Launch Share Intent
                            val shareIntent = if (format == ExportFormat.WAV) {
                                WavExporter.createShareIntent(context, file)
                            } else {
                                MidiExporter.createShareIntent(context, file, "Share SANWOLF MIDI: ${file.name}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share SANWOLF Export"))
                        }
                    },
                    enabled = !isRendering,
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("render_export_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                ) {
                    if (isRendering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RENDERING BUFFER...", color = Color.Black, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (format == ExportFormat.MIDI && midiScope == MidiScope.CURRENT_SEQUENCE) {
                                "EXPORT & SHARE SEQUENCE MIDI"
                            } else {
                                "RENDER & SHARE ${format.name}"
                            },
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }

                if (format == ExportFormat.MIDI) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val file = exportedFile ?: withContext(Dispatchers.IO) {
                                    if (midiScope == MidiScope.CURRENT_SEQUENCE && activeTrack != null) {
                                        MidiExporter.exportTrackSequenceToMidi(context, activeTrack, project.bpm, project.title)
                                    } else {
                                        MidiExporter.exportProjectToMidi(context, project)
                                    }
                                }
                                exportedFile = file
                                val uri = withContext(Dispatchers.IO) {
                                    MidiExporter.saveMidiToDownloads(context, file)
                                }
                                statusText = "Saved '${file.name}' to Downloads/SanwolfDaw folder!"
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
