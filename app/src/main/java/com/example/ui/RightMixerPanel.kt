package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SanwolfAudioEngine
import com.example.model.MasteringConfig
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.log10

/** Sections of the right-hand mastering hub. */
enum class RightPanelTab(val title: String, val icon: ImageVector) {
    MIXER("Mixer", Icons.Default.Tune),
    MASTER("Mastering", Icons.Default.GraphicEq)
}

/** A master-bus preset. Only fields the engine actually processes are set. */
private data class MasterPreset(
    val name: String,
    val lowDb: Float,
    val highDb: Float,
    val compThresholdDb: Float,
    val width: Float,
    val driveDb: Float,
    val ceilingDb: Float,
    val targetLufs: Float
)

private val MASTER_PRESETS = listOf(
    MasterPreset("Afro House Club", lowDb = 2.5f, highDb = 1.5f, compThresholdDb = -16f, width = 1.2f, driveDb = 4f, ceilingDb = -0.3f, targetLufs = -9f),
    MasterPreset("Streaming -14 LUFS", lowDb = 1.0f, highDb = 1.0f, compThresholdDb = -14f, width = 1.1f, driveDb = 0f, ceilingDb = -1.0f, targetLufs = -14f),
    MasterPreset("Warm & Wide", lowDb = 1.5f, highDb = -1.0f, compThresholdDb = -18f, width = 1.35f, driveDb = 2f, ceilingDb = -0.5f, targetLufs = -12f),
    MasterPreset("Transparent", lowDb = 0f, highDb = 0f, compThresholdDb = 0f, width = 1.0f, driveDb = 0f, ceilingDb = -0.3f, targetLufs = -14f)
)

/**
 * Right-hand mastering hub: a slim icon rail on the screen edge (always visible) plus an
 * animated ~320dp panel with the Mixer (per-track volume/pan/mute/solo) and the Master chain
 * (EQ shelves, glue compressor, stereo width, limiter drive/ceiling, meters, presets, bypass)
 * and an Export button. Every control here drives a real engine parameter.
 */
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
    modifier: Modifier = Modifier,
    selectedTab: RightPanelTab = RightPanelTab.MIXER,
    onSelectTab: (RightPanelTab) -> Unit = {},
    onOpenExport: () -> Unit = {},
    onMasteringChanged: () -> Unit = {},
    onMasterVolumeChange: (Float) -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .testTag("right_mastering_hub")
    ) {
        // --- Expanded panel (slides out from the rail) ---
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(SanwolfPanel)
                    .border(BorderStroke(1.dp, SanwolfPanelBorder))
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Collapse mastering panel", tint = SanwolfTextSecondary)
                    }
                    Icon(selectedTab.icon, contentDescription = null, tint = SanwolfCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedTab.title.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = SanwolfCyan,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedTab == RightPanelTab.MIXER) {
                        Text("${project.tracks.size} tracks", fontSize = 12.sp, color = SanwolfTextSecondary)
                    }
                }
                HorizontalDivider(color = SanwolfPanelBorder)

                key(selectedTab) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (selectedTab) {
                            RightPanelTab.MIXER -> MixerSection(
                                project = project,
                                onTrackVolumeChange = onTrackVolumeChange,
                                onTrackPanChange = onTrackPanChange,
                                onToggleMute = onToggleMute,
                                onToggleSolo = onToggleSolo
                            )
                            RightPanelTab.MASTER -> MasterSection(
                                project = project,
                                audioEngine = audioEngine,
                                isVisible = isExpanded,
                                onMasteringChanged = onMasteringChanged,
                                onMasterVolumeChange = onMasterVolumeChange
                            )
                        }
                    }
                }

                // Export is always one tap away at the bottom of the hub
                HorizontalDivider(color = SanwolfPanelBorder)
                Button(
                    onClick = onOpenExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .heightIn(min = 48.dp)
                        .testTag("hub_export_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export mix", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // --- Icon rail (always visible, on the screen edge) ---
        Column(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(SanwolfSurfaceDark)
                .border(BorderStroke(1.dp, SanwolfPanelBorder))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                    contentDescription = if (isExpanded) "Collapse mastering panel" else "Expand mastering panel",
                    tint = SanwolfTextSecondary
                )
            }
            HorizontalDivider(color = SanwolfPanelBorder, modifier = Modifier.width(32.dp))
            RightPanelTab.values().forEach { tab ->
                val isSelected = isExpanded && tab == selectedTab
                HubRailButton(
                    icon = tab.icon,
                    label = tab.title,
                    isSelected = isSelected,
                    onClick = {
                        if (isSelected) {
                            onToggleExpand()
                        } else {
                            onSelectTab(tab)
                            if (!isExpanded) onToggleExpand()
                        }
                    }
                )
            }
            HubRailButton(
                icon = Icons.Default.FileDownload,
                label = "Export mix",
                isSelected = false,
                onClick = onOpenExport
            )
            if (!project.masteringConfig.enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("BYP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfMagenta)
            }
        }
    }
}

