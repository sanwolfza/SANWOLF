package com.example.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.MidiExporter
import com.example.model.MidiNote
import com.example.model.TrackData
import com.example.model.TrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary

enum class PianoRollToolMode {
    DRAW,
    VELOCITY,
    ERASE
}

@Composable
fun PianoRollView(
    track: TrackData,
    currentBeat: Double,
    isPlaying: Boolean,
    onAddOrRemoveNote: (pitch: Int, startBeat: Double) -> Unit,
    onClearNotes: () -> Unit,
    onPreviewPitch: (pitch: Int) -> Unit,
    onQuantizeNotes: (scale: String) -> Unit,
    onStampChord: (chordType: String) -> Unit,
    projectVersion: Int = 0,
    bpm: Int = 120,
    initialSwing: Float = 0.15f,
    onSwingChange: (Float) -> Unit = {},
    onOpenInstrumentSelector: () -> Unit = {},
    onAddNote: (pitch: Int, startBeat: Double, lengthBeats: Double) -> Unit = { p, b, _ -> onAddOrRemoveNote(p, b) },
    onDeleteNote: (pitch: Int, startBeat: Double) -> Unit = { p, b -> onAddOrRemoveNote(p, b) },
    onMoveNote: (note: MidiNote, newPitch: Int, newBeat: Double) -> Unit = { note, p, b ->
        note.pitch = p
        note.startBeat = b
    },
    onUpdateVelocity: (note: MidiNote, velocity: Float) -> Unit = { note, vel ->
        note.velocity = vel
    },
    onPreviewPitchWithVelocity: (pitch: Int, velocity: Float) -> Unit = { pitch, _ ->
        onPreviewPitch(pitch)
    },
    modifier: Modifier = Modifier
) {
    var selectedScale by remember { mutableStateOf("Natural Minor") }
    var showScaleMenu by remember { mutableStateOf(false) }
    var showChordMenu by remember { mutableStateOf(false) }
    var showLengthMenu by remember { mutableStateOf(false) }
    var baseOctave by remember { mutableStateOf(4) } // C3 to C5
    var selectedNoteLength by remember { mutableDoubleStateOf(1.0) }
    var toolMode by remember { mutableStateOf(PianoRollToolMode.DRAW) }
    var selectedNote by remember { mutableStateOf<MidiNote?>(null) }
    var showVelocityLane by remember { mutableStateOf(true) }
    var showExportDialog by remember { mutableStateOf(false) }
    var localRevision by remember { mutableIntStateOf(0) }

    // Swing and Quantize Intensity Configuration
    var swingAmount by remember { mutableFloatStateOf(initialSwing) }
    var quantizeIntensity by remember { mutableFloatStateOf(1.0f) } // Default 100% Intensity
    var showGrooveMenu by remember { mutableStateOf(false) }
    var grooveFeedbackText by remember { mutableStateOf<String?>(null) }

    // Snap-to-Grid Configuration
    var isSnapToGrid by remember { mutableStateOf(true) }
    var snapDivision by remember { mutableDoubleStateOf(0.25) } // Default 1/16 note (0.25 beat)
    var showSnapMenu by remember { mutableStateOf(false) }

    // Active Note Dragging & Velocity Dragging States
    var draggingNote by remember { mutableStateOf<MidiNote?>(null) }
    var dragCurrentBeat by remember { mutableDoubleStateOf(0.0) }
    var dragCurrentPitch by remember { mutableIntStateOf(60) }
    var activeVelocityDragNote by remember { mutableStateOf<MidiNote?>(null) }
    var activeDragVelocityValue by remember { mutableFloatStateOf(0.85f) }

    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    // Display 6 full octaves (C7 down to C1) so all notes across bass and leads are always visible and editable
    val pitches = remember {
        val list = mutableListOf<Int>()
        for (p in 96 downTo 24) {
            list.add(p)
        }
        list
    }

    val totalBeats = 16
    val beatWidth = 50.dp
    val gridScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    // Scroll to middle C (pitch 60) on initial launch
    LaunchedEffect(Unit) {
        // Pitch 60 is at index 36 in 96 downTo 24, keyHeight = 22.dp
        verticalScrollState.scrollTo(36 * 22)
    }

    fun snapBeat(rawBeat: Double): Double {
        return if (isSnapToGrid) {
            val div = snapDivision
            val nearestStep = Math.round(rawBeat / div).toLong()
            val nearestGrid = nearestStep * div
            val isOffBeat = (nearestStep % 2L != 0L)
            val swingOffset = if (isOffBeat) (swingAmount * div * 0.5).toDouble() else 0.0
            val targetBeat = nearestGrid + swingOffset
            val snapped = rawBeat + (targetBeat - rawBeat) * quantizeIntensity.toDouble()
            (Math.round(snapped * 1000.0) / 1000.0).coerceIn(0.0, (totalBeats - div).coerceAtLeast(0.0))
        } else {
            rawBeat.coerceIn(0.0, totalBeats - 0.05)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SanwolfBlack)
            .padding(8.dp)
    ) {
        // --- Piano Roll Toolbar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanel)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PIANO ROLL: ${track.name.uppercase()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfGold,
                    letterSpacing = 0.5.sp
                )

                // Instrument Selector Badge Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfGold.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .clickable { onOpenInstrumentSelector() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("piano_roll_instrument_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Instrument",
                        tint = SanwolfGold,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = track.name,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = SanwolfGold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "▾",
                        fontSize = 8.sp,
                        color = SanwolfTextMuted
                    )
                }

                // Tool Mode: DRAW vs VELOCITY vs ERASE
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfPanelElevated)
                        .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (toolMode == PianoRollToolMode.DRAW) SanwolfGold else Color.Transparent)
                            .clickable { toolMode = PianoRollToolMode.DRAW }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("tool_draw")
                    ) {
                        Text(
                            text = "DRAW",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (toolMode == PianoRollToolMode.DRAW) SanwolfBlack else SanwolfTextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (toolMode == PianoRollToolMode.VELOCITY) SanwolfCyan else Color.Transparent)
                            .clickable { toolMode = PianoRollToolMode.VELOCITY }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("tool_velocity")
                    ) {
                        Text(
                            text = "VEL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (toolMode == PianoRollToolMode.VELOCITY) SanwolfBlack else SanwolfTextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (toolMode == PianoRollToolMode.ERASE) SanwolfMagenta else Color.Transparent)
                            .clickable { toolMode = PianoRollToolMode.ERASE }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("tool_erase")
                    ) {
                        Text(
                            text = "ERASE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (toolMode == PianoRollToolMode.ERASE) SanwolfBlack else SanwolfTextMuted
                        )
                    }
                }

                // Velocity Lane Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (showVelocityLane) SanwolfCyan.copy(alpha = 0.22f) else SanwolfPanelElevated)
                        .border(1.dp, if (showVelocityLane) SanwolfCyan else SanwolfPanelBorder, RoundedCornerShape(4.dp))
                        .clickable { showVelocityLane = !showVelocityLane }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("velocity_lane_toggle")
                ) {
                    Text(
                        text = if (showVelocityLane) "VEL LANE" else "VEL LANE OFF",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (showVelocityLane) SanwolfCyan else SanwolfTextMuted
                    )
                }

                // Snap-to-Grid Toggle & Division Selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSnapToGrid) SanwolfPanelElevated else SanwolfBlack.copy(alpha = 0.5f))
                        .border(1.dp, if (isSnapToGrid) SanwolfCyan else SanwolfPanelBorder, RoundedCornerShape(4.dp))
                ) {
                    // Toggle SNAP ON / OFF
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            .background(if (isSnapToGrid) SanwolfCyan.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable { isSnapToGrid = !isSnapToGrid }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .testTag("snap_to_grid_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SNAP",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = if (isSnapToGrid) SanwolfCyan else SanwolfTextMuted
                            )
                            Text(
                                text = if (isSnapToGrid) "ON" else "OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSnapToGrid) SanwolfLime else SanwolfTextMuted
                            )
                        }
                    }

                    // Division selector dropdown
                    Box {
                        Box(
                            modifier = Modifier
                                .clickable { showSnapMenu = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("snap_division_selector")
                        ) {
                            Text(
                                text = when (snapDivision) {
                                    0.125 -> "1/32"
                                    0.25 -> "1/16"
                                    0.5 -> "1/8"
                                    1.0 -> "1/4"
                                    2.0 -> "1/2"
                                    4.0 -> "1 BAR"
                                    1.0 / 3.0 -> "1/8T"
                                    1.0 / 6.0 -> "1/16T"
                                    else -> "${snapDivision}b"
                                },
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isSnapToGrid) SanwolfGold else SanwolfTextMuted
                            )
                        }

                        DropdownMenu(
                            expanded = showSnapMenu,
                            onDismissRequest = { showSnapMenu = false },
                            modifier = Modifier.background(SanwolfPanelElevated)
                        ) {
                            listOf(
                                "1/32 Beat (Fastest)" to 0.125,
                                "1/16 Beat (Standard 16th)" to 0.25,
                                "1/8 Beat (8th Note)" to 0.5,
                                "1/4 Beat (Quarter Note)" to 1.0,
                                "1/2 Beat (Half Note)" to 2.0,
                                "1 Whole Bar (4 Beats)" to 4.0,
                                "1/8 Triplet (3:2)" to (1.0 / 3.0),
                                "1/16 Triplet (6:4)" to (1.0 / 6.0)
                            ).forEach { (label, div) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = SanwolfTextPrimary, fontSize = 11.sp) },
                                    onClick = {
                                        snapDivision = div
                                        isSnapToGrid = true
                                        showSnapMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // --- Swing & Quantize Intensity Groove Controls ---
                // 1. Swing Slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, if (swingAmount > 0f) SanwolfGold else SanwolfPanelBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SWING: ${(swingAmount * 100).toInt()}%",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (swingAmount > 0f) SanwolfGold else SanwolfTextMuted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Slider(
                        value = swingAmount,
                        onValueChange = {
                            swingAmount = it
                            onSwingChange(it)
                        },
                        valueRange = 0f..0.75f,
                        modifier = Modifier
                            .width(68.dp)
                            .height(24.dp)
                            .testTag("piano_roll_swing_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = SanwolfGold,
                            activeTrackColor = SanwolfGold,
                            inactiveTrackColor = SanwolfPanelBorder
                        )
                    )
                }

                // 2. Quantize Intensity Slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Q INTENSITY: ${(quantizeIntensity * 100).toInt()}%",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Slider(
                        value = quantizeIntensity,
                        onValueChange = { quantizeIntensity = it },
                        valueRange = 0f..1.0f,
                        modifier = Modifier
                            .width(68.dp)
                            .height(24.dp)
                            .testTag("piano_roll_quantize_intensity_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = SanwolfCyan,
                            activeTrackColor = SanwolfCyan,
                            inactiveTrackColor = SanwolfPanelBorder
                        )
                    )
                }

                // 3. Apply Groove & Presets Trigger
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfLime.copy(alpha = 0.15f))
                        .border(1.dp, SanwolfLime, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .clickable {
                                val count = applyQuantizeAndSwing(
                                    notes = track.notes,
                                    division = snapDivision,
                                    intensity = quantizeIntensity,
                                    swing = swingAmount,
                                    totalBeats = totalBeats
                                )
                                localRevision++
                                grooveFeedbackText = "Groove Applied: ${(quantizeIntensity * 100).toInt()}% Q / ${(swingAmount * 100).toInt()}% Swing to $count notes"
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .testTag("apply_groove_quantize_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Apply Groove",
                                tint = SanwolfLime,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "APPLY GROOVE",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = SanwolfLime
                            )
                        }
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 0.5.dp,
                                    color = SanwolfLime.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                )
                                .clickable { showGrooveMenu = true }
                                .padding(horizontal = 5.dp, vertical = 4.dp)
                                .testTag("groove_presets_dropdown")
                        ) {
                            Text(
                                text = "PRESETS ▾",
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = SanwolfLime
                            )
                        }

                        DropdownMenu(
                            expanded = showGrooveMenu,
                            onDismissRequest = { showGrooveMenu = false },
                            modifier = Modifier.background(SanwolfPanelElevated)
                        ) {
                            listOf(
                                Triple("Straight Tight (0% Swing, 100% Q)", 0.0f, 1.0f),
                                Triple("Subtle Human (15% Swing, 80% Q)", 0.15f, 0.80f),
                                Triple("Classic MPC 60 (54% Swing, 85% Q)", 0.54f, 0.85f),
                                Triple("Heavy Triplet Shuffle (66% Swing, 100% Q)", 0.66f, 1.0f),
                                Triple("Loose Laidback (35% Swing, 50% Q)", 0.35f, 0.50f)
                            ).forEach { (label, sw, q) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(label, color = SanwolfTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("Swing: ${(sw * 100).toInt()}% • Intensity: ${(q * 100).toInt()}%", color = SanwolfTextMuted, fontSize = 9.sp)
                                        }
                                    },
                                    onClick = {
                                        swingAmount = sw
                                        quantizeIntensity = q
                                        onSwingChange(sw)
                                        showGrooveMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Note Length Selector
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SanwolfPanelElevated)
                            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                            .clickable { showLengthMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LEN: ${when (selectedNoteLength) {
                                0.25 -> "1/16"
                                0.5 -> "1/8"
                                1.0 -> "1/4"
                                2.0 -> "1/2"
                                4.0 -> "1 BAR"
                                else -> "${selectedNoteLength}b"
                            }}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfCyan
                        )
                    }
                    DropdownMenu(
                        expanded = showLengthMenu,
                        onDismissRequest = { showLengthMenu = false },
                        modifier = Modifier.background(SanwolfPanelElevated)
                    ) {
                        listOf(
                            "1/16 Note (Fast)" to 0.25,
                            "1/8 Note" to 0.5,
                            "1/4 Note (1 Beat)" to 1.0,
                            "1/2 Note (2 Beats)" to 2.0,
                            "1 Whole Bar" to 4.0
                        ).forEach { (label, len) ->
                            DropdownMenuItem(
                                text = { Text(label, color = SanwolfTextPrimary, fontSize = 11.sp) },
                                onClick = {
                                    selectedNoteLength = len
                                    showLengthMenu = false
                                }
                            )
                        }
                    }
                }

                // Scale Lock Dropdown
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SanwolfPanelElevated)
                            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                            .clickable { showScaleMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Scale: $selectedScale",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = SanwolfLime
                        )
                    }

                    DropdownMenu(
                        expanded = showScaleMenu,
                        onDismissRequest = { showScaleMenu = false },
                        modifier = Modifier.background(SanwolfPanelElevated)
                    ) {
                        listOf("Natural Minor", "Major / Ionian", "Dorian", "Phrygian", "Pentatonic Minor", "Blues").forEach { sc ->
                            DropdownMenuItem(
                                text = { Text(sc, color = SanwolfTextPrimary, fontSize = 11.sp) },
                                onClick = {
                                    selectedScale = sc
                                    onQuantizeNotes(sc)
                                    showScaleMenu = false
                                }
                            )
                        }
                    }
                }

                // Chord Stamper Dropdown
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SanwolfPanelElevated)
                            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                            .clickable { showChordMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Stamp Chord",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = SanwolfCyan
                        )
                    }

                    DropdownMenu(
                        expanded = showChordMenu,
                        onDismissRequest = { showChordMenu = false },
                        modifier = Modifier.background(SanwolfPanelElevated)
                    ) {
                        listOf("Minor 7th", "Major 9th", "Suspended 4th", "Wolf Dark 5th", "Amapiano 9th", "Afro House Triad").forEach { chord ->
                            DropdownMenuItem(
                                text = { Text(chord, color = SanwolfTextPrimary, fontSize = 11.sp) },
                                onClick = {
                                    onStampChord(chord)
                                    showChordMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${track.notes.size} NOTES",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SanwolfTextSecondary
                )

                // Export Sequence as MIDI Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfGold.copy(alpha = 0.22f))
                        .border(1.dp, SanwolfGold, RoundedCornerShape(4.dp))
                        .clickable { showExportDialog = true }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .testTag("piano_roll_export_midi_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = "Export MIDI Sequence",
                            tint = SanwolfGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "EXPORT MIDI",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = SanwolfGold
                        )
                    }
                }

                // Clear Notes Button
                IconButton(onClick = onClearNotes, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Notes",
                        tint = SanwolfMagenta,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Groove & Quantize Feedback Banner
        AnimatedVisibility(
            visible = grooveFeedbackText != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            grooveFeedbackText?.let { feedback ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfBlack)
                        .border(1.dp, SanwolfLime.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SanwolfLime,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = feedback,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfLime
                        )
                    }
                    Text(
                        text = "✕",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = SanwolfTextMuted,
                        modifier = Modifier.clickable { grooveFeedbackText = null }.padding(horizontal = 4.dp)
                    )
                }
                LaunchedEffect(feedback) {
                    kotlinx.coroutines.delay(2800)
                    grooveFeedbackText = null
                }
            }
        }

        // Dedicated Velocity & Note Inspector Bar (shown after selecting / tapping any note)
        AnimatedVisibility(
            visible = selectedNote != null && track.notes.contains(selectedNote),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            selectedNote?.let { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pitchName = noteNames[note.pitch % 12] + ((note.pitch / 12) - 1)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SanwolfCyan.copy(alpha = 0.2f))
                            .border(0.5.dp, SanwolfCyan, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$pitchName @ ${String.format(java.util.Locale.US, "%.2f", note.startBeat)}b",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfCyan
                        )
                    }

                    val velPercent = (note.velocity * 100).toInt()
                    val midiVel = (note.velocity * 127).toInt().coerceIn(1, 127)
                    Text(
                        text = "VEL: $velPercent% ($midiVel)",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfGold
                    )

                    Slider(
                        value = note.velocity,
                        onValueChange = { newVel ->
                            note.velocity = newVel
                            onUpdateVelocity(note, newVel)
                            onPreviewPitchWithVelocity(note.pitch, newVel)
                            localRevision++
                        },
                        valueRange = 0.05f..1.0f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("note_velocity_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = SanwolfGold,
                            activeTrackColor = SanwolfCyan,
                            inactiveTrackColor = SanwolfPanelBorder
                        )
                    )

                    listOf(
                        "p" to 0.35f,
                        "mf" to 0.70f,
                        "f" to 0.85f,
                        "ff" to 1.0f
                    ).forEach { (label, presetVel) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (kotlin.math.abs(note.velocity - presetVel) < 0.06f) SanwolfGold else SanwolfPanel)
                                .clickable {
                                    note.velocity = presetVel
                                    onUpdateVelocity(note, presetVel)
                                    onPreviewPitchWithVelocity(note.pitch, presetVel)
                                    localRevision++
                                }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (kotlin.math.abs(note.velocity - presetVel) < 0.06f) SanwolfBlack else SanwolfTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            onDeleteNote(note.pitch, note.startBeat)
                            selectedNote = null
                            localRevision++
                        },
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("delete_selected_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = SanwolfMagenta,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = { selectedNote = null },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Deselect Note",
                            tint = SanwolfTextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- Keyboard (Left) + Interactive Note Grid (Right) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
        ) {
            // Left Piano Keys Column
            val keyHeight = 22.dp
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .verticalScroll(verticalScrollState)
                    .background(SanwolfPanel)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, SanwolfPanelBorder))
            ) {
                pitches.forEach { pitch ->
                    val noteName = noteNames[pitch % 12]
                    val isBlack = noteName.contains("#")
                    val octaveNum = (pitch / 12) - 1

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(keyHeight)
                            .background(if (isBlack) Color(0xFF141416) else Color(0xFF2C2E35))
                            .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF0F0F12)))
                            .clickable { onPreviewPitch(pitch) }
                            .padding(end = 6.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "$noteName$octaveNum",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isBlack) FontWeight.Normal else FontWeight.Bold,
                            color = if (isBlack) SanwolfTextMuted else SanwolfTextPrimary
                        )
                    }
                }
            }

            // Right Grid with Notes Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(gridScrollState)
            ) {
                val gridWidth = beatWidth * totalBeats
                val totalHeightPx = keyHeight * pitches.size

                Canvas(
                    modifier = Modifier
                        .width(gridWidth)
                        .height(totalHeightPx)
                        .background(SanwolfBlack)
                        .pointerInput(pitches, track.id, isSnapToGrid, snapDivision, toolMode, selectedNoteLength) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startOffset = down.position
                                val beatPx = beatWidth.toPx()
                                val keyPx = keyHeight.toPx()

                                val rawStartBeat = (startOffset.x / beatPx).toDouble()
                                val startPitchIndex = (startOffset.y / keyPx).toInt().coerceIn(0, pitches.size - 1)
                                val startPitch = pitches[startPitchIndex]

                                val targetNote = track.notes.find { note ->
                                    note.pitch == startPitch &&
                                    rawStartBeat >= (note.startBeat - 0.15) &&
                                    rawStartBeat < (note.startBeat + note.lengthBeats + 0.15)
                                }

                                val noteY = if (targetNote != null) (pitches.indexOf(targetNote.pitch) * keyPx) else 0f
                                val isBottomHalfOfNote = targetNote != null && (startOffset.y - noteY) > (keyPx * 0.45f)

                                var totalDragDistance = 0f
                                var isVelocityDragging = false
                                var isMovingNote = false
                                var startVelocity = targetNote?.velocity ?: 0.85f
                                var currentDragBeat = targetNote?.startBeat ?: snapBeat(rawStartBeat)
                                var currentDragPitch = targetNote?.pitch ?: startPitch

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        // Pointer released
                                        if (!isVelocityDragging && !isMovingNote) {
                                            // Handle Tap (Add or Select / Delete)
                                            if (toolMode == PianoRollToolMode.ERASE) {
                                                if (targetNote != null) {
                                                    onDeleteNote(targetNote.pitch, targetNote.startBeat)
                                                    if (selectedNote === targetNote) selectedNote = null
                                                    localRevision++
                                                }
                                            } else {
                                                if (targetNote != null) {
                                                    // Tap on existing note selects it and plays preview at its velocity
                                                    selectedNote = targetNote
                                                    onPreviewPitchWithVelocity(targetNote.pitch, targetNote.velocity)
                                                    localRevision++
                                                } else {
                                                    val placedBeat = snapBeat(rawStartBeat)
                                                    val newNote = MidiNote(
                                                        pitch = startPitch,
                                                        startBeat = placedBeat,
                                                        lengthBeats = selectedNoteLength,
                                                        velocity = 0.85f
                                                    )
                                                    onAddNote(startPitch, placedBeat, selectedNoteLength)
                                                    onPreviewPitchWithVelocity(startPitch, 0.85f)
                                                    selectedNote = newNote
                                                    localRevision++
                                                }
                                            }
                                        } else if (isMovingNote) {
                                            // Drag note completed: commit note position
                                            if (targetNote != null && toolMode != PianoRollToolMode.ERASE) {
                                                onMoveNote(targetNote, currentDragPitch, currentDragBeat)
                                                localRevision++
                                            }
                                        } else if (isVelocityDragging && targetNote != null) {
                                            // Velocity drag completed: play feedback preview
                                            onPreviewPitchWithVelocity(targetNote.pitch, targetNote.velocity)
                                            localRevision++
                                        }
                                        draggingNote = null
                                        activeVelocityDragNote = null
                                        break
                                    }

                                    val dragDelta = change.position - change.previousPosition
                                    totalDragDistance += dragDelta.getDistance()

                                    if (!isVelocityDragging && !isMovingNote && totalDragDistance > 10f && targetNote != null && toolMode != PianoRollToolMode.ERASE) {
                                        val deltaY = Math.abs(change.position.y - startOffset.y)
                                        val deltaX = Math.abs(change.position.x - startOffset.x)

                                        if (toolMode == PianoRollToolMode.VELOCITY || isBottomHalfOfNote || (deltaY > 12f && deltaX < 8f)) {
                                            isVelocityDragging = true
                                            activeVelocityDragNote = targetNote
                                            selectedNote = targetNote
                                            startVelocity = targetNote.velocity
                                        } else {
                                            isMovingNote = true
                                            draggingNote = targetNote
                                            selectedNote = targetNote
                                            dragCurrentBeat = targetNote.startBeat
                                            dragCurrentPitch = targetNote.pitch
                                        }
                                    }

                                    if (isVelocityDragging && targetNote != null) {
                                        change.consume()
                                        // Vertical Drag: UP increases velocity (+), DOWN decreases (-)
                                        val totalDeltaY = -(change.position.y - startOffset.y)
                                        val newVel = (startVelocity + (totalDeltaY / 120f)).coerceIn(0.05f, 1.0f)
                                        targetNote.velocity = newVel
                                        activeDragVelocityValue = newVel
                                        onUpdateVelocity(targetNote, newVel)
                                        localRevision++
                                    } else if (isMovingNote && targetNote != null) {
                                        change.consume()
                                        val rawCurrentBeat = (change.position.x / beatPx).toDouble()
                                        val currentPitchIdx = (change.position.y / keyPx).toInt().coerceIn(0, pitches.size - 1)
                                        val newPitch = pitches[currentPitchIdx]
                                        val newBeat = snapBeat(rawCurrentBeat)

                                        if (newPitch != currentDragPitch) {
                                            onPreviewPitchWithVelocity(newPitch, targetNote.velocity)
                                        }
                                        currentDragPitch = newPitch
                                        currentDragBeat = newBeat
                                        dragCurrentBeat = newBeat
                                        dragCurrentPitch = newPitch
                                        localRevision++
                                    }
                                }
                            }
                        }
                ) {
                    // Force Compose redraw on local revision or project version increment
                    val _invalidationKey = localRevision + projectVersion + track.notes.size
                    val beatPx = beatWidth.toPx()
                    val keyPx = keyHeight.toPx()

                    // Background grid rows
                    pitches.forEachIndexed { i, pitch ->
                        val isBlack = noteNames[pitch % 12].contains("#")
                        val isC = (pitch % 12) == 0
                        val y = i * keyPx
                        drawRect(
                            color = if (isC) Color(0xFF191B24) else if (isBlack) Color(0xFF0C0D10) else Color(0xFF121318),
                            topLeft = Offset(0f, y),
                            size = Size(size.width, keyPx)
                        )
                        drawLine(
                            color = if (isC) Color(0xFF33384B) else Color(0xFF1E2028),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = if (isC) 1.2f else 0.5f
                        )
                    }

                    // Dynamic Snap-to-Grid division subdivision lines
                    if (isSnapToGrid && snapDivision > 0.0) {
                        val subDiv = snapDivision
                        val subCount = (totalBeats / subDiv).toInt()
                        for (s in 0..subCount) {
                            val x = (s * subDiv * beatPx).toFloat()
                            val isFullBeat = ((s * subDiv) % 1.0) < 0.0001
                            if (!isFullBeat) {
                                drawLine(
                                    color = SanwolfCyan.copy(alpha = 0.16f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 0.75f
                                )
                            }
                        }
                    } else {
                        // Subtle 16th note subdivision grid lines in free/unaligned mode
                        for (s in 0 until (totalBeats * 4)) {
                            val x = s * (beatPx / 4f)
                            val isFullBeat = s % 4 == 0
                            if (!isFullBeat) {
                                drawLine(
                                    color = Color(0xFF16171E),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 0.5f
                                )
                            }
                        }
                    }

                    // Vertical full beat and bar lines
                    for (b in 0..totalBeats) {
                        val x = b * beatPx
                        val isBar = b % 4 == 0
                        drawLine(
                            color = if (isBar) SanwolfGold.copy(alpha = 0.45f) else Color(0xFF2C2F3A),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = if (isBar) 1.5f else 0.8f
                        )
                    }

                    // Draw Notes with Outer Accent Border & Velocity Level
                    val trackColor = Color(track.colorHex)
                    for (note in track.notes) {
                        val isBeingDragged = draggingNote === note
                        val isVelBeingDragged = activeVelocityDragNote === note
                        val isSelected = selectedNote === note
                        val effectiveBeat = if (isBeingDragged) dragCurrentBeat else note.startBeat
                        val effectivePitch = if (isBeingDragged) dragCurrentPitch else note.pitch

                        val pitchIdx = pitches.indexOf(effectivePitch)
                        if (pitchIdx >= 0) {
                            val x = (effectiveBeat * beatPx).toFloat()
                            val y = (pitchIdx * keyPx)
                            val noteW = maxOf(8f, (note.lengthBeats * beatPx).toFloat() - 2f)

                            // Note brightness / alpha based on note velocity
                            val velAlpha = (0.35f + (note.velocity * 0.60f)).coerceIn(0.35f, 0.95f)
                            val noteColor = if (isBeingDragged || isVelBeingDragged) {
                                SanwolfCyan
                            } else if (isSelected) {
                                SanwolfGold
                            } else {
                                trackColor
                            }

                            // Main note body
                            drawRoundRect(
                                color = noteColor.copy(alpha = velAlpha),
                                topLeft = Offset(x + 1f, y + 2f),
                                size = Size(noteW, keyPx - 4f),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            // Outer accent stroke (Selected note gets glowing border)
                            drawRoundRect(
                                color = if (isSelected) Color.White else if (isBeingDragged || isVelBeingDragged) SanwolfCyan else SanwolfGold.copy(alpha = 0.8f),
                                topLeft = Offset(x + 1f, y + 2f),
                                size = Size(noteW, keyPx - 4f),
                                cornerRadius = CornerRadius(4f, 4f),
                                style = Stroke(width = if (isSelected) 2.5f else if (isBeingDragged || isVelBeingDragged) 2.0f else 1.2f)
                            )
                            // Inner high-contrast velocity level indicator bar
                            val velBarH = 3.5f
                            val velFillW = ((noteW - 4f) * note.velocity).coerceAtLeast(3f)
                            drawRoundRect(
                                color = if (isSelected) SanwolfCyan else Color.White.copy(alpha = 0.85f),
                                topLeft = Offset(x + 2f, y + keyPx - 6f),
                                size = Size(velFillW, velBarH),
                                cornerRadius = CornerRadius(1.5f, 1.5f)
                            )
                        }
                    }

                    // Floating Velocity HUD Tooltip when dragging velocity
                    activeVelocityDragNote?.let { note ->
                        val pitchIdx = pitches.indexOf(note.pitch)
                        if (pitchIdx >= 0) {
                            val x = (note.startBeat * beatPx).toFloat()
                            val y = (pitchIdx * keyPx)
                            val hudW = 68f
                            val hudH = 18f
                            val hudX = (x + 2f).coerceIn(4f, size.width - hudW - 4f)
                            val hudY = (y - hudH - 6f).coerceAtLeast(4f)

                            drawRoundRect(
                                color = Color(0xFF0F1117),
                                topLeft = Offset(hudX, hudY),
                                size = Size(hudW, hudH),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            drawRoundRect(
                                color = SanwolfCyan,
                                topLeft = Offset(hudX, hudY),
                                size = Size(hudW, hudH),
                                cornerRadius = CornerRadius(4f, 4f),
                                style = Stroke(width = 1.2f)
                            )
                            drawRect(
                                color = SanwolfCyan,
                                topLeft = Offset(hudX + 4f, hudY + hudH - 4f),
                                size = Size((hudW - 8f) * note.velocity, 2f)
                            )
                        }
                    }

                    // Dragging snap vertical guide line
                    if (draggingNote != null) {
                        val guideX = (dragCurrentBeat * beatPx).toFloat()
                        drawLine(
                            color = SanwolfCyan,
                            start = Offset(guideX, 0f),
                            end = Offset(guideX, size.height),
                            strokeWidth = 2f
                        )
                    }

                    // Playback Cursor Line with Glow
                    val cursorX = (currentBeat * beatPx).toFloat()
                    drawLine(
                        color = SanwolfCyan.copy(alpha = 0.35f),
                        start = Offset(cursorX, 0f),
                        end = Offset(cursorX, size.height),
                        strokeWidth = 5f
                    )
                    drawLine(
                        color = SanwolfCyan,
                        start = Offset(cursorX, 0f),
                        end = Offset(cursorX, size.height),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // --- Bottom Velocity Lane (Collapsible & Synchronized with Grid Scroll) ---
        if (showVelocityLane) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SanwolfPanel)
                    .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
            ) {
                // Left Header Label
                Column(
                    modifier = Modifier
                        .width(60.dp)
                        .fillMaxHeight()
                        .background(SanwolfPanelElevated)
                        .border(androidx.compose.foundation.BorderStroke(1.dp, SanwolfPanelBorder))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VELOCITY",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = SanwolfCyan,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "0 - 127",
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SanwolfTextMuted
                    )
                }

                // Right Velocity Stems Canvas (Scrolled synchronously with gridScrollState)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(gridScrollState)
                ) {
                    val laneWidth = beatWidth * totalBeats
                    Canvas(
                        modifier = Modifier
                            .width(laneWidth)
                            .fillMaxHeight()
                            .background(SanwolfBlack)
                            .pointerInput(track.id, localRevision) {
                                detectTapGestures { offset ->
                                    val beatPx = beatWidth.toPx()
                                    val tapBeat = (offset.x / beatPx).toDouble()
                                    val hitNote = track.notes.minByOrNull { kotlin.math.abs(it.startBeat - tapBeat) }
                                    if (hitNote != null && kotlin.math.abs(hitNote.startBeat - tapBeat) < 0.6) {
                                        val laneH = size.height
                                        val newVel = ((laneH - offset.y) / laneH).coerceIn(0.05f, 1.0f)
                                        hitNote.velocity = newVel
                                        selectedNote = hitNote
                                        onUpdateVelocity(hitNote, newVel)
                                        onPreviewPitchWithVelocity(hitNote.pitch, newVel)
                                        localRevision++
                                    }
                                }
                            }
                    ) {
                        val beatPx = beatWidth.toPx()
                        val laneH = size.height

                        // Horizontal guideline levels (25%, 50%, 75%, 100%)
                        listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { fraction ->
                            val y = laneH * (1f - fraction)
                            drawLine(
                                color = Color(0xFF1E2028),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 0.6f
                            )
                        }

                        // Vertical beat and bar divider lines
                        for (b in 0..totalBeats) {
                            val x = b * beatPx
                            val isBar = b % 4 == 0
                            drawLine(
                                color = if (isBar) SanwolfGold.copy(alpha = 0.25f) else Color(0xFF1C1E26),
                                start = Offset(x, 0f),
                                end = Offset(x, laneH),
                                strokeWidth = if (isBar) 1.2f else 0.5f
                            )
                        }

                        // Velocity stalks for all notes
                        for (note in track.notes) {
                            val isSelected = selectedNote === note
                            val x = (note.startBeat * beatPx).toFloat() + 4f
                            val stemTopY = laneH * (1f - note.velocity)

                            drawLine(
                                color = if (isSelected) SanwolfGold else SanwolfCyan.copy(alpha = 0.85f),
                                start = Offset(x, laneH),
                                end = Offset(x, stemTopY),
                                strokeWidth = if (isSelected) 3.0f else 2.0f
                            )

                            drawCircle(
                                color = if (isSelected) Color.White else SanwolfCyan,
                                radius = if (isSelected) 5.0f else 3.8f,
                                center = Offset(x, stemTopY)
                            )
                        }
                    }
                }
            }
        }

        // Export Piano Roll Sequence as MIDI Dialog
        if (showExportDialog) {
            ExportPianoRollSequenceDialog(
                track = track,
                bpm = bpm,
                onDismiss = { showExportDialog = false }
            )
        }
    }
}

