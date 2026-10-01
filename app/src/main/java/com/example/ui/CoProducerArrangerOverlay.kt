package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.model.TrackType
import com.example.model.MidiNote
import com.example.ui.theme.*

@Composable
fun CoProducerArrangerOverlay(
    project: ProjectData,
    onApplyArrangement: (ProjectData) -> Unit,
    onDismiss: () -> Unit
) {
    var isAiAssisted by remember { mutableStateOf(true) }
    var introLengthBeats by remember { mutableFloatStateOf(16f) }
    var dropStartBeat by remember { mutableFloatStateOf(16f) }
    var outroStartBeat by remember { mutableFloatStateOf(48f) }
    var totalSongBeats by remember { mutableFloatStateOf(64f) }
    var aiEnergyLevel by remember { mutableFloatStateOf(0.85f) }
    var includeSubDrop by remember { mutableStateOf(true) }
    var includeRisers by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, SanwolfGold, RoundedCornerShape(16.dp)),
            color = SanwolfPanel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SYSTEM AI CO-PRODUCER ARRANGER",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = SanwolfGold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Intelligent Song Structure: Starting Point • Drop • Finish Point",
                                fontSize = 11.sp,
                                color = SanwolfCyan
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Toggle: Manual vs AI Assisted
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isAiAssisted) SanwolfGold else Color.Transparent)
                            .clickable { isAiAssisted = false }
                            .padding(vertical = 10.dp)
                            .testTag("manual_mode_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MANUAL ARRANGEMENT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (!isAiAssisted) Color.Black else SanwolfTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isAiAssisted) SanwolfGold else Color.Transparent)
                            .clickable { isAiAssisted = true }
                            .padding(vertical = 10.dp)
                            .testTag("ai_assisted_mode_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AI ASSISTED CO-PRODUCER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isAiAssisted) Color.Black else SanwolfTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isAiAssisted) {
                    // Manual mode info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SanwolfPanelElevated)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = SanwolfTextMuted, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Manual Arrangement Mode Active",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SanwolfTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Use the Arranger timeline grid, drag clips, resize regions, and adjust track order freely.",
                                fontSize = 11.sp,
                                color = SanwolfTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    // AI Assisted Granular Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SanwolfPanelElevated)
                            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "GRANULAR AI SONG STRUCTURE CONTROLS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = SanwolfGold
                        )

                        // Starting Point (Intro) Slider
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Starting Point / Intro Length", fontSize = 11.sp, color = SanwolfTextPrimary, fontWeight = FontWeight.Bold)
                                Text("${introLengthBeats.toInt()} Beats (Bars 1-${(introLengthBeats / 4).toInt()})", fontSize = 11.sp, color = SanwolfCyan, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = introLengthBeats,
                                onValueChange = { introLengthBeats = it },
                                valueRange = 4f..32f,
                                steps = 7,
                                colors = SliderDefaults.colors(thumbColor = SanwolfCyan, activeTrackColor = SanwolfCyan)
                            )
                        }

                        // Drop (Climax) Slider
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Drop / Main Climax Start Beat", fontSize = 11.sp, color = SanwolfTextPrimary, fontWeight = FontWeight.Bold)
                                Text("Beat ${dropStartBeat.toInt()} (Bar ${(dropStartBeat / 4).toInt() + 1})", fontSize = 11.sp, color = SanwolfGold, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = dropStartBeat,
                                onValueChange = { dropStartBeat = it },
                                valueRange = 8f..32f,
                                steps = 6,
                                colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
                            )
                        }

                        // Finish Point (Outro) Slider
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Finish Point / Outro Start Beat", fontSize = 11.sp, color = SanwolfTextPrimary, fontWeight = FontWeight.Bold)
                                Text("Beat ${outroStartBeat.toInt()} (Bar ${(outroStartBeat / 4).toInt() + 1})", fontSize = 11.sp, color = SanwolfMagenta, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = outroStartBeat,
                                onValueChange = { outroStartBeat = it },
                                valueRange = 32f..96f,
                                steps = 16,
                                colors = SliderDefaults.colors(thumbColor = SanwolfMagenta, activeTrackColor = SanwolfMagenta)
                            )
                        }

                        // AI Energy Level
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Co-Producer Intensity & Drive", fontSize = 11.sp, color = SanwolfTextPrimary, fontWeight = FontWeight.Bold)
                                Text("${(aiEnergyLevel * 100).toInt()}%", fontSize = 11.sp, color = SanwolfLime, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = aiEnergyLevel,
                                onValueChange = { aiEnergyLevel = it },
                                valueRange = 0.2f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = SanwolfLime, activeTrackColor = SanwolfLime)
                            )
                        }

                        // Toggles for Sub Drop & Risers
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Include 808 Sub Drop at Climax", fontSize = 11.sp, color = SanwolfTextSecondary)
                            Switch(
                                checked = includeSubDrop,
                                onCheckedChange = { includeSubDrop = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = SanwolfGold, checkedTrackColor = SanwolfGold.copy(alpha = 0.5f))
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Include Riser FX at Transition Points", fontSize = 11.sp, color = SanwolfTextSecondary)
                            Switch(
                                checked = includeRisers,
                                onCheckedChange = { includeRisers = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = SanwolfCyan, checkedTrackColor = SanwolfCyan.copy(alpha = 0.5f))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Footer Button
                Button(
                    onClick = {
                        if (isAiAssisted) {
                            val arrangedProject = project.copy(
                                tracks = project.tracks.map { track ->
                                    val notes = track.notes.toMutableList()
                                    if (track.type == TrackType.SYNTH || track.type == TrackType.MODULAR_SYNTH) {
                                        notes.add(MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = introLengthBeats.toDouble() * 0.5, velocity = 0.6f * aiEnergyLevel))
                                        notes.add(MidiNote(pitch = 63, startBeat = dropStartBeat.toDouble(), lengthBeats = 16.0, velocity = 0.95f * aiEnergyLevel))
                                        notes.add(MidiNote(pitch = 67, startBeat = dropStartBeat.toDouble() + 4.0, lengthBeats = 12.0, velocity = 1.0f * aiEnergyLevel))
                                        notes.add(MidiNote(pitch = 60, startBeat = outroStartBeat.toDouble(), lengthBeats = 16.0, velocity = 0.4f))
                                    } else if (track.type == TrackType.DRUM_MACHINE) {
                                        track.stepCount = totalSongBeats.toInt() * 4
                                        for (i in 0 until track.stepCount) {
                                            val beat = i / 4.0
                                            if (beat < introLengthBeats) {
                                                track.steps[i % 64] = (i % 16 == 0)
                                            } else if (beat >= dropStartBeat && beat < outroStartBeat) {
                                                track.steps[i % 64] = (i % 4 == 0 || i % 8 == 6)
                                            } else if (beat >= outroStartBeat) {
                                                track.steps[i % 64] = (i % 32 == 0)
                                            }
                                        }
                                    }
                                    track.copy(notes = notes)
                                }.toMutableList()
                            )
                            onApplyArrangement(arrangedProject)
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("apply_co_producer_arrangement"),
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAiAssisted) "APPLY AI CO-PRODUCER ARRANGEMENT" else "CLOSE MANUAL MODE",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
