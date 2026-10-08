package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.model.TrackType
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfOrange
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary

@Composable
fun StepSequencerView(
    project: ProjectData,
    currentStep: Int,
    isPlaying: Boolean,
    onStepToggle: (trackIndex: Int, stepIndex: Int) -> Unit,
    onStepVelocityChange: (trackIndex: Int, stepIndex: Int, velocity: Float) -> Unit,
    onPolyStepCountChange: (trackIndex: Int, count: Int) -> Unit,
    onPreviewTrack: (trackIndex: Int) -> Unit,
    onApplyPresetPattern: (presetName: String) -> Unit,
    onSwingChange: (Float) -> Unit,
    onTriggerPad: (name: String, velocity: Float) -> Unit,
    onAddTrack: ((TrackType, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isPadMode by remember { mutableStateOf(false) }
    // 0: Page 1 (1-16), 1: Page 2 (17-32), 2: Page 3 (33-48), 3: Page 4 (49-64), -1: Full Horizontal Scroll
    var activePage by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SanwolfBlack)
            .padding(8.dp)
    ) {
        // --- Top Control Bar: Mode Toggle, Page Switcher (20+ Steps), Presets, Swing ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanel)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selector Switch for Sequencer vs Pads
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfBlack)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (!isPadMode) SanwolfGold else Color.Transparent)
                            .clickable { isPadMode = false }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("toggle_sequencer_grid")
                    ) {
                        Text(
                            text = "SEQUENCER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (!isPadMode) SanwolfBlack else SanwolfTextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isPadMode) SanwolfGold else Color.Transparent)
                            .clickable { isPadMode = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("toggle_drum_pads")
                    ) {
                        Text(
                            text = "DRUM PADS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isPadMode) SanwolfBlack else SanwolfTextMuted
                        )
                    }
                }

                if (!isPadMode) {
                    // Page Navigation Buttons (Supports 20+ Steps up to 64 steps)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SanwolfBlack.copy(alpha = 0.6f))
                            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
                            .padding(2.dp)
                    ) {
                        listOf(
                            0 to "1–16",
                            1 to "17–32",
                            2 to "33–48",
                            3 to "49–64",
                            -1 to "ALL 64 ▶"
                        ).forEach { (pageIdx, label) ->
                            val isSelected = activePage == pageIdx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) SanwolfCyan else Color.Transparent)
                                    .clickable { activePage = pageIdx }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected) SanwolfBlack else SanwolfTextSecondary
                                )
                            }
                        }
                    }

                    // 20+ Steps Quick Presets
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 4.dp)
                    ) {
                        SequencerPresetChip("Trap 32s") { onApplyPresetPattern("Trap 808 (32s)") }
                        SequencerPresetChip("Amapiano 32s") { onApplyPresetPattern("Amapiano (32s)") }
                        SequencerPresetChip("Polyrhythm 24s") { onApplyPresetPattern("Polyrhythm (24s)") }
                        SequencerPresetChip("Afrobeat 24s") { onApplyPresetPattern("Afrobeat (24s)") }
                        SequencerPresetChip("Techno 32s") { onApplyPresetPattern("Techno (32s)") }
                        SequencerPresetChip("Boom Bap 16s") { onApplyPresetPattern("Boom Bap (16s)") }
                    }
                }
            }

            // Swing Slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SWING: ${(project.swing * 100).toInt()}%",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SanwolfCyan
                )
                Spacer(modifier = Modifier.width(4.dp))
                Slider(
                    value = project.swing,
                    onValueChange = onSwingChange,
                    valueRange = 0f..0.5f,
                    modifier = Modifier.width(75.dp).height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = SanwolfCyan,
                        activeTrackColor = SanwolfCyan,
                        inactiveTrackColor = SanwolfPanelBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- View Content Switching ---
        if (isPadMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SanwolfPanel)
                    .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                DrumPadView(
                    onTriggerPad = onTriggerPad,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // --- Channel Rows ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(project.tracks) { trackIndex, track ->
                    SequencerTrackRow(
                        track = track,
                        trackIndex = trackIndex,
                        currentStep = currentStep,
                        isPlaying = isPlaying,
                        activePage = activePage,
                        onStepToggle = { step -> onStepToggle(trackIndex, step) },
                        onPolyStepCountChange = { count -> onPolyStepCountChange(trackIndex, count) },
                        onPreviewSound = { onPreviewTrack(trackIndex) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Bottom Quick Track Adders
                if (onAddTrack != null) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickAddTrackChip("+ 808 Sub Kick", SanwolfOrange) {
                                onAddTrack(TrackType.DRUM_MACHINE, "808 Sub Kick")
                            }
                            QuickAddTrackChip("+ Snare Trap", SanwolfMagenta) {
                                onAddTrack(TrackType.DRUM_MACHINE, "Snare Trap Crack")
                            }
                            QuickAddTrackChip("+ Closed Hi-Hat", SanwolfLime) {
                                onAddTrack(TrackType.DRUM_MACHINE, "Closed Hi-Hat")
                            }
                            QuickAddTrackChip("+ Djembe / Perc", SanwolfGold) {
                                onAddTrack(TrackType.DRUM_MACHINE, "Djembe African Drum")
                            }
                            QuickAddTrackChip("+ Grand Piano", SanwolfCyan) {
                                onAddTrack(TrackType.SYNTH, "Grand Piano")
                            }
                            QuickAddTrackChip("+ 808 Sub Bass", SanwolfOrange) {
                                onAddTrack(TrackType.SYNTH, "808 Sub Boom")
                            }
                            QuickAddTrackChip("+ Violin Solo", SanwolfLime) {
                                onAddTrack(TrackType.SYNTH, "Violin Solo")
                            }
                            QuickAddTrackChip("+ Trumpet Solo", SanwolfGold) {
                                onAddTrack(TrackType.SYNTH, "Trumpet Solo")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SequencerTrackRow(
    track: TrackData,
    trackIndex: Int,
    currentStep: Int,
    isPlaying: Boolean,
    activePage: Int, // 0: 1-16, 1: 17-32, 2: 33-48, 3: 49-64, -1: Scroll All 64
    onStepToggle: (Int) -> Unit,
    onPolyStepCountChange: (Int) -> Unit,
    onPreviewSound: () -> Unit
) {
    val trackColor = Color(track.colorHex)
    var showPolyMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanel)
            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel Label & Sound Preview
        Row(
            modifier = Modifier
                .width(135.dp)
                .fillMaxHeight()
                .clickable { onPreviewSound() }
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(trackColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SanwolfTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "${track.stepCount} steps",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SanwolfTextMuted,
                    maxLines = 1
                )
            }
        }

        // Polyrhythm & Step Count Selector Button (Supports 20+ Steps: 20, 24, 28, 32, 48, 64)
        Box {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SanwolfPanelElevated)
                    .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                    .clickable { showPolyMenu = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${track.stepCount}s",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (track.stepCount > 16) SanwolfCyan else SanwolfGold
                )
            }

            DropdownMenu(
                expanded = showPolyMenu,
                onDismissRequest = { showPolyMenu = false },
                modifier = Modifier.background(SanwolfPanelElevated)
            ) {
                // 20+ steps options and classic polyrhythms
                listOf(64, 48, 32, 28, 24, 20, 16, 12, 8, 7, 5, 3).forEach { count ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "$count steps ${if (count > 16) "(20+ Steps Extended)" else if (count != 16) "(Polyrhythm)" else "(Standard)"}",
                                color = if (count == track.stepCount) SanwolfCyan else SanwolfTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (count == track.stepCount) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onPolyStepCountChange(count)
                            showPolyMenu = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Step Buttons Row (Supports Page Mode and Full Scroll Mode)
        if (activePage == -1) {
            // Full 64-step horizontal scrollable row
            val hScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(hScrollState),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val totalStepsToRender = maxOf(track.stepCount, 32).coerceAtMost(64)
                for (step in 0 until totalStepsToRender) {
                    StepButton(
                        step = step,
                        track = track,
                        trackIndex = trackIndex,
                        trackColor = trackColor,
                        currentStep = currentStep,
                        isPlaying = isPlaying,
                        onStepToggle = onStepToggle,
                        modifier = Modifier
                            .width(32.dp)
                            .height(34.dp)
                    )
                }
            }
        } else {
            // Paginated View: 16 buttons for the selected page
            val startStep = activePage * 16
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (offset in 0 until 16) {
                    val step = startStep + offset
                    StepButton(
                        step = step,
                        track = track,
                        trackIndex = trackIndex,
                        trackColor = trackColor,
                        currentStep = currentStep,
                        isPlaying = isPlaying,
                        onStepToggle = onStepToggle,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepButton(
    step: Int,
    track: TrackData,
    trackIndex: Int,
    trackColor: Color,
    currentStep: Int,
    isPlaying: Boolean,
    onStepToggle: (Int) -> Unit,
    modifier: Modifier
) {
    val isStepActive = track.steps.getOrElse(step) { false }
    val isCurrentPlayStep = isPlaying && (currentStep % track.stepCount.coerceAtLeast(1) == step)
    val isOutsidePolyCount = step >= track.stepCount

    val stepColor = when {
        isOutsidePolyCount -> SanwolfBlack.copy(alpha = 0.3f)
        isCurrentPlayStep && isStepActive -> Color.White
        isCurrentPlayStep -> SanwolfGold
        isStepActive -> trackColor
        step % 4 == 0 -> SanwolfPanelElevated
        else -> SanwolfBlack
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(stepColor)
            .border(
                width = 1.dp,
                color = when {
                    isCurrentPlayStep -> SanwolfGold
                    isOutsidePolyCount -> SanwolfPanelBorder.copy(alpha = 0.3f)
                    step % 4 == 0 -> SanwolfPanelBorder.copy(alpha = 0.8f)
                    else -> SanwolfPanelBorder
                },
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(enabled = !isOutsidePolyCount) {
                onStepToggle(step)
            }
            .testTag("seq_step_${trackIndex}_$step"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Step index number (1 to 64)
            Text(
                text = "${step + 1}",
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (step % 4 == 0) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isOutsidePolyCount -> SanwolfTextMuted.copy(alpha = 0.3f)
                    isCurrentPlayStep -> Color.Black
                    isStepActive -> Color.Black
                    step % 4 == 0 -> SanwolfTextSecondary
                    else -> SanwolfTextMuted
                }
            )
            if (isStepActive) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentPlayStep) Color.Black else Color.White)
                )
            }
        }
    }
}

@Composable
private fun SequencerPresetChip(name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SanwolfPanelElevated)
            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = name,
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = SanwolfTextSecondary
        )
    }
}

@Composable
private fun QuickAddTrackChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SanwolfPanelElevated)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