@Composable
fun ExportPianoRollSequenceDialog(
    track: TrackData,
    bpm: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var savedSuccess by remember { mutableStateOf(false) }

    val noteCount = track.notes.size
    val stepCount = if (track.notes.isEmpty() && track.type == TrackType.DRUM_MACHINE) {
        track.steps.count { it }
    } else 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.78f)
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
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = "Export MIDI Icon",
                            tint = SanwolfGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "EXPORT PIANO ROLL MIDI",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = SanwolfGold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Standard MIDI File (SMF Type 1 • .mid) • Cross-DAW Compatibility",
                                fontSize = 10.sp,
                                color = SanwolfTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_export_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sequence details card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SanwolfCyan.copy(alpha = 0.25f))
                                    .border(1.dp, SanwolfCyan, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = track.name.uppercase(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = SanwolfCyan
                                )
                            }

                            Text(
                                text = if (track.type == TrackType.DRUM_MACHINE) "DRUM RACK • MIDI CH 10" else "SYNTH INSTRUMENT • MIDI CH 1",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SanwolfTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "NOTES IN SEQUENCE:",
                                fontSize = 10.sp,
                                color = SanwolfTextSecondary
                            )
                            Text(
                                text = if (noteCount > 0) "$noteCount Polyphonic Notes" else if (stepCount > 0) "$stepCount Drum Steps" else "Empty sequence (0 notes)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (noteCount > 0 || stepCount > 0) SanwolfLime else SanwolfMagenta
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TEMPO / TIME SIGNATURE:",
                                fontSize = 10.sp,
                                color = SanwolfTextSecondary
                            )
                            Text(
                                text = "$bpm BPM • 4/4 Time Signature",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = SanwolfTextPrimary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "VELOCITY & TIMING DYNAMICS:",
                                fontSize = 10.sp,
                                color = SanwolfTextSecondary
                            )
                            Text(
                                text = "Preserved 1:1 (MIDI 1–127 Values)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = SanwolfGold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "DAW COMPATIBILITY:",
                                fontSize = 10.sp,
                                color = SanwolfTextSecondary
                            )
                            Text(
                                text = "FL Studio, Ableton Live, Logic Pro, Reaper, Cubase, Bitwig",
                                fontSize = 9.sp,
                                color = SanwolfTextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status banner
                if (statusMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SanwolfBlack)
                            .border(1.dp, if (savedSuccess || exportedFile != null) SanwolfLime else SanwolfCyan, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SanwolfLime,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusMessage!!,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SanwolfLime
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isExporting = true
                            statusMessage = "Generating SMF Type 1 MIDI sequence file..."
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    MidiExporter.exportTrackSequenceToMidi(context, track, bpm)
                                }
                                exportedFile = file
                                isExporting = false
                                statusMessage = "Exported '${file.name}' (${file.length()} bytes) • Opening share sheet..."
                                val shareIntent = MidiExporter.createShareIntent(
                                    context,
                                    file,
                                    "Share ${track.name} MIDI Sequence"
                                )
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share MIDI Sequence to DAW / Files")
                                )
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("export_share_midi_action"),
                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(color = SanwolfBlack, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EXPORTING MIDI...", color = SanwolfBlack, fontWeight = FontWeight.Black)
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, tint = SanwolfBlack, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SHARE MIDI FILE (.MID) TO OTHER DAWS",
                                color = SanwolfBlack,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val file = exportedFile ?: withContext(Dispatchers.IO) {
                                    MidiExporter.exportTrackSequenceToMidi(context, track, bpm)
                                }
                                exportedFile = file
                                val uri = withContext(Dispatchers.IO) {
                                    MidiExporter.saveMidiToDownloads(context, file)
                                }
                                savedSuccess = true
                                statusMessage = "Saved '${file.name}' to Downloads/SanwolfDaw folder!"
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("save_midi_downloads_action"),
                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfPanelElevated)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = SanwolfCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAVE TO DOWNLOADS FOLDER",
                            color = SanwolfCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quantizes MIDI notes with configurable intensity (0%–100%) and swing amount (0%–75%).
 * - intensity = 1.0f: Complete hard snap to target grid.
 * - intensity < 1.0f: Partial humanized shift towards grid, preserving performance nuances.
 * - swing: Delays the off-beat subdivision steps (e.g. the 2nd and 4th sixteenth notes) to create shuffle / bounce.
 */
