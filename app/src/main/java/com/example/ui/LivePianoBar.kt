package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SanwolfAudioEngine
import com.example.model.TrackData
import com.example.ui.theme.*

@Composable
fun LivePianoBar(
    selectedTrack: TrackData?,
    currentBeat: Double,
    isPlaying: Boolean,
    audioEngine: SanwolfAudioEngine,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    isRecording: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    bpm: Int = 120,
    onBpmChange: (Int) -> Unit = {},
    onPlayToggle: () -> Unit = {},
    onStop: () -> Unit = {},
    onRecordToggle: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onKeyTriggered: (pitch: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sanwolf_daw_prefs", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var baseOctave by remember { mutableIntStateOf(prefs.getInt(PREF_PIANO_OCTAVE, 3).coerceIn(1, 6)) }
    var userPressedPitch by remember { mutableStateOf<Int?>(null) }
    // Size snaps to Collapsed / Compact / Medium / Large and is remembered across launches
    val initialSize = PianoSize.values().getOrNull(prefs.getInt(PREF_PIANO_SIZE, PianoSize.MEDIUM.ordinal)) ?: PianoSize.MEDIUM
    var pianoSize by remember { mutableStateOf(if (isExpanded) initialSize else PianoSize.COLLAPSED) }
    var lastOpenSize by remember { mutableStateOf(if (initialSize == PianoSize.COLLAPSED) PianoSize.MEDIUM else initialSize) }
    var octaveCount by remember { mutableIntStateOf(prefs.getInt(PREF_PIANO_OCTAVES, 2).coerceIn(1, 4)) }
    val barHeight = remember { Animatable(pianoSize.heightDp) }

    // Gesture handlers outlive a composition: read the latest parent state through these
    val latestIsExpanded by rememberUpdatedState(isExpanded)
    val latestOnToggleExpanded by rememberUpdatedState(onToggleExpanded)

    fun snapPianoTo(size: PianoSize) {
        pianoSize = size
        if (size != PianoSize.COLLAPSED) lastOpenSize = size
        prefs.edit().putInt(PREF_PIANO_SIZE, size.ordinal).apply()
        scope.launch { barHeight.animateTo(size.heightDp, spring(stiffness = Spring.StiffnessMediumLow)) }
        if ((size != PianoSize.COLLAPSED) != latestIsExpanded) latestOnToggleExpanded()
    }

    val keyboardVisible = pianoSize != PianoSize.COLLAPSED || barHeight.value > PianoSize.COLLAPSED.heightDp + 8f

    val startPitch = baseOctave * 12 + 12
    val endPitch = startPitch + 12 * octaveCount

    val playingPitches = remember(currentBeat, isPlaying, selectedTrack) {
        if (!isPlaying || selectedTrack == null) emptySet()
        else {
            selectedTrack.notes.filter { note ->
                note.startBeat <= currentBeat && currentBeat < (note.startBeat + note.lengthBeats)
            }.map { it.pitch }.toSet()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight.value.dp)
            .background(SanwolfBlack)
            .border(androidx.compose.foundation.BorderStroke(1.dp, SanwolfPanelBorder))
    ) {
        // Resize handle: drag to any height, release to snap to the nearest size; tap cycles sizes
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(SanwolfPanelElevated)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (barHeight.value - dragAmount / density)
                                .coerceIn(PianoSize.COLLAPSED.heightDp, PianoSize.LARGE.heightDp + 20f)
                            scope.launch { barHeight.snapTo(next) }
                        },
                        onDragEnd = { snapPianoTo(PianoSize.nearest(barHeight.value)) },
                        onDragCancel = { snapPianoTo(PianoSize.nearest(barHeight.value)) }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        val order = PianoSize.values()
                        snapPianoTo(order[(pianoSize.ordinal + 1) % order.size])
                    })
                }
                .semantics { contentDescription = "Resize piano. Drag up or down, or tap to change size. Now ${pianoSize.label}." }
                .testTag("piano_resize_handle"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SanwolfTextMuted)
            )
        }

        // --- Header / Toggle Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(SanwolfPanel)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SanwolfGold,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "LIVE PIANO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfGold,
                    letterSpacing = 0.5.sp
                )
                if (selectedTrack != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(SanwolfPanelElevated)
                            .border(0.5.dp, Color(selectedTrack.colorHex), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = selectedTrack.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfTextPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Octave & Transport Controls (scrolls horizontally on narrow screens)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfPanelElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("OCT $baseOctave", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanwolfCyan)
                    IconButton(
                        onClick = {
                            if (baseOctave > 1) baseOctave--
                            prefs.edit().putInt(PREF_PIANO_OCTAVE, baseOctave).apply()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Octave Down", tint = SanwolfTextPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            if (baseOctave < 6) baseOctave++
                            prefs.edit().putInt(PREF_PIANO_OCTAVE, baseOctave).apply()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Octave Up", tint = SanwolfTextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfPanelElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("OCTAVES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanwolfGold)
                    IconButton(
                        onClick = {
                            if (octaveCount > 1) {
                                octaveCount--
                                prefs.edit().putInt(PREF_PIANO_OCTAVES, octaveCount).apply()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Fewer octaves (wider keys)", tint = SanwolfTextPrimary, modifier = Modifier.size(18.dp))
                    }
                    Text("$octaveCount", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = SanwolfTextPrimary)
                    IconButton(
                        onClick = {
                            if (octaveCount < 4) {
                                octaveCount++
                                prefs.edit().putInt(PREF_PIANO_OCTAVES, octaveCount).apply()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "More octaves (narrower keys)", tint = SanwolfTextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PianoBarTransportButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label = if (isPlaying) "Pause" else "Play",
                        contentDescription = if (isPlaying) "Pause playback" else "Play",
                        tint = if (isPlaying) SanwolfLime else SanwolfTextPrimary,
                        onClick = onPlayToggle
                    )
                    PianoBarTransportButton(
                        icon = Icons.Default.Stop,
                        label = "Stop",
                        contentDescription = "Stop playback",
                        tint = SanwolfMagenta,
                        onClick = onStop
                    )
                    PianoBarTransportButton(
                        icon = Icons.Default.FiberManualRecord,
                        label = if (isRecording) "Stop rec" else "Record",
                        contentDescription = if (isRecording) "Stop recording" else "Record from microphone",
                        tint = Color.Red,
                        highlighted = isRecording,
                        onClick = onRecordToggle
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfPanelElevated)
                        .clickable {
                            val nextBpm = if (bpm >= 150) 110 else bpm + 5
                            onBpmChange(nextBpm)
                        }
                        .heightIn(min = 40.dp)
                        .semantics { contentDescription = "Tempo $bpm BPM. Tap to raise by 5." }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "BPM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfTextSecondary
                    )
                    Text(
                        text = "$bpm +5",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = SanwolfGold
                    )
                }

                IconButton(
                    onClick = { snapPianoTo(if (pianoSize == PianoSize.COLLAPSED) lastOpenSize else PianoSize.COLLAPSED) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (pianoSize != PianoSize.COLLAPSED) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (pianoSize != PianoSize.COLLAPSED) "Hide piano keyboard" else "Show piano keyboard",
                        tint = SanwolfTextPrimary
                    )
                }
            }
        }

        // --- Keyboard Body: white keys share the width, black keys sit on top ---
        if (keyboardVisible) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(SanwolfBlack)
                    .padding(4.dp)
            ) {
                val whitePitches = (startPitch..endPitch).filter { !isBlackKey(it) }
                val minWhite = 28.dp
                val fitWhite = maxWidth / whitePitches.size
                val whiteW = if (fitWhite < minWhite) minWhite else fitWhite
                val blackW = whiteW * 0.6f
                val keysWidth = whiteW * whitePitches.size
                val showLabels = whiteW >= 30.dp

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Box(modifier = Modifier.width(keysWidth).fillMaxHeight()) {
                        // White keys
                        whitePitches.forEachIndexed { idx, pitch ->
                            val lit = playingPitches.contains(pitch) || userPressedPitch == pitch
                            PianoKey(
                                pitch = pitch,
                                isBlack = false,
                                lit = lit,
                                label = if (showLabels && pitch % 12 == 0) getNoteName(pitch) else null,
                                modifier = Modifier
                                    .offset(x = whiteW * idx)
                                    .width(whiteW)
                                    .fillMaxHeight(),
                                onPress = { p ->
                                    userPressedPitch = p
                                    audioEngine.triggerMidiNote(p, selectedTrack)
                                    onKeyTriggered(p)
                                },
                                onRelease = { p -> if (userPressedPitch == p) userPressedPitch = null }
                            )
                        }
                        // Black keys (drawn last so they win the touch)
                        var whiteIndex = 0
                        for (pitch in startPitch..endPitch) {
                            if (!isBlackKey(pitch)) {
                                whiteIndex++
                                continue
                            }
                            val lit = playingPitches.contains(pitch) || userPressedPitch == pitch
                            PianoKey(
                                pitch = pitch,
                                isBlack = true,
                                lit = lit,
                                label = null,
                                modifier = Modifier
                                    .offset(x = whiteW * whiteIndex - blackW / 2)
                                    .width(blackW)
                                    .fillMaxHeight(0.62f),
                                onPress = { p ->
                                    userPressedPitch = p
                                    audioEngine.triggerMidiNote(p, selectedTrack)
                                    onKeyTriggered(p)
                                },
                                onRelease = { p -> if (userPressedPitch == p) userPressedPitch = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PianoKey(
    pitch: Int,
    isBlack: Boolean,
    lit: Boolean,
    label: String?,
    modifier: Modifier,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit
) {
    val keyColor = when {
        lit -> SanwolfCyan
        isBlack -> Color(0xFF14161B)
        else -> Color(0xFFE2E8F0)
    }
    Box(
        modifier = modifier
            .padding(horizontal = if (isBlack) 0.dp else 0.5.dp)
            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
            .background(keyColor)
            .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
            .pointerInput(pitch) {
                detectTapGestures(
                    onPress = {
                        onPress(pitch)
                        try {
                            awaitRelease()
                        } finally {
                            onRelease(pitch)
                        }
                    }
                )
            }
            .semantics { contentDescription = getNoteName(pitch) },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/** Snap sizes for the piano dock (total height incl. handle + header). */
private enum class PianoSize(val heightDp: Float, val label: String) {
    COLLAPSED(68f, "collapsed"),
    COMPACT(140f, "compact"),
    MEDIUM(210f, "medium"),
    LARGE(300f, "large");

    companion object {
        fun nearest(h: Float): PianoSize = values().minByOrNull { kotlin.math.abs(it.heightDp - h) } ?: MEDIUM
    }
}

private const val PREF_PIANO_SIZE = "piano_dock_size"
private const val PREF_PIANO_OCTAVES = "piano_dock_octaves"
private const val PREF_PIANO_OCTAVE = "piano_dock_base_octave"

@Composable
private fun PianoBarTransportButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlighted) tint.copy(alpha = 0.25f) else SanwolfPanelElevated)
            .border(1.dp, if (highlighted) tint else SanwolfPanelBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SanwolfTextPrimary
        )
    }
}

private fun isBlackKey(pitch: Int): Boolean {
    val noteInOctave = pitch % 12
    return noteInOctave == 1 || noteInOctave == 3 || noteInOctave == 6 || noteInOctave == 8 || noteInOctave == 10
}

private fun getNoteName(pitch: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val noteName = names[pitch % 12]
    val octave = (pitch / 12) - 1
    return "$noteName$octave"
}
