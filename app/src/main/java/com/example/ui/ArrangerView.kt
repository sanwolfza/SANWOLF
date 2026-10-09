package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.firebase.FirebaseDawManager
import com.example.model.AutomationLane
import com.example.model.AutomationPoint
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.model.TrackType
import com.example.model.VstPlugin
import com.example.ui.sidebar.SidebarNameDialog
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ---- Arranger geometry ----
private val ARR_HEADER_WIDTH = 200.dp
private val ARR_ROW_HEIGHT = 64.dp
private val ARR_RULER_HEIGHT = 30.dp
/** 16 bars of 4/4: matches the engine's maximum song loop (SanwolfAudioEngine.MAX_LOOP_BEATS). */
private const val ARR_TOTAL_BEATS = 64
private const val MIN_BEAT_WIDTH_DP = 14f
private const val MAX_BEAT_WIDTH_DP = 160f

/** What a clip on a lane represents. Clips are derived from the track's data. */
private enum class ClipKind { NOTES, PATTERN, AUDIO }

private data class ArrClip(val kind: ClipKind, val startBeat: Double, val endBeat: Double)

private data class ClipHit(val clip: ArrClip, val isResizeEdge: Boolean)

/** Grid sizes for clip moves (in beats). */
private val SNAP_OPTIONS = listOf(0.25 to "1/16", 1.0 to "Beat", 4.0 to "Bar")

/**
 * A track's clips: its MIDI notes as one clip (spanning first to last note), its step pattern
 * as a looping pattern clip (one cycle drawn solid, repeats ghosted), or its audio stem.
 */
private fun clipsFor(track: TrackData): List<ArrClip> {
    val clips = mutableListOf<ArrClip>()
    if (track.type == TrackType.AUDIO_IMPORT) {
        if (track.audioUri != null || track.waveformPeaks != null || track.audioFileName != null) {
            clips.add(ArrClip(ClipKind.AUDIO, 0.0, 16.0))
        }
        return clips
    }
    if (track.notes.isNotEmpty()) {
        val start = floor(track.notes.minOf { it.startBeat })
        val end = ceil(track.notes.maxOf { it.startBeat + it.lengthBeats }).coerceAtLeast(start + 1.0)
        clips.add(ArrClip(ClipKind.NOTES, start, end))
    }
    val count = track.stepCount.coerceIn(1, 64)
    if ((0 until count).any { track.steps.getOrElse(it) { false } }) {
        clips.add(ArrClip(ClipKind.PATTERN, 0.0, count / 4.0))
    }
    return clips
}

/** Tracks that can receive each other's clips: drums with drums, melodic with melodic. */
private fun trackGroup(t: TrackData): Int = when (t.type) {
    TrackType.DRUM_MACHINE -> 0
    TrackType.AUDIO_IMPORT -> 2
    else -> 1
}

