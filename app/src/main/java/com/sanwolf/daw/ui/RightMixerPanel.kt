package com.sanwolf.daw.ui

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.sanwolf.daw.audio.SanwolfAudioEngine
import com.sanwolf.daw.model.MasteringConfig
import com.sanwolf.daw.model.ProjectData
import com.sanwolf.daw.model.TrackData
import com.sanwolf.daw.model.TrackType
import com.sanwolf.daw.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.log10

/** Sections of the right-hand panel: the selected track's channel, the master, the keyboard. */
enum class RightPanelTab(val title: String, val icon: ImageVector) {
    TRACK("Track", Icons.Default.Tune),
    MASTER("Master", Icons.Default.GraphicEq),
    KEYS("Keys", Icons.Default.Piano)
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
 * Right-hand panel, FL Studio Mobile style. Tabs at the top:
 *  - Track: pick a track, its mixer basics (volume, pan, mute, solo), then its insert rack.
 *  - Master: master insert rack, then the mastering chain (A/B, presets, volume, EQ, glue,
 *    width, limiter), with the LUFS / RMS meter pinned at the bottom.
 *  - Keys: the live piano's keyboard settings (octave, range, key size, velocity).
 * The whole panel collapses to nothing; the edge tab (EdgeHandle) re-opens it.
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
    keyboardSettings: PianoKeyboardSettings,
    modifier: Modifier = Modifier,
    selectedTrackIndex: Int = 0,
    onSelectTrack: (Int) -> Unit = {},
    revision: Int = 0,
    selectedTab: RightPanelTab = RightPanelTab.TRACK,
    onSelectTab: (RightPanelTab) -> Unit = {},
    onOpenExport: () -> Unit = {},
    onMasteringChanged: () -> Unit = {},
    onMasterVolumeChange: (Float) -> Unit = {},
    /** A rack slot was added, removed or reordered (the engine must rebuild its routing). */
    onRackStructureChanged: () -> Unit = {},
    /** A rack slot's parameter or bypass was committed (undo entry). */
    onRackParamsCommitted: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .width(sidePanelWidth())
            .fillMaxHeight()
            .background(SanwolfPanel)
            .border(BorderStroke(1.dp, SanwolfPanelBorder))
            .testTag("right_mastering_hub")
    ) {
        // Header: collapse + tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Collapse right panel", tint = SanwolfTextSecondary, modifier = Modifier.size(18.dp))
            }
            RightPanelTab.values().forEach { tab ->
                PanelTabChip(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        HorizontalDivider(color = SanwolfPanelBorder)

        val selectedTrack: TrackData? = project.tracks.getOrNull(selectedTrackIndex) ?: project.tracks.firstOrNull()
        val selectedIndex = if (selectedTrack == null) -1 else project.tracks.indexOf(selectedTrack)

        key(selectedTab) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (selectedTab) {
                    RightPanelTab.TRACK -> TrackTab(
                        project = project,
                        track = selectedTrack,
                        trackIndex = selectedIndex,
                        revision = revision,
                        onSelectTrack = onSelectTrack,
                        onTrackVolumeChange = onTrackVolumeChange,
                        onTrackPanChange = onTrackPanChange,
                        onToggleMute = onToggleMute,
                        onToggleSolo = onToggleSolo,
                        onRackStructureChanged = onRackStructureChanged,
                        onRackParamsCommitted = onRackParamsCommitted
                    )
                    RightPanelTab.MASTER -> {
                        MasteringAssistantCard(
                            project = project,
                            audioEngine = audioEngine,
                            isVisible = isExpanded,
                            onApplied = onMasteringChanged,
                            onMasterVolumeChange = onMasterVolumeChange
                        )
                        FxRackView(
                            title = "Master inserts",
                            slots = project.masterSlots(),
                            ensureSlots = { project.masterRackOrCreate() },
                            revision = revision,
                            onStructureChanged = onRackStructureChanged,
                            onParamsCommitted = onRackParamsCommitted,
                            footerNote = "Signal: tracks → master inserts → mastering chain → out."
                        )
                        MasterSection(
                            project = project,
                            revision = revision,
                            onMasteringChanged = onMasteringChanged,
                            onMasterVolumeChange = onMasterVolumeChange
                        )
                    }
                    RightPanelTab.KEYS -> KeyboardTab(keyboardSettings)
                }
            }
        }

        if (selectedTab == RightPanelTab.MASTER) {
            HorizontalDivider(color = SanwolfPanelBorder)
            MasterMeterFooter(project = project, audioEngine = audioEngine, isVisible = isExpanded)
        }

        // Export is always one tap away at the bottom of the panel
        HorizontalDivider(color = SanwolfPanelBorder)
        Button(
            onClick = onOpenExport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .heightIn(min = 34.dp)
                .testTag("hub_export_button"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export mix", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PanelTabChip(tab: RightPanelTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) SanwolfCyan.copy(alpha = 0.18f) else SanwolfPanelElevated)
            .border(1.dp, if (selected) SanwolfCyan else SanwolfPanelBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = tab.title
                this.selected = selected
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = null, tint = if (selected) SanwolfCyan else SanwolfTextSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            tab.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) SanwolfCyan else SanwolfTextPrimary,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Track tab
// ---------------------------------------------------------------------------------------------

@Composable
private fun TrackTab(
    project: ProjectData,
    track: TrackData?,
    trackIndex: Int,
    revision: Int,
    onSelectTrack: (Int) -> Unit,
    onTrackVolumeChange: (Int, Float) -> Unit,
    onTrackPanChange: (Int, Float) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
    onRackStructureChanged: () -> Unit,
    onRackParamsCommitted: () -> Unit
) {
    if (track == null || trackIndex < 0) {
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
                "Add an instrument, import audio, or record to create your first track.",
                fontSize = 12.sp,
                color = SanwolfTextSecondary,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    // Channel selector
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        project.tracks.forEachIndexed { idx, t ->
            val isSel = idx == trackIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSel) Color(t.colorHex).copy(alpha = 0.25f) else SanwolfPanelElevated)
                    .border(1.dp, if (isSel) Color(t.colorHex) else SanwolfPanelBorder, RoundedCornerShape(4.dp))
                    .clickable { onSelectTrack(idx) }
                    .semantics { contentDescription = "Select ${t.name}" }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    "${idx + 1} ${t.name}",
                    fontSize = 10.sp,
                    color = if (isSel) SanwolfTextPrimary else SanwolfTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp)
                )
            }
        }
    }

    Text(
        "Track: ${track.name}",
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = SanwolfCyan,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    key(track.id) {
        TrackMixerStrip(
            trackIndex = trackIndex,
            track = track,
            onVolumeChange = { vol -> onTrackVolumeChange(trackIndex, vol) },
            onPanChange = { pan -> onTrackPanChange(trackIndex, pan) },
            onToggleMute = { onToggleMute(trackIndex) },
            onToggleSolo = { onToggleSolo(trackIndex) }
        )

        val isAudio = track.type == TrackType.AUDIO_IMPORT
        FxRackView(
            title = "Inserts",
            slots = track.fxSlots(),
            ensureSlots = { track.fxRackOrCreate() },
            revision = revision,
            onStructureChanged = onRackStructureChanged,
            onParamsCommitted = onRackParamsCommitted,
            enabled = !isAudio,
            disabledNote = "Audio-clip tracks play through the system media player, outside the effects engine, so they can't take inserts yet. Instrument and drum tracks can.",
            footerNote = "Processed in order, top to bottom, before the master."
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Keys tab: live piano keyboard settings (the transport, BPM and metronome stay on the piano)
// ---------------------------------------------------------------------------------------------

@Composable
private fun KeyboardTab(settings: PianoKeyboardSettings) {
    HubCard {
        HubLabel("Octave")
        StepperRow(
            label = "Lowest key C${settings.baseOctave}",
            value = "${settings.baseOctave}",
            canDecrease = settings.baseOctave > 1,
            canIncrease = settings.baseOctave < 6,
            onDecrease = { settings.changeOctave(-1) },
            onIncrease = { settings.changeOctave(1) },
            decreaseDescription = "Octave down",
            increaseDescription = "Octave up"
        )
    }
    HubCard {
        HubLabel("Keyboard range")
        StepperRow(
            label = if (settings.octaveCount == 1) "1 octave (widest keys)" else "${settings.octaveCount} octaves",
            value = "${settings.octaveCount}",
            canDecrease = settings.octaveCount > 1,
            canIncrease = settings.octaveCount < 4,
            onDecrease = { settings.changeOctaveCount(-1) },
            onIncrease = { settings.changeOctaveCount(1) },
            decreaseDescription = "Fewer octaves (wider keys)",
            increaseDescription = "More octaves (narrower keys)"
        )
    }
    HubCard {
        HubLabel("Key size")
        PianoKeysSize.values().toList().chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { size ->
                    AbChip(
                        label = size.label,
                        selected = settings.keysSize == size,
                        color = SanwolfGold,
                        modifier = Modifier.weight(1f)
                    ) { settings.selectSize(size) }
                }
            }
        }
        Text("Hidden keeps only the transport strip on screen.", fontSize = 10.sp, color = SanwolfTextMuted)
    }
    HubCard {
        HubSlider(
            label = "Key velocity",
            value = settings.velocity,
            range = 0.1f..1f,
            display = "${(settings.velocity * 100).toInt()}%",
            color = SanwolfCyan,
            enabled = true,
            onChange = { settings.changeVelocity(it) },
            onDone = { settings.commitVelocity() }
        )
        Text("How hard the on-screen keys play (and record).", fontSize = 10.sp, color = SanwolfTextMuted)
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseDescription: String,
    increaseDescription: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = SanwolfTextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onDecrease, enabled = canDecrease, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Remove, contentDescription = decreaseDescription, tint = SanwolfTextPrimary, modifier = Modifier.size(16.dp))
        }
        Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = SanwolfCyan, textAlign = TextAlign.Center, modifier = Modifier.width(22.dp))
        IconButton(onClick = onIncrease, enabled = canIncrease, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Add, contentDescription = increaseDescription, tint = SanwolfTextPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Mixer strip
// ---------------------------------------------------------------------------------------------

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
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
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
                        .size(30.dp)
                        .semantics { contentDescription = if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (track.muted) Color.Red else SanwolfTextSecondary)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (track.solo) SanwolfGold.copy(alpha = 0.3f) else SanwolfBlack)
                        .border(0.5.dp, if (track.solo) SanwolfGold else SanwolfPanelBorder)
                        .clickable { onToggleSolo() }
                        .size(30.dp)
                        .semantics { contentDescription = if (track.solo) "Unsolo ${track.name}" else "Solo ${track.name}" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (track.solo) SanwolfGold else SanwolfTextSecondary)
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
                modifier = Modifier.weight(1f).height(28.dp).semantics { contentDescription = "${track.name} volume" },
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
                modifier = Modifier.weight(1f).height(28.dp).semantics { contentDescription = "${track.name} pan" },
                colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
            )
            val panTxt = if (track.pan < -0.05f) "L${((-track.pan) * 100).toInt()}" else if (track.pan > 0.05f) "R${((track.pan) * 100).toInt()}" else "C"
            Text(panTxt, fontSize = 12.sp, color = SanwolfTextSecondary, modifier = Modifier.width(40.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Master tab: mastering chain (after the master inserts) and the pinned meter
// ---------------------------------------------------------------------------------------------

@Composable
private fun MasterSection(
    project: ProjectData,
    revision: Int,
    onMasteringChanged: () -> Unit,
    onMasterVolumeChange: (Float) -> Unit
) {
    // project.masteringConfig is a plain object: bump this to redraw after edits. [revision]
    // (projectVersion) redraws after outside edits, e.g. the mastering assistant's Apply.
    var uiTick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val tick = uiTick + revision
    var chainOpen by remember { mutableStateOf(true) }
    val config: MasteringConfig = project.masteringConfig
    val bypassed = !config.enabled

    fun edit(commit: Boolean = false, change: (MasteringConfig) -> Unit) {
        change(config)
        uiTick++
        if (commit) onMasteringChanged()
    }

    // Master volume (always active)
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

    // Mastering chain header (fixed last stage of the master rack)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .border(0.5.dp, if (bypassed) SanwolfMagenta.copy(alpha = 0.6f) else SanwolfLime.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable { chainOpen = !chainOpen }
            .semantics { contentDescription = if (chainOpen) "Hide mastering chain" else "Show mastering chain" }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text("MASTERING CHAIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfTextMuted, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
        Text(if (bypassed) "BYPASS" else "ON", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (bypassed) SanwolfMagenta else SanwolfLime)
        Icon(
            if (chainOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = SanwolfTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
    if (!chainOpen) return

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
            fontSize = 10.sp,
            color = SanwolfTextMuted
        )
    }

    val chainEnabled = !bypassed
    HubCard {
        HubLabel("EQ")
        HubSlider("Low shelf (120 Hz)", config.lowGainDb, -6f..6f, dbText(config.lowGainDb), SanwolfOrange, chainEnabled,
            onChange = { v -> edit { it.lowGainDb = v } }, onDone = onMasteringChanged)
        HubSlider("High shelf (6 kHz)", config.highGainDb, -6f..6f, dbText(config.highGainDb), SanwolfCyan, chainEnabled,
            onChange = { v -> edit { it.highGainDb = v } }, onDone = onMasteringChanged)
    }
    HubCard {
        HubLabel("Glue compressor · 2:1")
        val thr = config.compressorThresholdDb
        HubSlider("Threshold", thr, -30f..0f, if (thr >= -0.5f) "Off" else dbText(thr), SanwolfGold, chainEnabled,
            onChange = { v -> edit { it.compressorThresholdDb = v } }, onDone = onMasteringChanged)
    }
    HubCard {
        HubSlider("Stereo width", config.stereoWidth, 0f..2f, "${(config.stereoWidth * 100).toInt()}%", SanwolfPurple, chainEnabled,
            onChange = { v -> edit { it.stereoWidth = v } }, onDone = onMasteringChanged)
    }
    HubCard {
        HubLabel("Limiter")
        HubSlider("Drive (gain in)", config.limiterGainDb, 0f..12f, dbText(config.limiterGainDb), SanwolfMagenta, chainEnabled,
            onChange = { v -> edit { it.limiterGainDb = v } }, onDone = onMasteringChanged)
        HubSlider("Ceiling", config.limiterCeilingDb, -3f..0f, "%.1f dB".format(config.limiterCeilingDb), SanwolfLime, chainEnabled,
            onChange = { v -> edit { it.limiterCeilingDb = v } }, onDone = onMasteringChanged)
    }
    if (bypassed) {
        Text(
            "Bypassed: only master volume, the master inserts and a -0.3 dB safety limiter are active.",
            fontSize = 11.sp,
            color = SanwolfMagenta
        )
    }
}

/** Output meter pinned under the Master tab: L/R peak, LUFS-M, LUFS-S, RMS, gain reduction. */
@Composable
private fun MasterMeterFooter(project: ProjectData, audioEngine: SanwolfAudioEngine, isVisible: Boolean) {
    var peakL by remember { mutableFloatStateOf(0f) }
    var peakR by remember { mutableFloatStateOf(0f) }
    var rmsDb by remember { mutableFloatStateOf(-90f) }
    var lufsM by remember { mutableFloatStateOf(-70f) }
    var lufsS by remember { mutableFloatStateOf(-70f) }
    var compGr by remember { mutableFloatStateOf(0f) }
    var limGr by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isVisible) {
        while (isVisible) {
            peakL = audioEngine.currentPeakLeft
            peakR = audioEngine.currentPeakRight
            rmsDb = audioEngine.masterRmsDb
            lufsM = audioEngine.masterMomentaryLufs
            lufsS = audioEngine.masterShortTermLufs
            compGr = audioEngine.compressorReductionDb
            limGr = audioEngine.limiterReductionDb
            delay(60)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SanwolfSurfaceDark)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("master_meter_footer"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        PeakMeterRow("L", peakL)
        PeakMeterRow("R", peakR)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MeterReadout("LUFS-M", lufsText(lufsM))
            MeterReadout("LUFS-S", lufsText(lufsS))
            MeterReadout("RMS", if (rmsDb <= -89f) "-inf" else "%.1f".format(rmsDb))
            val delta = lufsS - project.masteringConfig.targetLufs
            MeterReadout("vs tgt", if (lufsS <= -69f) "--" else (if (delta > 0f) "+" else "") + "%.1f".format(delta))
        }
        Text(
            "GR comp %.1f / lim %.1f dB · BS.1770 (M 400 ms, S 3 s)".format(compGr, limGr),
            fontSize = 9.sp,
            color = SanwolfTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun lufsText(v: Float): String = if (v <= -69.5f) "-inf" else "%.1f".format(v)

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
                .height(30.dp)
                .semantics { contentDescription = label },
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}

@Composable
private fun AbChip(label: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
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
