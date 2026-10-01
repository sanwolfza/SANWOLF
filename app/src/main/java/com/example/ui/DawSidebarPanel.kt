package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectData
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfGoldDim
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary

@Composable
fun DawSidebarPanel(
    project: ProjectData,
    isPlaying: Boolean,
    currentBeat: Double,
    isRecording: Boolean,
    isFocusMode: Boolean,
    isMetronomeEnabled: Boolean,
    metronomeVolume: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onPlayToggle: () -> Unit,
    onStop: () -> Unit,
    onRecordToggle: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onToggleFocusMode: () -> Unit,
    onMetronomeToggle: () -> Unit,
    onMetronomeVolumeChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onOpenCoProducerArranger: () -> Unit = {},
    onOpenAiAnalyzer: () -> Unit = {},
    onOpenMastering: () -> Unit,
    onOpenMixer: () -> Unit,
    onOpenProjectManager: () -> Unit = {},
    onNewProject: () -> Unit = {},
    onSaveProject: () -> Unit = {},
    onOpenCloudSync: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSampleManager: () -> Unit = {},
    onOpenTutorial: () -> Unit,
    onCloseSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(SanwolfPanel)
            .border(androidx.compose.foundation.BorderStroke(1.dp, SanwolfPanelBorder))
            .padding(12.dp)
    ) {
        // --- Sidebar Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Studio Control Panel",
                    tint = SanwolfGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "STUDIO PANEL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfGold,
                    letterSpacing = 1.sp
                )
            }
            IconButton(
                onClick = onCloseSidebar,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Collapse Sidebar",
                    tint = SanwolfTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = SanwolfPanelBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))

        // --- Scrollable Options List ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group 1: MASTER CLOCK & TEMPO
            SidebarGroup(title = "MASTER CLOCK & TEMPO") {

                // Time Code & Tempo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val bar = (currentBeat / 4).toInt() + 1
                    val beat = (currentBeat % 4).toInt() + 1
                    val tick = ((currentBeat % 1) * 100).toInt()

                    Text(
                        text = String.format("%02d:%02d:%02d", bar, beat, tick),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfCyan,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SanwolfBlack)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TapTempoButton(onBpmChange = onBpmChange)
                        Text(
                            text = "${project.bpm} BPM",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfGold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SanwolfBlack)
                                .clickable {
                                    val nextBpm = if (project.bpm >= 150) 115 else project.bpm + 5
                                    onBpmChange(nextBpm)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Group 2: WORKSPACE UTILITIES
            SidebarGroup(title = "HISTORY & SETTINGS") {
                // Undo / Redo Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (canUndo) SanwolfPanelElevated else SanwolfBlack.copy(alpha = 0.5f))
                            .clickable(enabled = canUndo) { onUndo() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) SanwolfTextPrimary else SanwolfTextMuted, modifier = Modifier.size(14.dp))
                            Text("UNDO", fontSize = 9.sp, color = if (canUndo) SanwolfTextPrimary else SanwolfTextMuted, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (canRedo) SanwolfPanelElevated else SanwolfBlack.copy(alpha = 0.5f))
                            .clickable(enabled = canRedo) { onRedo() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) SanwolfTextPrimary else SanwolfTextMuted, modifier = Modifier.size(14.dp))
                            Text("REDO", fontSize = 9.sp, color = if (canRedo) SanwolfTextPrimary else SanwolfTextMuted, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Focus Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfPanelElevated)
                        .clickable { onToggleFocusMode() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isFocusMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Focus",
                            tint = if (isFocusMode) SanwolfGold else SanwolfTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Immersion Focus", fontSize = 11.sp, color = SanwolfTextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isFocusMode) SanwolfGold else Color.Transparent)
                            .border(1.dp, SanwolfTextSecondary, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metronome Settings
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfPanelElevated)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "Metronome", tint = if (isMetronomeEnabled) SanwolfLime else SanwolfTextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Click Metronome", fontSize = 11.sp, color = SanwolfTextPrimary)
                        }
                        Switch(
                            checked = isMetronomeEnabled,
                            onCheckedChange = { onMetronomeToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SanwolfLime,
                                checkedTrackColor = SanwolfLime.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.size(36.dp, 20.dp)
                        )
                    }

                    if (isMetronomeEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = SanwolfTextSecondary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Slider(
                                value = metronomeVolume,
                                onValueChange = onMetronomeVolumeChange,
                                modifier = Modifier.weight(1f).height(16.dp),
                                colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
                            )
                        }
                    }
                }
            }

            // Group 3: PRODUCTION SUITE
            SidebarGroup(title = "PRODUCTION STUDIO") {
                SidebarMenuButton(
                    label = "AI STUDIO CO-PRODUCER",
                    icon = Icons.Default.AutoAwesome,
                    color = SanwolfGold,
                    isAi = true,
                    onClick = onOpenAiStudio
                )

                SidebarMenuButton(
                    label = "CO-PRODUCER ARRANGER UI",
                    icon = Icons.Default.GraphicEq,
                    color = SanwolfCyan,
                    onClick = onOpenCoProducerArranger
                )

                SidebarMenuButton(
                    label = "AI POLYRHYTHM ANALYZER",
                    icon = Icons.Default.AutoAwesome,
                    color = SanwolfLime,
                    isAi = true,
                    onClick = onOpenAiAnalyzer
                )

                SidebarMenuButton(
                    label = "RE-MASTERING SUITE",
                    icon = Icons.Default.Equalizer,
                    color = SanwolfCyan,
                    onClick = onOpenMastering
                )

                SidebarMenuButton(
                    label = "TRACK LEVEL MIXER",
                    icon = Icons.Default.ListAlt,
                    color = SanwolfGold,
                    onClick = onOpenMixer
                )
            }

            // Group 4: MY PROJECTS & SESSIONS
            SidebarGroup(title = "MY PROJECTS & SESSIONS") {
                // Active Project Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable { onOpenProjectManager() }
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = project.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = SanwolfTextPrimary,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (project.isCloudSynced) SanwolfLime.copy(alpha = 0.2f) else SanwolfGold.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (project.isCloudSynced) "FIRESTORE SYNCED" else "UNSAVED EDITS",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (project.isCloudSynced) SanwolfLime else SanwolfGold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${project.bpm} BPM • ${project.tracks.size} Tracks",
                        fontSize = 9.sp,
                        color = SanwolfTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                SidebarMenuButton(
                    label = "PROJECT REPOSITORY",
                    icon = Icons.Default.Folder,
                    color = SanwolfGold,
                    onClick = onOpenProjectManager
                )

                SidebarMenuButton(
                    label = "NEW BLANK SESSION",
                    icon = Icons.Default.Add,
                    color = SanwolfCyan,
                    onClick = onNewProject
                )

                SidebarMenuButton(
                    label = "SAVE PROJECT",
                    icon = Icons.Default.Save,
                    color = SanwolfLime,
                    onClick = onSaveProject
                )

                SidebarMenuButton(
                    label = "SYNC & LIVE JAM ROOM",
                    icon = Icons.Default.Cloud,
                    color = if (project.isCloudSynced) SanwolfLime else SanwolfTextSecondary,
                    onClick = onOpenCloudSync
                )

                SidebarMenuButton(
                    label = "EXPORT WAV / MIDI",
                    icon = Icons.Default.IosShare,
                    color = SanwolfTextPrimary,
                    onClick = onOpenExport
                )

                SidebarMenuButton(
                    label = "FACTORY .WAV SAMPLES",
                    icon = Icons.Default.FolderOpen,
                    color = SanwolfGold,
                    onClick = onOpenSampleManager
                )
            }
        }

        // --- Sidebar Footer ---
        Divider(color = SanwolfPanelBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
        Text(
            text = "Master Vol:",
            fontSize = 9.sp,
            color = SanwolfTextSecondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.VolumeUp, contentDescription = "Master", tint = SanwolfGold, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Slider(
                value = project.masterVolume,
                onValueChange = onMasterVolumeChange,
                modifier = Modifier.weight(1f).height(16.dp),
                colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
            )
        }
    }
}

