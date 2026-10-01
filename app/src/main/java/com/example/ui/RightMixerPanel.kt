package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SanwolfAudioEngine
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.ui.theme.*

@Composable
fun RightMixerPanel(
    project: ProjectData,
    audioEngine: SanwolfAudioEngine,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onTrackVolumeChange: (Int, Float) -> Unit,
    onTrackPanChange: (Int, Float) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxHeight()) {
        // Toggle Tab / Button when collapsed or expanded edge
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .background(SanwolfPanelElevated)
                .border(0.5.dp, SanwolfPanelBorder)
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                contentDescription = "Toggle Mixer",
                tint = SanwolfCyan,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(SanwolfPanel)
                    .border(0.5.dp, SanwolfPanelBorder)
                    .padding(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = SanwolfCyan, modifier = Modifier.size(16.dp))
                        Text("WORKSTATION MIXER", fontSize = 11.sp, fontWeight = FontWeight.Black, color = SanwolfCyan)
                    }
                    Text("${project.tracks.size} Tracks", fontSize = 9.sp, color = SanwolfTextMuted)
                }

                HorizontalDivider(color = SanwolfPanelBorder, thickness = 1.dp)

                Spacer(modifier = Modifier.height(6.dp))

                // Section A: Individual Track / Channel Mixing
                Text("A. TRACK CHANNELS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SanwolfGold)
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(project.tracks) { index, track ->
                        TrackMixerStrip(
                            trackIndex = index,
                            track = track,
                            onVolumeChange = { vol -> onTrackVolumeChange(index, vol) },
                            onPanChange = { pan -> onTrackPanChange(index, pan) },
                            onToggleMute = { onToggleMute(index) },
                            onToggleSolo = { onToggleSolo(index) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SanwolfPanelBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Section B: Master Mixing Section
                Text("B. MASTER BUS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SanwolfMagenta)
                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfPanelElevated)
                        .border(0.5.dp, SanwolfPanelBorder)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Master Vol", fontSize = 10.sp, color = SanwolfTextPrimary)
                        Text("${(project.masterVolume * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfMagenta)
                    }
                    Slider(
                        value = project.masterVolume,
                        onValueChange = {
                            project.masterVolume = it
                            audioEngine.masterVolume = it
                        },
                        valueRange = 0f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = SanwolfMagenta, activeTrackColor = SanwolfMagenta)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Limit: ${project.masteringConfig.limiterCeilingDb}dB", fontSize = 9.sp, color = SanwolfTextSecondary)
                        Text(if (project.masteringConfig.enabled) "Mastering ON" else "Mastering OFF", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (project.masteringConfig.enabled) SanwolfLime else SanwolfTextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackMixerStrip(
    trackIndex: Int,
    track: TrackData,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .border(0.5.dp, if (track.muted) Color.Red.copy(alpha = 0.5f) else if (track.solo) SanwolfGold else SanwolfPanelBorder)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(track.colorHex))
                )
                Text(
                    text = "${trackIndex + 1}. ${track.name}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SanwolfTextPrimary,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Mute button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (track.muted) Color.Red.copy(alpha = 0.3f) else SanwolfBlack)
                        .border(0.5.dp, if (track.muted) Color.Red else SanwolfPanelBorder)
                        .clickable { onToggleMute() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("M", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (track.muted) Color.Red else SanwolfTextSecondary)
                }
                // Solo button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (track.solo) SanwolfGold.copy(alpha = 0.3f) else SanwolfBlack)
                        .border(0.5.dp, if (track.solo) SanwolfGold else SanwolfPanelBorder)
                        .clickable { onToggleSolo() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("S", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (track.solo) SanwolfGold else SanwolfTextSecondary)
                }
            }
        }

        // Volume Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Vol", fontSize = 8.sp, color = SanwolfTextMuted, modifier = Modifier.width(20.dp))
            Slider(
                value = track.volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).height(18.dp),
                colors = SliderDefaults.colors(thumbColor = SanwolfCyan, activeTrackColor = SanwolfCyan)
            )
            Text("${(track.volume * 100).toInt()}%", fontSize = 8.sp, color = SanwolfTextSecondary, modifier = Modifier.width(24.dp))
        }

        // Pan Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Pan", fontSize = 8.sp, color = SanwolfTextMuted, modifier = Modifier.width(20.dp))
            Slider(
                value = track.pan,
                onValueChange = onPanChange,
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f).height(18.dp),
                colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
            )
            val panTxt = if (track.pan < -0.05f) "L${((-track.pan) * 100).toInt()}" else if (track.pan > 0.05f) "R${((track.pan) * 100).toInt()}" else "C"
            Text(panTxt, fontSize = 8.sp, color = SanwolfTextSecondary, modifier = Modifier.width(24.dp))
        }
    }
}