fun applyQuantizeAndSwing(
    notes: MutableList<MidiNote>,
    division: Double = 0.25,
    intensity: Float = 1.0f,
    swing: Float = 0.0f,
    totalBeats: Int = 16
): Int {
    if (notes.isEmpty() || intensity <= 0f) return 0
    var modifiedCount = 0
    val div = if (division > 0.0) division else 0.25

    for (note in notes) {
        val originalBeat = note.startBeat
        // 1. Identify nearest grid beat
        val nearestStep = Math.round(originalBeat / div).toLong()
        val nearestGrid = nearestStep * div

        // 2. Off-beat swing offset (odd steps are shifted forward)
        val isOffBeat = (nearestStep % 2L != 0L)
        val swingOffset = if (isOffBeat) (swing * div * 0.5).toDouble() else 0.0
        val targetBeat = (nearestGrid + swingOffset).coerceIn(0.0, (totalBeats - note.lengthBeats).coerceAtLeast(0.0))

        // 3. Interpolate using quantize intensity
        val newBeat = originalBeat + (targetBeat - originalBeat) * intensity.toDouble()
        val roundedBeat = (Math.round(newBeat * 1000.0) / 1000.0).coerceIn(0.0, (totalBeats - note.lengthBeats).coerceAtLeast(0.0))

        if (kotlin.math.abs(roundedBeat - originalBeat) > 0.0001) {
            note.startBeat = roundedBeat
            modifiedCount++
        }
    }
    return modifiedCount
}
