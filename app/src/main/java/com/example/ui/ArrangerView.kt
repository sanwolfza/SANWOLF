package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AutomationLane
import com.example.model.AutomationPoint
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.model.TrackType
import com.example.model.VstPlugin
import android.widget.Toast
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.firebase.FirebaseDawManager
import kotlinx.coroutines.launch
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
fun ArrangerView(
    project: ProjectData,
    currentBeat: Double,
    selectedTrackIndex: Int,
    onSelectTrack: (Int) -> Unit,
    onEditInPianoRoll: (Int) -> Unit,
    onTrackVolumeChange: (Int, Float) -> Unit,
    onTrackPanChange: (Int, Float) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onDeleteTrack: (Int) -> Unit,
    onTimelineScrub: (Double) -> Unit,
    onSelectInstrumentForTrack: (Int) -> Unit = {},
    onOpenInstrumentBrowser: () -> Unit = {},
    onUpdateAutomation: () -> Unit = {},
    onMoveTrackUp: (Int) -> Unit = {},
    onMoveTrackDown: (Int) -> Unit = {},
    firebaseManager: FirebaseDawManager? = null,
    onLoadProject: (ProjectData) -> Unit = {},
    onProjectSaved: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dawFirebaseManager = remember(firebaseManager) { firebaseManager ?: FirebaseDawManager(context) }

    var showProjectManagementPanel by remember { mutableStateOf(false) }
    var expandedFxTrackIndex by remember { mutableStateOf<Int?>(null) }
    var expandedAutomationTrackIndex by remember { mutableStateOf<Int?>(null) }
    var showAddTrackMenu by remember { mutableStateOf(false) }
    var isSavingProject by remember { mutableStateOf(false) }

    val timelineScrollState = rememberScrollState()
    val safeSelectedTrackIndex = selectedTrackIndex.coerceIn(0, (project.tracks.size - 1).coerceAtLeast(0))
    val anySolo = project.tracks.any { it.solo }

    Box(modifier = modifier.fillMaxSize().background(SanwolfBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- Main Arrangement Workstation Rows ---
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

                // --- Left Panel: Track Control Headers (FLM / Ableton style) ---
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(SanwolfPanel)
                        .border(androidx.compose.foundation.BorderStroke(1.dp, SanwolfPanelBorder))
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(SanwolfPanelElevated)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TRACKS (${project.tracks.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        // Add Track Dropdown
                        Box {
                            IconButton(
                                onClick = { showAddTrackMenu = true },
                                modifier = Modifier.size(24.dp).testTag("add_track_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Track",
                                    tint = SanwolfGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showAddTrackMenu,
                                onDismissRequest = { showAddTrackMenu = false },
                                modifier = Modifier.background(SanwolfPanelElevated)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("🎹 Browse Instrument Catalog...", color = SanwolfGold, fontWeight = FontWeight.Black) },
                                    onClick = {
                                        onOpenInstrumentBrowser()
                                        showAddTrackMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("+ Melodic Synthesizer", color = SanwolfCyan) },
                                    onClick = {
                                        onAddTrack(TrackType.SYNTH)
                                        showAddTrackMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("+ 808 / Drum Kit", color = SanwolfOrange) },
                                    onClick = {
                                        onAddTrack(TrackType.DRUM_MACHINE)
                                        showAddTrackMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("+ Modular Synth Rack", color = SanwolfGold) },
                                    onClick = {
                                        onAddTrack(TrackType.MODULAR_SYNTH)
                                        showAddTrackMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("+ Audio Stem / Vocal", color = SanwolfMagenta) },
                                    onClick = {
                                        onAddTrack(TrackType.AUDIO_IMPORT)
                                        showAddTrackMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("+ Ambient Riser FX", color = SanwolfLime) },
                                    onClick = {
                                        onAddTrack(TrackType.RISER_TEXTURE)
                                        showAddTrackMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Track Headers List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(project.tracks) { index, track ->
                            TrackHeaderItem(
                                track = track,
                                isSelected = index == safeSelectedTrackIndex,
                                isFxExpanded = expandedFxTrackIndex == index,
                                isAutomationExpanded = expandedAutomationTrackIndex == index,
                                anySoloActive = anySolo,
                                onSelect = { onSelectTrack(index) },
                                onEditPianoRoll = { onEditInPianoRoll(index) },
                                onSelectInstrument = { onSelectInstrumentForTrack(index) },
                                onVolumeChange = { onTrackVolumeChange(index, it) },
                                onPanChange = { onTrackPanChange(index, it) },
                                onMuteToggle = { onToggleMute(index) },
                                onSoloToggle = { onToggleSolo(index) },
                                onToggleFx = {
                                    expandedFxTrackIndex = if (expandedFxTrackIndex == index) null else index
                                },
                                onToggleAutomation = {
                                    expandedAutomationTrackIndex = if (expandedAutomationTrackIndex == index) null else index
                                },
                                onMoveUp = { onMoveTrackUp(index) },
                                onMoveDown = { onMoveTrackDown(index) },
                                onDelete = { onDeleteTrack(index) }
                            )
                        }
                    }
                }

                // --- Right Panel: Arranger Timeline Grid ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(timelineScrollState)
                ) {
                    val totalBeats = 32 // 8 bars @ 4/4
                    val beatWidth = 56.dp
                    val timelineWidth = beatWidth * totalBeats

                    // 1. Timeline Bars Ruler Header
                    Box(
                        modifier = Modifier
                            .width(timelineWidth)
                            .height(34.dp)
                            .background(SanwolfPanelElevated)
                            .border(androidx.compose.foundation.BorderStroke(1.dp, SanwolfPanelBorder))
                            .clickable {
                                // Approximate scrub
                                onTimelineScrub(0.0)
                            }
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            for (bar in 1..8) {
                                Box(
                                    modifier = Modifier
                                        .width(beatWidth * 4)
                                        .fillMaxHeight()
                                        .border(androidx.compose.foundation.BorderStroke(0.5.dp, SanwolfPanelBorder))
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "BAR $bar",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = SanwolfTextSecondary
                                    )
                                }
                            }
                        }

                        // Playback Cursor Line Header
                        val cursorOffset = (currentBeat * beatWidth.value).dp
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(start = cursorOffset)
                                .width(2.dp)
                                .background(SanwolfGold)
                        )
                    }

                    // 2. Timeline Track Lanes
                    LazyColumn(
                        modifier = Modifier
                            .width(timelineWidth)
                            .fillMaxHeight()
                    ) {
                        itemsIndexed(project.tracks) { index, track ->
                            val isSelected = index == safeSelectedTrackIndex
                            val isFxExpanded = expandedFxTrackIndex == index
                            val isAutomationExpanded = expandedAutomationTrackIndex == index
                            val isSilenced = track.muted || (anySolo && !track.solo)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(androidx.compose.foundation.BorderStroke(0.5.dp, SanwolfPanelBorder))
                            ) {
                                // Main Clip Row
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .background(
                                            if (isSilenced) Color(0xFF0A0A0C)
                                            else if (isSelected) SanwolfPanelElevated
                                            else SanwolfBlack
                                        )
                                        .clickable { onSelectTrack(index) }
                                ) {
                                    // Subtle 16th beat grid lines
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val colWidthPx = beatWidth.toPx()
                                        for (b in 0..totalBeats) {
                                            val x = b * colWidthPx
                                            val isBarLine = b % 4 == 0
                                            drawLine(
                                                color = if (isBarLine) Color(0xFF282A30) else Color(0xFF141416),
                                                start = Offset(x, 0f),
                                                end = Offset(x, size.height),
                                                strokeWidth = if (isBarLine) 1.5f else 0.8f
                                            )
                                        }
                                    }

                                    // Render Track Clips
                                    TrackClipCanvas(track = track, beatWidth = beatWidth, isSilenced = isSilenced)

                                    // Status watermark badge if silenced or soloed
                                    if (isSilenced) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .padding(start = 12.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SanwolfBlack.copy(alpha = 0.85f))
                                                .border(
                                                    0.5.dp,
                                                    if (track.muted) SanwolfMagenta.copy(alpha = 0.7f) else SanwolfTextMuted.copy(alpha = 0.4f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (track.muted) "MUTED" else "SILENCED (SOLO ACTIVE)",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = if (track.muted) SanwolfMagenta else SanwolfTextMuted
                                            )
                                        }
                                    } else if (track.solo) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .padding(start = 12.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SanwolfGold.copy(alpha = 0.2f))
                                                .border(0.5.dp, SanwolfGold, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "SOLO ACTIVE",
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Black,
                                                color = SanwolfGold
                                            )
                                        }
                                    }

                                    // Playhead line passing through lane
                                    val playheadX = (currentBeat * beatWidth.value).dp
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(start = playheadX)
                                            .width(1.5.dp)
                                            .background(SanwolfGold)
                                    )
                                }

                                // Expanded VST Plugin Inspector Drawer
                                if (isFxExpanded) {
                                    VstPluginRackRow(track = track)
                                }

                                // Expanded Automation Curve Lane
                                if (isAutomationExpanded) {
                                    AutomationLaneRow(
                                        track = track,
                                        totalBeats = totalBeats,
                                        beatWidth = beatWidth,
                                        currentBeat = currentBeat,
                                        onAutomationChanged = onUpdateAutomation
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- In-Arranger Project Management Drawer / Panel Overlay ---
        if (showProjectManagementPanel) {
            ArrangerProjectManagerPanel(
                currentProject = project,
                firebaseManager = dawFirebaseManager,
                onLoadProject = { loadedProject ->
                    onLoadProject(loadedProject)
                    showProjectManagementPanel = false
                },
                onProjectSaved = {
                    onProjectSaved()
                },
                onClose = { showProjectManagementPanel = false }
            )
        }
    }
}