/** Vertical band a clip occupies in its lane (notes on top when a pattern shares the lane). */
private fun clipBand(kind: ClipKind, sharesLane: Boolean, h: Float): Pair<Float, Float> {
    val inset = 5f
    if (!sharesLane) return inset to (h - inset)
    return if (kind == ClipKind.NOTES) inset to (h * 0.58f - 2f) else (h * 0.58f + 2f) to (h - inset)
}

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
    modifier: Modifier = Modifier,
    /** Opens the piano roll for a track (tap on a notes clip). Defaults to [onEditInPianoRoll]. */
    onOpenPianoRoll: (Int) -> Unit = onEditInPianoRoll,
    /** Called after clips were moved/resized or a track was renamed (undo snapshot + redraw). */
    onClipsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dawFirebaseManager = remember(firebaseManager) { firebaseManager ?: FirebaseDawManager(context) }

    var showProjectManagementPanel by remember { mutableStateOf(false) }
    var expandedFxTrackIndex by remember { mutableStateOf<Int?>(null) }
    var expandedAutomationTrackIndex by remember { mutableStateOf<Int?>(null) }
    var showAddTrackMenu by remember { mutableStateOf(false) }
    var renameTrackIndex by remember { mutableStateOf<Int?>(null) }

    // Timeline view state
    var beatWidthDp by remember { mutableFloatStateOf(48f) }
    var snapIndex by remember { mutableIntStateOf(1) }
    val snapBeats = SNAP_OPTIONS[snapIndex.coerceIn(0, SNAP_OPTIONS.size - 1)].first
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val beatWidth = beatWidthDp.dp
    val beatPx = with(density) { beatWidth.toPx() }
    val headerWidthPx = with(density) { ARR_HEADER_WIDTH.toPx() }
    val rowPx = with(density) { ARR_ROW_HEIGHT.toPx() }
    val timelineWidth = beatWidth * ARR_TOTAL_BEATS

    // Bumped after in-place edits so the lanes redraw immediately
    var localRev by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val rev = localRev

    // Clip drag state (long-press on a clip, then drag)
    var dragTrack by remember { mutableIntStateOf(-1) }
    var dragKind by remember { mutableStateOf<ClipKind?>(null) }
    var dragResize by remember { mutableStateOf(false) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val laneTops = remember { HashMap<Int, Float>() }
    val extraHeights = remember { mutableStateMapOf<String, Int>() }
    val tapGuard = remember { BooleanArray(1) }

    val safeSelectedTrackIndex = selectedTrackIndex.coerceIn(0, (project.tracks.size - 1).coerceAtLeast(0))
    val anySolo = project.tracks.any { it.solo }

    fun applyZoom(factor: Float, focusXInBody: Float) {
        val old = beatWidthDp
        val next = (old * factor).coerceIn(MIN_BEAT_WIDTH_DP, MAX_BEAT_WIDTH_DP)
        if (next == old) return
        beatWidthDp = next
        val focus = (focusXInBody - headerWidthPx).coerceAtLeast(0f)
        val target = ((hScroll.value + focus) * (next / old) - focus).roundToInt().coerceAtLeast(0)
        // Scroll after the next layout, once the wider/narrower timeline has a new max
        scope.launch {
            withFrameNanos { }
            hScroll.scrollTo(target)
        }
    }

    /** Nearest track row to a y position inside the lanes column. */
    fun trackAtLaneY(y: Float): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (i in project.tracks.indices) {
            val top = laneTops[i] ?: continue
            val d = abs((top + rowPx / 2f) - y)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    fun resetDrag() {
        dragTrack = -1
        dragKind = null
        dragResize = false
        dragDx = 0f
        dragDy = 0f
    }

    fun commitDrag() {
        val srcIdx = dragTrack
        val kind = dragKind
        val src = project.tracks.getOrNull(srcIdx)
        if (src == null || kind == null) {
            resetDrag()
            return
        }
        val beatDelta = snapBeats * (dragDx / beatPx / snapBeats).roundToInt()
        val pointerY = (laneTops[srcIdx] ?: 0f) + dragStartY + dragDy
        val targetIdx = if (dragResize) srcIdx else trackAtLaneY(pointerY).takeIf { it >= 0 } ?: srcIdx
        val dst = project.tracks.getOrNull(targetIdx) ?: src
        val crossTrack = dst !== src
        val compatible = trackGroup(dst) == trackGroup(src)
        if (crossTrack && !compatible) {
            Toast.makeText(context, "That clip can't go on '${dst.name}' (drums move to drum tracks, notes to instrument tracks)", Toast.LENGTH_SHORT).show()
        }
        var changed = false
        when (kind) {
            ClipKind.NOTES -> if (src.notes.isNotEmpty()) {
                val minStart = src.notes.minOf { it.startBeat }
                val maxEnd = src.notes.maxOf { it.startBeat + it.lengthBeats }
                val delta = beatDelta.coerceIn(-minStart, (ARR_TOTAL_BEATS - maxEnd).coerceAtLeast(-minStart))
                if (crossTrack && compatible) {
                    val moved = src.notes.toList()
                    src.notes.clear()
                    moved.forEach { it.startBeat += delta }
                    dst.notes.addAll(moved)
                    onSelectTrack(targetIdx)
                    changed = true
                } else if (delta != 0.0) {
                    src.notes.forEach { it.startBeat += delta }
                    changed = true
                }
            }
            ClipKind.PATTERN -> {
                val count = src.stepCount.coerceIn(1, 64)
                if (dragResize) {
                    // Right edge: change the pattern length in whole beats (4 steps)
                    val stepPx = beatPx / 4f
                    val newCount = (count + ((dragDx / stepPx) / 4f).roundToInt() * 4).coerceIn(4, 64)
                    if (newCount != src.stepCount) {
                        src.stepCount = newCount
                        changed = true
                    }
                } else if (crossTrack && compatible) {
                    // Merge the pattern into the target lane, then clear the source pattern
                    for (i in 0 until count) {
                        if (src.steps[i]) {
                            dst.steps[i] = true
                            dst.stepVelocities[i] = src.stepVelocities[i]
                        }
                    }
                    if (dst.stepCount < count) dst.stepCount = count
                    for (i in 0 until count) src.steps[i] = false
                    onSelectTrack(targetIdx)
                    changed = true
                } else {
                    // Patterns loop, so a horizontal move nudges (rotates) the pattern by whole steps
                    val stepDelta = (beatDelta * 4).roundToInt()
                    val shift = ((stepDelta % count) + count) % count
                    if (shift != 0) {
                        val oldSteps = BooleanArray(count) { src.steps[it] }
                        val oldVels = FloatArray(count) { src.stepVelocities[it] }
                        for (i in 0 until count) {
                            val from = ((i - shift) % count + count) % count
                            src.steps[i] = oldSteps[from]
                            src.stepVelocities[i] = oldVels[from]
                        }
                        changed = true
                    }
                }
            }
            ClipKind.AUDIO -> Unit
        }
        resetDrag()
        if (changed) {
            localRev++
            onClipsChanged()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(SanwolfBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- Compact header: add, snap, zoom, gesture hint ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(SanwolfPanelElevated)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SanwolfGold)
                            .clickable { showAddTrackMenu = true }
                            .padding(horizontal = 10.dp)
                            .testTag("add_track_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Track", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    DropdownMenu(
                        expanded = showAddTrackMenu,
                        onDismissRequest = { showAddTrackMenu = false },
                        modifier = Modifier.background(SanwolfPanelElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Browse instruments…", color = SanwolfGold, fontWeight = FontWeight.Bold) },
                            onClick = { onOpenInstrumentBrowser(); showAddTrackMenu = false }
                        )
                        DropdownMenuItem(text = { Text("Quick synth", color = SanwolfCyan) }, onClick = { onAddTrack(TrackType.SYNTH); showAddTrackMenu = false })
                        DropdownMenuItem(text = { Text("Quick drum", color = SanwolfOrange) }, onClick = { onAddTrack(TrackType.DRUM_MACHINE); showAddTrackMenu = false })
                        DropdownMenuItem(text = { Text("Modular synth rack", color = SanwolfGold) }, onClick = { onAddTrack(TrackType.MODULAR_SYNTH); showAddTrackMenu = false })
                        DropdownMenuItem(text = { Text("Audio stem / vocal", color = SanwolfMagenta) }, onClick = { onAddTrack(TrackType.AUDIO_IMPORT); showAddTrackMenu = false })
                        DropdownMenuItem(text = { Text("Ambient riser FX", color = SanwolfLime) }, onClick = { onAddTrack(TrackType.RISER_TEXTURE); showAddTrackMenu = false })
                    }
                }

                // Snap grid
                Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfBlack)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                        .clickable { snapIndex = (snapIndex + 1) % SNAP_OPTIONS.size }
                        .semantics { contentDescription = "Snap to ${SNAP_OPTIONS[snapIndex].second}. Tap to change." }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Snap: ${SNAP_OPTIONS[snapIndex].second}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanwolfCyan)
                }

                IconButton(onClick = { applyZoom(1f / 1.25f, headerWidthPx) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", tint = SanwolfTextPrimary)
                }
                IconButton(onClick = { applyZoom(1.25f, headerWidthPx) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", tint = SanwolfTextPrimary)
                }

                Text(
                    text = "Tap a clip to edit · hold to move · pinch to zoom",
                    fontSize = 11.sp,
                    color = SanwolfTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // --- Ruler (tap to move the playhead) ---
            val textMeasurer = rememberTextMeasurer()
            Row(modifier = Modifier.fillMaxWidth().height(ARR_RULER_HEIGHT)) {
                Box(
                    modifier = Modifier
                        .width(ARR_HEADER_WIDTH)
                        .fillMaxHeight()
                        .background(SanwolfPanel)
                        .border(0.5.dp, SanwolfPanelBorder)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("TRACKS (${project.tracks.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfTextSecondary)
                }
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(SanwolfPanelElevated)
                        .pointerInput(beatPx, snapBeats) {
                            detectTapGestures { pos ->
                                val beat = ((pos.x + hScroll.value) / beatPx).toDouble()
                                val snapped = (floor(beat / snapBeats) * snapBeats).coerceIn(0.0, ARR_TOTAL_BEATS.toDouble())
                                onTimelineScrub(snapped)
                            }
                        }
                ) {
                    val scrollX = hScroll.value.toFloat()
                    for (b in 0..ARR_TOTAL_BEATS) {
                        val x = b * beatPx - scrollX
                        if (x < -beatPx * 4 || x > size.width + 2f) continue
                        val isBar = b % 4 == 0
                        drawLine(
                            color = if (isBar) Color(0xFF3A3D45) else Color(0xFF24262C),
                            start = Offset(x, if (isBar) 0f else size.height * 0.6f),
                            end = Offset(x, size.height),
                            strokeWidth = if (isBar) 1.5f else 1f
                        )
                        if (isBar && b < ARR_TOTAL_BEATS) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "${b / 4 + 1}",
                                topLeft = Offset(x + 4f, 2f),
                                style = TextStyle(color = SanwolfTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    val px = (currentBeat * beatPx).toFloat() - scrollX
                    if (px >= 0f && px <= size.width) {
                        drawLine(SanwolfGold, Offset(px, 0f), Offset(px, size.height), strokeWidth = 3f)
                    }
                }
            }

            // --- Body: headers + lanes share one vertical scroll; lanes scroll horizontally ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        // Two-finger pinch zooms the timeline horizontally. Runs in the Initial
                        // pass so the scroll containers below never see a pinch as a scroll.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f && !zoom.isNaN()) {
                                        applyZoom(zoom, event.calculateCentroid(useCurrent = true).x)
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                if (project.tracks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = SanwolfTextSecondary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No tracks yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap + Track to add an instrument or drum track, import audio, or press Record to capture your mic.",
                            fontSize = 12.sp,
                            color = SanwolfTextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                ) {
                    // Track headers
                    Column(modifier = Modifier.width(ARR_HEADER_WIDTH)) {
                        project.tracks.forEachIndexed { index, track ->
                            androidx.compose.runtime.key(track.id) {
                                TrackHeaderItem(
                                    track = track,
                                    isSelected = index == safeSelectedTrackIndex,
                                    isFxExpanded = expandedFxTrackIndex == index,
                                    isAutomationExpanded = expandedAutomationTrackIndex == index,
                                    anySoloActive = anySolo,
                                    onSelect = { onSelectTrack(index) },
                                    onEditPianoRoll = { onEditInPianoRoll(index) },
                                    onSelectInstrument = { onSelectInstrumentForTrack(index) },
                                    onVolumeChange = { onTrackVolumeChange(index, it); localRev++ },
                                    onMuteToggle = { onToggleMute(index) },
                                    onSoloToggle = { onToggleSolo(index) },
                                    onToggleFx = {
                                        expandedFxTrackIndex = if (expandedFxTrackIndex == index) null else index
                                    },
                                    onToggleAutomation = {
                                        expandedAutomationTrackIndex = if (expandedAutomationTrackIndex == index) null else index
                                    },
                                    onRename = { renameTrackIndex = index },
                                    onMoveUp = { onMoveTrackUp(index) },
                                    onMoveDown = { onMoveTrackDown(index) },
                                    onDelete = { onDeleteTrack(index) }
                                )
                                // Spacers that mirror the expanded rows on the lane side
                                val extraPx = extraHeights[track.id] ?: 0
                                if (extraPx > 0 && (expandedFxTrackIndex == index || expandedAutomationTrackIndex == index)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(with(density) { extraPx.toDp() })
                                            .background(Color(0xFF0D0D10))
                                            .border(0.5.dp, SanwolfPanelBorder)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = listOfNotNull(
                                                if (expandedFxTrackIndex == index) "FX rack" else null,
                                                if (expandedAutomationTrackIndex == index) "Automation" else null
                                            ).joinToString(" · "),
                                            fontSize = 11.sp,
                                            color = SanwolfTextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Lanes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(hScroll)
                    ) {
                        Column(modifier = Modifier.width(timelineWidth)) {
                            project.tracks.forEachIndexed { index, track ->
                                androidx.compose.runtime.key(track.id) {
                                    val isSelected = index == safeSelectedTrackIndex
                                    val isSilenced = track.muted || (anySolo && !track.solo)
                                    val clips = clipsFor(track)
                                    val isDragSource = dragTrack == index
                                    val dropTarget = if (dragTrack >= 0 && !dragResize) {
                                        trackAtLaneY((laneTops[dragTrack] ?: 0f) + dragStartY + dragDy)
                                    } else -1

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .zIndex(if (isDragSource) 1f else 0f)
                                            .onPlaced { laneTops[index] = it.positionInParent().y }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(ARR_ROW_HEIGHT)
                                                .background(
                                                    if (isSilenced) Color(0xFF0A0A0C)
                                                    else if (isSelected) SanwolfPanelElevated
                                                    else SanwolfBlack
                                                )
                                                .pointerInput(index, track.id, beatPx, localRev) {
                                                    detectTapGestures(
                                                        onPress = { tapGuard[0] = false },
                                                        onTap = { pos ->
                                                            if (tapGuard[0]) return@detectTapGestures
                                                            val hit = hitTestClip(clipsFor(track), pos, size.height.toFloat(), beatPx, 20.dp.toPx())
                                                            onSelectTrack(index)
                                                            when (hit?.clip?.kind) {
                                                                ClipKind.NOTES -> onOpenPianoRoll(index)
                                                                ClipKind.PATTERN -> onEditInPianoRoll(index)
                                                                else -> Unit
                                                            }
                                                        }
                                                    )
                                                }
                                                .pointerInput(index, track.id, beatPx, localRev) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { pos ->
                                                            tapGuard[0] = true
                                                            val hit = hitTestClip(clipsFor(track), pos, size.height.toFloat(), beatPx, 20.dp.toPx())
                                                            when {
                                                                hit == null -> resetDrag()
                                                                hit.clip.kind == ClipKind.AUDIO -> {
                                                                    resetDrag()
                                                                    Toast.makeText(context, "Audio clips start at bar 1 for now; moving them isn't supported yet", Toast.LENGTH_SHORT).show()
                                                                }
                                                                else -> {
                                                                    dragTrack = index
                                                                    dragKind = hit.clip.kind
                                                                    dragResize = hit.isResizeEdge && hit.clip.kind == ClipKind.PATTERN
                                                                    dragStartY = pos.y
                                                                    dragDx = 0f
                                                                    dragDy = 0f
                                                                    onSelectTrack(index)
                                                                }
                                                            }
                                                        },
                                                        onDrag = { change, amount ->
                                                            change.consume()
                                                            if (dragTrack == index) {
                                                                dragDx += amount.x
                                                                dragDy += amount.y
                                                            }
                                                        },
                                                        onDragEnd = { if (dragTrack == index) commitDrag() },
                                                        onDragCancel = { resetDrag() }
                                                    )
                                                }
                                        ) {
                                            LaneCanvas(
                                                track = track,
                                                clips = clips,
                                                beatPx = beatPx,
                                                isSilenced = isSilenced,
                                                isDropTarget = dropTarget == index && dragTrack != index,
                                                dropAllowed = dropTarget == index && dragTrack >= 0 &&
                                                    project.tracks.getOrNull(dragTrack)?.let { trackGroup(it) == trackGroup(track) } == true,
                                                dragKind = if (isDragSource) dragKind else null,
                                                dragResize = isDragSource && dragResize,
                                                dragDx = if (isDragSource) dragDx else 0f,
                                                dragDy = if (isDragSource) dragDy else 0f,
                                                snapBeats = snapBeats,
                                                currentBeat = currentBeat,
                                                revision = localRev
                                            )

                                            if (isSilenced) {
                                                Text(
                                                    text = if (track.muted) "MUTED" else "OFF (SOLO)",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (track.muted) SanwolfMagenta else SanwolfTextMuted,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                )
                                            }
                                        }

                                        val fxOpen = expandedFxTrackIndex == index
                                        val autoOpen = expandedAutomationTrackIndex == index
                                        if (fxOpen || autoOpen) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onSizeChanged { extraHeights[track.id] = it.height }
                                            ) {
                                                if (fxOpen) VstPluginRackRow(track = track)
                                                if (autoOpen) {
                                                    AutomationLaneRow(
                                                        track = track,
                                                        totalBeats = ARR_TOTAL_BEATS,
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
                    }
                }
            }
        }

        // --- Rename dialog ---
        renameTrackIndex?.let { idx ->
            val track = project.tracks.getOrNull(idx)
            if (track == null) {
                renameTrackIndex = null
            } else {
                SidebarNameDialog(
                    title = "Rename track",
                    initialValue = track.name,
                    confirmLabel = "Rename",
                    fieldLabel = "Track name",
                    onConfirm = { newName ->
                        track.name = newName
                        renameTrackIndex = null
                        localRev++
                        onClipsChanged()
                    },
                    onDismiss = { renameTrackIndex = null }
                )
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

/** Finds the clip under [pos] in a lane of height [h]. The last [edgePx] of a clip is its resize edge. */
private fun hitTestClip(clips: List<ArrClip>, pos: Offset, h: Float, beatPx: Float, edgePx: Float): ClipHit? {
    val shares = clips.count { it.kind != ClipKind.AUDIO } > 1
    // Check patterns/notes before audio; later clips are drawn on top
    for (clip in clips.reversed()) {
        val x0 = (clip.startBeat * beatPx).toFloat()
        val x1 = (clip.endBeat * beatPx).toFloat()
        val (y0, y1) = clipBand(clip.kind, shares, h)
        if (pos.x >= x0 && pos.x <= x1 + edgePx * 0.4f && pos.y >= y0 - 4f && pos.y <= y1 + 4f) {
            return ClipHit(clip, isResizeEdge = pos.x >= x1 - edgePx)
        }
    }
    return null
}

/** Draws one lane: grid, clips (with mini note / step / waveform previews), drag ghost and playhead. */
@Composable
private fun LaneCanvas(
    track: TrackData,
    clips: List<ArrClip>,
    beatPx: Float,
    isSilenced: Boolean,
    isDropTarget: Boolean,
    dropAllowed: Boolean,
    dragKind: ClipKind?,
    dragResize: Boolean,
    dragDx: Float,
    dragDy: Float,
    snapBeats: Double,
    currentBeat: Double,
    @Suppress("UNUSED_PARAMETER") revision: Int
) {
    val baseColor = Color(track.colorHex)
    val color = if (isSilenced) baseColor.copy(alpha = 0.35f) else baseColor
    Canvas(modifier = Modifier.fillMaxSize()) {
        val h = size.height
        // Grid
        for (b in 0..ARR_TOTAL_BEATS) {
            val x = b * beatPx
            val isBar = b % 4 == 0
            drawLine(
                color = if (isBar) Color(0xFF282A30) else Color(0xFF16171A),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = if (isBar) 1.5f else 0.8f
            )
        }
        drawLine(Color(0xFF1E2026), Offset(0f, h - 0.5f), Offset(size.width, h - 0.5f), strokeWidth = 1f)

        if (isDropTarget) {
            drawRect(
                color = (if (dropAllowed) SanwolfCyan else SanwolfMagenta).copy(alpha = 0.12f),
                size = Size(size.width, h)
            )
        }

        val shares = clips.count { it.kind != ClipKind.AUDIO } > 1
        for (clip in clips) {
            val (y0, y1) = clipBand(clip.kind, shares, h)
            val x0 = (clip.startBeat * beatPx).toFloat()
            val x1 = (clip.endBeat * beatPx).toFloat()
            val isDragged = dragKind == clip.kind

            // Pattern loops: ghost the repeats across the timeline
            if (clip.kind == ClipKind.PATTERN) {
                val cycle = (clip.endBeat - clip.startBeat).coerceAtLeast(0.25)
                var rep = clip.endBeat
                while (rep < ARR_TOTAL_BEATS) {
                    val rx0 = (rep * beatPx).toFloat()
                    val rx1 = ((rep + cycle).coerceAtMost(ARR_TOTAL_BEATS.toDouble()) * beatPx).toFloat()
                    drawRoundRect(
                        color = color.copy(alpha = 0.10f),
                        topLeft = Offset(rx0 + 1f, y0),
                        size = Size((rx1 - rx0 - 2f).coerceAtLeast(2f), y1 - y0),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    rep += cycle
                }
            }

            drawClipBody(track, clip, x0, x1, y0, y1, beatPx, color, alpha = if (isDragged) 0.35f else 1f)

            if (isDragged) {
                // Ghost at the snapped destination
                val snappedBeats = snapBeats * (dragDx / beatPx / snapBeats).roundToInt()
                if (dragResize) {
                    val stepPx = beatPx / 4f
                    val count = track.stepCount.coerceIn(1, 64)
                    val newCount = (count + ((dragDx / stepPx) / 4f).roundToInt() * 4).coerceIn(4, 64)
                    val gx1 = (newCount / 4.0 * beatPx).toFloat()
                    drawRoundRect(
                        color = SanwolfGold,
                        topLeft = Offset(x0, y0),
                        size = Size(gx1 - x0, y1 - y0),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 3f)
                    )
                } else {
                    val off = (snappedBeats * beatPx).toFloat()
                    drawClipBody(track, clip, x0 + off, x1 + off, y0 + dragDy, y1 + dragDy, beatPx, color, alpha = 0.9f)
                    drawRoundRect(
                        color = SanwolfGold,
                        topLeft = Offset(x0 + off, y0 + dragDy),
                        size = Size(x1 - x0, y1 - y0),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }

        // Playhead
        val px = (currentBeat * beatPx).toFloat()
        drawLine(SanwolfGold, Offset(px, 0f), Offset(px, h), strokeWidth = 2f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClipBody(
    track: TrackData,
    clip: ArrClip,
    x0: Float,
    x1: Float,
    y0: Float,
    y1: Float,
    beatPx: Float,
    color: Color,
    alpha: Float
) {
    val w = (x1 - x0).coerceAtLeast(6f)
    val ch = y1 - y0
    drawRoundRect(
        color = color.copy(alpha = 0.22f * alpha),
        topLeft = Offset(x0, y0),
        size = Size(w, ch),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = color.copy(alpha = 0.85f * alpha),
        topLeft = Offset(x0, y0),
        size = Size(w, ch),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 1.5f)
    )
    when (clip.kind) {
        ClipKind.NOTES -> {
            val notes = track.notes
            if (notes.isEmpty()) return
            val lo = notes.minOf { it.pitch }
            val hi = notes.maxOf { it.pitch }
            val span = (hi - lo).coerceAtLeast(1)
            val shift = x0 - (clip.startBeat * beatPx).toFloat()
            for (n in notes) {
                val nx = (n.startBeat * beatPx).toFloat() + shift
                val nw = (n.lengthBeats * beatPx).toFloat().coerceAtLeast(3f)
                val ny = y0 + 4f + (1f - (n.pitch - lo).toFloat() / span) * (ch - 12f)
                drawRect(color.copy(alpha = alpha), Offset(nx, ny), Size(nw, 4f))
            }
        }
        ClipKind.PATTERN -> {
            val count = track.stepCount.coerceIn(1, 64)
            val stepPx = beatPx / 4f
            val shift = x0 - (clip.startBeat * beatPx).toFloat()
            for (i in 0 until count) {
                if (track.steps.getOrElse(i) { false }) {
                    val vel = track.stepVelocities.getOrElse(i) { 0.8f }.coerceIn(0.1f, 1f)
                    val bh = (ch - 8f) * vel
                    drawRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(i * stepPx + shift + stepPx * 0.2f, y1 - 4f - bh),
                        size = Size((stepPx * 0.6f).coerceAtLeast(2f), bh)
                    )
                }
            }
            // Resize grip on the right edge
            drawRect(color.copy(alpha = 0.6f * alpha), Offset(x0 + w - 5f, y0 + ch * 0.3f), Size(3f, ch * 0.4f))
        }
        ClipKind.AUDIO -> {
            val peaks = track.waveformPeaks
            val mid = y0 + ch / 2f
            if (peaks != null && peaks.isNotEmpty()) {
                val n = peaks.size
                for (i in 0 until n) {
                    val x = x0 + w * i / n
                    val a = peaks[i].coerceIn(0f, 1f) * (ch / 2f - 3f)
                    drawLine(color.copy(alpha = alpha), Offset(x, mid - a), Offset(x, mid + a), strokeWidth = 1.2f)
                }
            } else {
                drawLine(color.copy(alpha = 0.6f * alpha), Offset(x0, mid), Offset(x0 + w, mid), strokeWidth = 1.5f)
            }
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
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onToggleFx: () -> Unit,
    onToggleAutomation: () -> Unit,
    onRename: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDelete: () -> Unit
) {
    val trackColor = Color(track.colorHex)
    val isSilencedBySolo = anySoloActive && !track.solo
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ARR_ROW_HEIGHT)
            .background(if (isSelected) SanwolfPanelElevated else SanwolfPanel)
            .border(0.5.dp, SanwolfPanelBorder)
            .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(trackColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, end = 2.dp)
        ) {
            // One clear name: the instrument's name unless the user renamed the track
            Text(
                text = track.name,
                color = if (isSelected) SanwolfTextPrimary else SanwolfTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("track_name_${track.id}")
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderToggle("M", track.muted, SanwolfMagenta, Color.White, "track_mute_button_${track.id}",
                    if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}", onMuteToggle)
                Spacer(modifier = Modifier.width(3.dp))
                HeaderToggle("S", track.solo, SanwolfGold, SanwolfBlack, "track_solo_button_${track.id}",
                    if (track.solo) "Unsolo ${track.name}" else "Solo ${track.name}", onSoloToggle)
                Slider(
                    value = track.volume.coerceIn(0f, 1.2f),
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1.2f,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .semantics { contentDescription = "${track.name} volume" },
                    colors = SliderDefaults.colors(
                        thumbColor = if (track.muted || isSilencedBySolo) SanwolfTextMuted else trackColor,
                        activeTrackColor = if (track.muted || isSilencedBySolo) SanwolfTextMuted else trackColor,
                        inactiveTrackColor = SanwolfBlack
                    )
                )
            }
        }
        // Secondary piano-roll entry (tapping a notes clip is the primary one)
        IconButton(onClick = onEditPianoRoll, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MusicNote, contentDescription = "Edit notes", tint = SanwolfGold, modifier = Modifier.size(18.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Track options for ${track.name}", tint = SanwolfTextSecondary, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(SanwolfPanelElevated)
            ) {
                DropdownMenuItem(
                    text = { Text("Change instrument…", color = SanwolfGold) },
                    onClick = { menuOpen = false; onSelectInstrument() },
                    modifier = Modifier.testTag("track_instrument_button_${track.id}")
                )
                DropdownMenuItem(text = { Text("Edit notes", color = SanwolfTextPrimary) }, onClick = { menuOpen = false; onEditPianoRoll() })
                DropdownMenuItem(text = { Text("Rename…", color = SanwolfTextPrimary) }, onClick = { menuOpen = false; onRename() })
                HorizontalDivider(color = SanwolfPanelBorder)
                DropdownMenuItem(
                    text = { Text(if (isFxExpanded) "Hide FX rack" else "Show FX rack", color = SanwolfTextPrimary) },
                    onClick = { menuOpen = false; onToggleFx() }
                )
                DropdownMenuItem(
                    text = { Text(if (isAutomationExpanded) "Hide automation" else "Show automation", color = SanwolfTextPrimary) },
                    onClick = { menuOpen = false; onToggleAutomation() }
                )
                HorizontalDivider(color = SanwolfPanelBorder)
                DropdownMenuItem(text = { Text("Move up", color = SanwolfTextPrimary) }, onClick = { menuOpen = false; onMoveUp() })
                DropdownMenuItem(text = { Text("Move down", color = SanwolfTextPrimary) }, onClick = { menuOpen = false; onMoveDown() })
                DropdownMenuItem(text = { Text("Delete track", color = SanwolfMagenta) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

@Composable
private fun HeaderToggle(
    label: String,
    active: Boolean,
    activeColor: Color,
    activeText: Color,
    tag: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 30.dp, height = 28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) activeColor else SanwolfBlack.copy(alpha = 0.7f))
            .border(if (active) 1.dp else 0.5.dp, if (active) activeColor else SanwolfPanelBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            color = if (active) activeText else SanwolfTextSecondary
        )
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
                    .pointerInput(currentLane, totalBeats, localRevision, beatWidth) {
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
                    .pointerInput(currentLane, totalBeats, localRevision, beatWidth) {
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