@Composable
private fun HubRailButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) SanwolfCyan.copy(alpha = 0.18f) else Color.Transparent)
            .semantics {
                contentDescription = label
                selected = isSelected
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) SanwolfCyan else SanwolfTextPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Mixer
// ---------------------------------------------------------------------------------------------

@Composable
private fun MixerSection(
    project: ProjectData,
    onTrackVolumeChange: (Int, Float) -> Unit,
    onTrackPanChange: (Int, Float) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit
) {
    if (project.tracks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = SanwolfTextSecondary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("No tracks to mix yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Add an instrument, import audio, or tap Record to create your first track.",
                fontSize = 12.sp,
                color = SanwolfTextSecondary,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    project.tracks.forEachIndexed { index, track ->
        key(track.id) {
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(track.colorHex))
                )
                Text(
                    text = "${trackIndex + 1}. ${track.name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SanwolfTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (track.muted) Color.Red.copy(alpha = 0.3f) else SanwolfBlack)
                        .border(0.5.dp, if (track.muted) Color.Red else SanwolfPanelBorder)
                        .clickable { onToggleMute() }
                        .size(40.dp)
                        .semantics { contentDescription = if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (track.muted) Color.Red else SanwolfTextSecondary)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (track.solo) SanwolfGold.copy(alpha = 0.3f) else SanwolfBlack)
                        .border(0.5.dp, if (track.solo) SanwolfGold else SanwolfPanelBorder)
                        .clickable { onToggleSolo() }
                        .size(40.dp)
                        .semantics { contentDescription = if (track.solo) "Unsolo ${track.name}" else "Solo ${track.name}" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (track.solo) SanwolfGold else SanwolfTextSecondary)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Vol", fontSize = 12.sp, color = SanwolfTextSecondary, modifier = Modifier.width(30.dp))
            Slider(
                value = track.volume.coerceIn(0f, 1f),
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).height(36.dp).semantics { contentDescription = "${track.name} volume" },
                colors = SliderDefaults.colors(thumbColor = SanwolfCyan, activeTrackColor = SanwolfCyan)
            )
            Text("${(track.volume * 100).toInt()}%", fontSize = 12.sp, color = SanwolfTextSecondary, modifier = Modifier.width(40.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Pan", fontSize = 12.sp, color = SanwolfTextSecondary, modifier = Modifier.width(30.dp))
            Slider(
                value = track.pan,
                onValueChange = onPanChange,
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f).height(36.dp).semantics { contentDescription = "${track.name} pan" },
                colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
            )
            val panTxt = if (track.pan < -0.05f) "L${((-track.pan) * 100).toInt()}" else if (track.pan > 0.05f) "R${((track.pan) * 100).toInt()}" else "C"
            Text(panTxt, fontSize = 12.sp, color = SanwolfTextSecondary, modifier = Modifier.width(40.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Master chain / mastering
// ---------------------------------------------------------------------------------------------

@Composable
private fun MasterSection(
    project: ProjectData,
    audioEngine: SanwolfAudioEngine,
    isVisible: Boolean,
    onMasteringChanged: () -> Unit,
    onMasterVolumeChange: (Float) -> Unit
) {
    // project.masteringConfig is a plain object: bump this to redraw after edits
    var uiTick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val tick = uiTick
    val config: MasteringConfig = project.masteringConfig
    val bypassed = !config.enabled

    fun edit(commit: Boolean = false, change: (MasteringConfig) -> Unit) {
        change(config)
        uiTick++
        if (commit) onMasteringChanged()
    }

    // ---- Meters (polled from the audio thread's volatile readings while visible) ----
    var peakL by remember { mutableFloatStateOf(0f) }
    var peakR by remember { mutableFloatStateOf(0f) }
    var rmsDb by remember { mutableFloatStateOf(-90f) }
    var compGr by remember { mutableFloatStateOf(0f) }
    var limGr by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isVisible) {
        while (isVisible) {
            peakL = audioEngine.currentPeakLeft
            peakR = audioEngine.currentPeakRight
            rmsDb = audioEngine.masterRmsDb
            compGr = audioEngine.compressorReductionDb
            limGr = audioEngine.limiterReductionDb
            delay(60)
        }
    }

    // A/B: mastered chain vs bypass
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AbChip("A · Mastered", selected = !bypassed, color = SanwolfLime, modifier = Modifier.weight(1f)) {
            edit(commit = true) { it.enabled = true }
        }
        AbChip("B · Bypass", selected = bypassed, color = SanwolfMagenta, modifier = Modifier.weight(1f)) {
            edit(commit = true) { it.enabled = false }
        }
    }

    // Meters
    HubCard {
        HubLabel("Output meters")
        PeakMeterRow("L", peakL)
        PeakMeterRow("R", peakR)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MeterReadout("RMS", if (rmsDb <= -89f) "-inf" else "%.1f dB".format(rmsDb))
            MeterReadout("Comp GR", "%.1f dB".format(compGr))
            MeterReadout("Limiter GR", "%.1f dB".format(limGr))
        }
        Text(
            "Loudness shown as RMS (dBFS), not LUFS. Use it as a guide.",
            fontSize = 11.sp,
            color = SanwolfTextMuted
        )
    }

    // Presets
    HubCard {
        HubLabel("Presets")
        MASTER_PRESETS.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { preset ->
                    AbChip(
                        label = preset.name,
                        selected = config.profileName == preset.name,
                        color = SanwolfGold,
                        modifier = Modifier.weight(1f)
                    ) {
                        edit(commit = true) {
                            it.enabled = true
                            it.lowGainDb = preset.lowDb
                            it.highGainDb = preset.highDb
                            it.compressorThresholdDb = preset.compThresholdDb
                            it.stereoWidth = preset.width
                            it.limiterGainDb = preset.driveDb
                            it.limiterCeilingDb = preset.ceilingDb
                            it.targetLufs = preset.targetLufs
                            it.profileName = preset.name
                        }
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        Text(
            "Target: ${config.targetLufs.toInt()} LUFS (reference only; the chain does not auto-normalise).",
            fontSize = 11.sp,
            color = SanwolfTextMuted
        )
    }

    // Master volume
    HubCard {
        HubSlider(
            label = "Master volume",
            value = project.masterVolume,
            range = 0f..1.5f,
            display = "${(project.masterVolume * 100).toInt()}%",
            color = SanwolfMagenta,
            enabled = true,
            onChange = {
                onMasterVolumeChange(it)
                uiTick++
            },
            onDone = onMasteringChanged
        )
    }

    val chainEnabled = !bypassed
    // EQ
    HubCard {
        HubLabel("Master EQ")
        HubSlider("Low shelf (120 Hz)", config.lowGainDb, -6f..6f, dbText(config.lowGainDb), SanwolfOrange, chainEnabled,
            onChange = { v -> edit { it.lowGainDb = v } }, onDone = onMasteringChanged)
        HubSlider("High shelf (6 kHz)", config.highGainDb, -6f..6f, dbText(config.highGainDb), SanwolfCyan, chainEnabled,
            onChange = { v -> edit { it.highGainDb = v } }, onDone = onMasteringChanged)
    }

    // Glue compressor
    HubCard {
        HubLabel("Glue compressor · 2:1")
        val thr = config.compressorThresholdDb
        HubSlider("Threshold", thr, -30f..0f, if (thr >= -0.5f) "Off" else dbText(thr), SanwolfGold, chainEnabled,
            onChange = { v -> edit { it.compressorThresholdDb = v } }, onDone = onMasteringChanged)
        Text("10 ms attack, 150 ms release, automatic make-up gain.", fontSize = 11.sp, color = SanwolfTextMuted)
    }

    // Stereo width
    HubCard {
        HubSlider("Stereo width", config.stereoWidth, 0f..2f, "${(config.stereoWidth * 100).toInt()}%", SanwolfPurple, chainEnabled,
            onChange = { v -> edit { it.stereoWidth = v } }, onDone = onMasteringChanged)
    }

    // Limiter
    HubCard {
        HubLabel("Limiter")
        HubSlider("Drive (gain in)", config.limiterGainDb, 0f..12f, dbText(config.limiterGainDb), SanwolfMagenta, chainEnabled,
            onChange = { v -> edit { it.limiterGainDb = v } }, onDone = onMasteringChanged)
        HubSlider("Ceiling", config.limiterCeilingDb, -3f..0f, "%.1f dB".format(config.limiterCeilingDb), SanwolfLime, chainEnabled,
            onChange = { v -> edit { it.limiterCeilingDb = v } }, onDone = onMasteringChanged)
    }

    if (bypassed) {
        Text(
            "Bypassed: only master volume and a -0.3 dB safety limiter are active.",
            fontSize = 12.sp,
            color = SanwolfMagenta
        )
    }
}

private fun dbText(v: Float): String = (if (v > 0.05f) "+" else "") + "%.1f dB".format(v)

@Composable
private fun HubCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun HubLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanwolfTextMuted, letterSpacing = 0.8.sp)
}

@Composable
private fun HubSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    color: Color,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 12.sp, color = if (enabled) SanwolfTextPrimary else SanwolfTextMuted)
            Text(display, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (enabled) color else SanwolfTextMuted)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics { contentDescription = label },
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}

@Composable
private fun AbChip(label: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else SanwolfPanelElevated)
            .border(1.dp, if (selected) color else SanwolfPanelBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) color else SanwolfTextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun PeakMeterRow(label: String, linear: Float) {
    val db = if (linear > 1e-5f) 20f * log10(linear) else -60f
    val fraction = ((db + 60f) / 60f).coerceIn(0f, 1f)
    val barColor = when {
        db > -1f -> SanwolfMagenta
        db > -6f -> SanwolfGold
        else -> SanwolfLime
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = SanwolfTextSecondary, modifier = Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SanwolfBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(barColor)
            )
        }
        Text(
            if (db <= -59f) "-inf" else "%.1f".format(db),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = SanwolfTextSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}

@Composable
private fun MeterReadout(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = SanwolfTextMuted)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
    }
}