@Composable
private fun TrackHeaderItem(
    track: TrackData,
    isSelected: Boolean,
    isFxExpanded: Boolean,
    isAutomationExpanded: Boolean,
    anySoloActive: Boolean = false,
    onSelect: () -> Unit,
    onEditPianoRoll: () -> Unit,
    onSelectInstrument: () -> Unit = {},
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onToggleFx: () -> Unit,
    onToggleAutomation: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDelete: () -> Unit
) {
    val trackColor = Color(track.colorHex)
    val isSilencedBySolo = anySoloActive && !track.solo

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) SanwolfPanelElevated else SanwolfPanel)
            .border(androidx.compose.foundation.BorderStroke(0.5.dp, SanwolfPanelBorder))
            .clickable { onSelect() }
            .padding(6.dp)
    ) {
        // Track Name & Color Dot & Status Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(trackColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = track.name,
                    color = if (isSelected) SanwolfTextPrimary else SanwolfTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                // Solo / Mute status badge
                if (track.solo) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(SanwolfGold.copy(alpha = 0.25f))
                            .border(0.5.dp, SanwolfGold, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "SOLO",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = SanwolfGold
                        )
                    }
                } else if (track.muted) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(SanwolfMagenta.copy(alpha = 0.25f))
                            .border(0.5.dp, SanwolfMagenta, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "MUTED",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = SanwolfMagenta
                        )
                    }
                } else if (isSilencedBySolo) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(SanwolfBlack.copy(alpha = 0.6f))
                            .border(0.5.dp, SanwolfTextMuted.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "OFF",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = SanwolfTextMuted
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Piano Roll Edit button
                IconButton(onClick = onEditPianoRoll, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Edit in Piano Roll",
                        tint = SanwolfGold,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // FX toggle
                IconButton(onClick = onToggleFx, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "VST Rack",
                        tint = if (isFxExpanded) SanwolfGold else SanwolfTextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Automation toggle
                IconButton(onClick = onToggleAutomation, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Automation",
                        tint = if (isAutomationExpanded) SanwolfCyan else SanwolfTextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Move up
                IconButton(onClick = onMoveUp, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = SanwolfTextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Move down
                IconButton(onClick = onMoveDown, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = SanwolfTextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Delete track
                IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = SanwolfTextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Instrument Preset Badge / Selector Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(SanwolfBlack.copy(alpha = 0.5f))
                .border(0.5.dp, trackColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .clickable { onSelectInstrument() }
                .padding(horizontal = 5.dp, vertical = 2.dp)
                .testTag("track_instrument_button_${track.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "INST:",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SanwolfTextMuted
                )
                Text(
                    text = track.synthPresetName.ifBlank { track.name },
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = trackColor,
                    maxLines = 1
                )
            }
            Text(
                text = "▾",
                fontSize = 8.sp,
                color = SanwolfTextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Mute / Solo & Volume Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Button (M)
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (track.muted) SanwolfMagenta else SanwolfBlack.copy(alpha = 0.7f))
                    .border(
                        width = if (track.muted) 1.dp else 0.5.dp,
                        color = if (track.muted) SanwolfMagenta else SanwolfPanelBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onMuteToggle() }
                    .testTag("track_mute_button_${track.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = if (track.muted) Color.White else SanwolfTextSecondary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Solo Button (S)
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (track.solo) SanwolfGold else SanwolfBlack.copy(alpha = 0.7f))
                    .border(
                        width = if (track.solo) 1.dp else 0.5.dp,
                        color = if (track.solo) SanwolfGold else SanwolfPanelBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSoloToggle() }
                    .testTag("track_solo_button_${track.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "S",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = if (track.solo) SanwolfBlack else SanwolfTextSecondary
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Mini Volume Slider
            Slider(
                value = track.volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1.2f,
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = if (track.muted || isSilencedBySolo) SanwolfTextMuted else trackColor,
                    activeTrackColor = if (track.muted || isSilencedBySolo) SanwolfTextMuted else trackColor,
                    inactiveTrackColor = SanwolfBlack
                )
            )
        }
    }
}

@Composable
private fun TrackClipCanvas(
    track: TrackData,
    beatWidth: androidx.compose.ui.unit.Dp,
    isSilenced: Boolean = false
) {
    val baseColor = Color(track.colorHex)
    val trackColor = if (isSilenced) baseColor.copy(alpha = 0.3f) else baseColor

    Canvas(modifier = Modifier.fillMaxSize()) {
        val beatWidthPx = beatWidth.toPx()

        // 1. Draw MIDI Piano roll notes as sleek modern DAW blocks
        if (track.notes.isNotEmpty()) {
            for (note in track.notes) {
                val startX = (note.startBeat * beatWidthPx).toFloat()
                val width = (note.lengthBeats * beatWidthPx).toFloat().coerceAtLeast(8f)
                val pitchNorm = (note.pitch - 36).toFloat() / 48f
                val y = size.height * (1f - pitchNorm).coerceIn(0.1f, 0.8f)

                drawRoundRect(
                    color = trackColor.copy(alpha = if (isSilenced) 0.25f else 0.85f),
                    topLeft = Offset(startX, y - 6f),
                    size = androidx.compose.ui.geometry.Size(width, 12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }

        // 2. Draw Step sequencer rhythm blocks
        val stepCount = track.stepCount.coerceAtLeast(1)
        val stepWidthPx = beatWidthPx / 4f
        for (i in 0 until 64) {
            val eff = i % stepCount
            if (track.steps.getOrElse(eff) { false }) {
                val x = i * stepWidthPx
                drawCircle(
                    color = trackColor.copy(alpha = if (isSilenced) 0.3f else 1.0f),
                    radius = 4f,
                    center = Offset(x + stepWidthPx / 2f, size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun VstPluginRackRow(track: TrackData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFF0D0D10))
            .border(androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF202028)))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "VST RACK:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = SanwolfGold
        )

        // Mock hosted plugins
        track.fxChain.forEach { plugin ->
            VstMiniModule(plugin = plugin) { 
                it.isSidechainEnabled = !it.isSidechainEnabled 
            }
        }
    }
}

@Composable
private fun VstMiniModule(plugin: com.example.model.VstPlugin, onToggleSidechain: (com.example.model.VstPlugin) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SanwolfPanelElevated)
            .border(
                1.dp,
                if (plugin.enabled) SanwolfCyan.copy(alpha = 0.5f) else SanwolfPanelBorder,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (plugin.enabled) SanwolfLime else SanwolfTextMuted)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(plugin.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
            
            // Sidechain toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SC", fontSize = 7.sp, color = SanwolfTextMuted)
                Checkbox(
                    checked = plugin.isSidechainEnabled,
                    onCheckedChange = { onToggleSidechain(plugin) },
                    modifier = Modifier.size(16.dp),
                    colors = CheckboxDefaults.colors(checkedColor = SanwolfGold)
                )
            }
        }
    }
}

@Composable
private fun AutomationLaneRow(
    track: TrackData,
    totalBeats: Int,
    beatWidth: androidx.compose.ui.unit.Dp,
    currentBeat: Double = 0.0,
    onAutomationChanged: () -> Unit = {}
) {
    // Ensure track has at least one active automation lane
    if (track.automationLanes.isEmpty()) {
        track.automationLanes.add(
            AutomationLane(
                targetParam = "Filter Cutoff",
                points = mutableListOf(
                    AutomationPoint(0.0, 0.25f),
                    AutomationPoint(4.0, 0.85f),
                    AutomationPoint(8.0, 0.40f),
                    AutomationPoint(12.0, 0.95f),
                    AutomationPoint(16.0, 0.30f)
                )
            )
        )
    }

    var selectedLaneIndex by remember { mutableIntStateOf(0) }
    var showParamMenu by remember { mutableStateOf(false) }
    var showShapesMenu by remember { mutableStateOf(false) }
    var activeDraggingPointIndex by remember { mutableIntStateOf(-1) }
    var localRevision by remember { mutableIntStateOf(0) }

    val safeIndex = selectedLaneIndex.coerceIn(0, (track.automationLanes.size - 1).coerceAtLeast(0))
    val currentLane = track.automationLanes[safeIndex]
    val currentValAtPlayhead = currentLane.getValueAtBeat(currentBeat)

    val availableParams = listOf(
        "Filter Cutoff",
        "Resonance",
        "Volume",
        "Pan",
        "Attack",
        "Release"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF07080B))
            .border(androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF1B1D26)))
    ) {
        // --- Lane Toolbar Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(SanwolfPanelElevated)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Parameter Dropdown Selector
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SanwolfCyan.copy(alpha = 0.15f))
                            .border(1.dp, SanwolfCyan, RoundedCornerShape(4.dp))
                            .clickable { showParamMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("automation_param_selector_${track.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = SanwolfCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "AUTOMATION: ${currentLane.targetParam.uppercase()}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = SanwolfCyan
                        )
                        Text(
                            text = "▾",
                            fontSize = 9.sp,
                            color = SanwolfCyan
                        )
                    }

                    DropdownMenu(
                        expanded = showParamMenu,
                        onDismissRequest = { showParamMenu = false },
                        modifier = Modifier.background(SanwolfPanelElevated)
                    ) {
                        availableParams.forEach { param ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = param,
                                        color = if (currentLane.targetParam == param) SanwolfCyan else SanwolfTextPrimary,
                                        fontWeight = if (currentLane.targetParam == param) FontWeight.Black else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                },
                                onClick = {
                                    currentLane.targetParam = param
                                    showParamMenu = false
                                    onAutomationChanged()
                                }
                            )
                        }
                    }
                }

                // Value readout at current playhead
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfBlack.copy(alpha = 0.6f))
                        .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "VAL: ${currentLane.toDisplayValue(currentValAtPlayhead)}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = SanwolfGold
                    )
                }

                // Shapes Dropdown
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SanwolfPanel)
                            .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                            .clickable { showShapesMenu = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("automation_shapes_button_${track.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "SHAPES ▾",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfTextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showShapesMenu,
                        onDismissRequest = { showShapesMenu = false },
                        modifier = Modifier.background(SanwolfPanelElevated)
                    ) {
                        listOf(
                            "Ramp Up" to "Linear Rise (0% -> 100%)",
                            "Ramp Down" to "Linear Drop (100% -> 0%)",
                            "Sine Wave" to "LFO Modulation Wobble",
                            "Sidechain Duck" to "Pumping 4-on-Floor Ducking",
                            "Build-up Sweep" to "Exponential Tension Riser",
                            "Flat Center" to "Reset to 50%"
                        ).forEach { (shape, desc) ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(shape, color = SanwolfTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(desc, color = SanwolfTextMuted, fontSize = 9.sp)
                                    }
                                },
                                onClick = {
                                    currentLane.applyPresetShape(shape, totalBeats)
                                    showShapesMenu = false
                                    localRevision++
                                    onAutomationChanged()
                                }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Add Point button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfLime.copy(alpha = 0.15f))
                        .border(0.5.dp, SanwolfLime, RoundedCornerShape(4.dp))
                        .clickable {
                            val targetB = Math.round(currentBeat * 4.0) / 4.0
                            val existing = currentLane.points.find { kotlin.math.abs(it.beat - targetB) < 0.2 }
                            if (existing != null) {
                                existing.value = currentValAtPlayhead
                            } else {
                                currentLane.points.add(AutomationPoint(targetB, currentValAtPlayhead))
                                currentLane.points.sortBy { it.beat }
                            }
                            localRevision++
                            onAutomationChanged()
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("automation_add_point_${track.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ POINT",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = SanwolfLime
                    )
                }

                // Clear Lane button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfMagenta.copy(alpha = 0.15f))
                        .border(0.5.dp, SanwolfMagenta.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .clickable {
                            currentLane.points.clear()
                            currentLane.points.add(AutomationPoint(0.0, 0.5f))
                            currentLane.points.add(AutomationPoint(totalBeats.toDouble(), 0.5f))
                            localRevision++
                            onAutomationChanged()
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("automation_clear_${track.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CLEAR",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfMagenta
                    )
                }
            }
        }

        // --- Interactive Curve Canvas ---
        val laneHeight = 84.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(laneHeight)
                .testTag("automation_canvas_${track.id}")
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentLane, totalBeats, localRevision) {
                        val beatPx = beatWidth.toPx()
                        val h = size.height.toFloat()

                        detectTapGestures(
                            onTap = { offset ->
                                val clickedBeat = (offset.x / beatPx).toDouble().coerceIn(0.0, totalBeats.toDouble())
                                val clickedVal = (1f - (offset.y / h)).coerceIn(0f, 1f)

                                // Check if tapped existing point to toggle/remove
                                val existingIndex = currentLane.points.indexOfFirst {
                                    val px = (it.beat * beatPx).toFloat()
                                    val py = (1f - it.value) * h
                                    kotlin.math.hypot(offset.x - px, offset.y - py) < 30f
                                }

                                if (existingIndex >= 0 && currentLane.points.size > 2) {
                                    currentLane.points.removeAt(existingIndex)
                                } else {
                                    val roundedBeat = Math.round(clickedBeat * 4.0) / 4.0
                                    currentLane.points.add(AutomationPoint(roundedBeat, clickedVal))
                                    currentLane.points.sortBy { it.beat }
                                }
                                localRevision++
                                onAutomationChanged()
                            }
                        )
                    }
                    .pointerInput(currentLane, totalBeats, localRevision) {
                        val beatPx = beatWidth.toPx()
                        val h = size.height.toFloat()

                        detectDragGestures(
                            onDragStart = { offset ->
                                val found = currentLane.points.indexOfFirst {
                                    val px = (it.beat * beatPx).toFloat()
                                    val py = (1f - it.value) * h
                                    kotlin.math.hypot(offset.x - px, offset.y - py) < 36f
                                }
                                if (found >= 0) {
                                    activeDraggingPointIndex = found
                                } else {
                                    val newB = (offset.x / beatPx).toDouble().coerceIn(0.0, totalBeats.toDouble())
                                    val newV = (1f - (offset.y / h)).coerceIn(0f, 1f)
                                    val newPt = AutomationPoint(newB, newV)
                                    currentLane.points.add(newPt)
                                    currentLane.points.sortBy { it.beat }
                                    activeDraggingPointIndex = currentLane.points.indexOf(newPt)
                                }
                                localRevision++
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val currentIdx = activeDraggingPointIndex
                                if (currentIdx in currentLane.points.indices) {
                                    val pt = currentLane.points[currentIdx]
                                    val newX = (change.position.x / beatPx).toDouble().coerceIn(0.0, totalBeats.toDouble())
                                    val newY = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                                    pt.beat = newX
                                    pt.value = newY
                                    localRevision++
                                    onAutomationChanged()
                                }
                            },
                            onDragEnd = {
                                activeDraggingPointIndex = -1
                                currentLane.points.sortBy { it.beat }
                                localRevision++
                                onAutomationChanged()
                            },
                            onDragCancel = {
                                activeDraggingPointIndex = -1
                            }
                        )
                    }
            ) {
                val beatPx = beatWidth.toPx()
                val w = size.width
                val h = size.height

                // 1. Grid Lines
                for (b in 0..totalBeats) {
                    val x = b * beatPx
                    val isBarLine = b % 4 == 0
                    drawLine(
                        color = if (isBarLine) Color(0xFF222530) else Color(0xFF101217),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = if (isBarLine) 1.2f else 0.6f
                    )
                }

                // Horizontal Value Guides (25%, 50%, 75%)
                drawLine(
                    color = Color(0xFF161820),
                    start = Offset(0f, h * 0.25f),
                    end = Offset(w, h * 0.25f),
                    strokeWidth = 0.5f
                )
                drawLine(
                    color = Color(0xFF1E222D),
                    start = Offset(0f, h * 0.50f),
                    end = Offset(w, h * 0.50f),
                    strokeWidth = 0.8f
                )
                drawLine(
                    color = Color(0xFF161820),
                    start = Offset(0f, h * 0.75f),
                    end = Offset(w, h * 0.75f),
                    strokeWidth = 0.5f
                )

                // 2. Sort points
                val sorted = currentLane.points.sortedBy { it.beat }

                if (sorted.isNotEmpty()) {
                    val path = Path()
                    val fillPath = Path()

                    val firstPt = sorted.first()
                    val startX = (firstPt.beat * beatPx).toFloat()
                    val startY = (1f - firstPt.value) * h

                    path.moveTo(startX, startY)
                    fillPath.moveTo(startX, h)
                    fillPath.lineTo(startX, startY)

                    for (i in 1 until sorted.size) {
                        val prev = sorted[i - 1]
                        val curr = sorted[i]
                        val x1 = (prev.beat * beatPx).toFloat()
                        val y1 = (1f - prev.value) * h
                        val x2 = (curr.beat * beatPx).toFloat()
                        val y2 = (1f - curr.value) * h

                        val midX = (x1 + x2) / 2f
                        path.cubicTo(midX, y1, midX, y2, x2, y2)
                        fillPath.cubicTo(midX, y1, midX, y2, x2, y2)
                    }

                    val lastPt = sorted.last()
                    val endX = (lastPt.beat * beatPx).toFloat()
                    fillPath.lineTo(endX, h)
                    fillPath.close()

                    // Draw Gradient Fill underneath curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SanwolfCyan.copy(alpha = 0.28f),
                                SanwolfCyan.copy(alpha = 0.04f)
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw Glowing Spline Stroke
                    drawPath(
                        path = path,
                        color = SanwolfCyan,
                        style = Stroke(width = 2.5f)
                    )

                    // 3. Draw Nodes / Control Points
                    for ((pIdx, p) in sorted.withIndex()) {
                        val px = (p.beat * beatPx).toFloat()
                        val py = (1f - p.value) * h
                        val isDragging = activeDraggingPointIndex == pIdx

                        // Outer glow
                        drawCircle(
                            color = if (isDragging) SanwolfGold.copy(alpha = 0.4f) else SanwolfCyan.copy(alpha = 0.25f),
                            radius = if (isDragging) 10f else 7f,
                            center = Offset(px, py)
                        )

                        // Inner solid node
                        drawCircle(
                            color = if (isDragging) SanwolfGold else SanwolfCyan,
                            radius = if (isDragging) 6f else 4.5f,
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5f,
                            center = Offset(px, py)
                        )
                    }
                }

                // 4. Playhead intersection point
                val playheadPx = (currentBeat * beatPx).toFloat()
                if (playheadPx in 0f..w) {
                    val playheadValY = (1f - currentValAtPlayhead) * h
                    drawCircle(
                        color = SanwolfGold,
                        radius = 4.5f,
                        center = Offset(playheadPx, playheadValY)
                    )
                }
            }
        }
    }
}