@Composable
private fun SidebarGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = SanwolfTextMuted,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

@Composable
private fun SidebarMenuButton(
    label: String,
    icon: ImageVector,
    color: Color,
    isAi: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isAi) SanwolfGoldDim else SanwolfPanelElevated)
            .border(1.dp, if (isAi) SanwolfGold else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isAi) Color.White else color,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = if (isAi) Color.White else SanwolfTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
            if (isAi) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(SanwolfGold)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("AI", fontSize = 8.sp, color = SanwolfBlack, fontWeight = FontWeight.Black)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun TapTempoButton(onBpmChange: (Int) -> Unit) {
    val tapTimes = remember { mutableStateListOf<Long>() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SanwolfPanelElevated)
            .clickable {
                val now = System.currentTimeMillis()
                if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000) {
                    tapTimes.clear()
                }
                tapTimes.add(now)
                if (tapTimes.size > 5) tapTimes.removeAt(0)

                if (tapTimes.size >= 2) {
                    val intervals = (1 until tapTimes.size).map { tapTimes[it] - tapTimes[it - 1] }
                    val avgInterval = intervals.average()
                    val newBpm = (60000 / avgInterval).toInt()
                    onBpmChange(newBpm.coerceIn(40, 300))
                }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "TAP",
            color = SanwolfGold,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}
