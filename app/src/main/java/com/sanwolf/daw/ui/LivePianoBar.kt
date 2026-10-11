package com.sanwolf.daw.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanwolf.daw.audio.SanwolfAudioEngine
import com.sanwolf.daw.model.TrackData
import com.sanwolf.daw.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tempo range accepted by the strip (same as Song info). */
private const val MIN_BPM = 40
private const val MAX_BPM = 300

/**
 * Bottom live piano. A fixed ("stand still") transport strip that never scrolls or collapses:
 * Play/Pause, Stop, Record-the-keys, BPM -/+ (hold to repeat, tap the number to type it),
 * metronome, playhead position and a show/hide-keys arrow. The keys sit underneath; hiding the
 * piano hides only the keys. Octave, range, key size and velocity live in the right panel's
 * Keys tab ([PianoKeyboardSettings]).
 */
@Composable
fun LivePianoBar(
    selectedTrack: TrackData?,
    currentBeat: Double,
    isPlaying: Boolean,
    audioEngine: SanwolfAudioEngine,
    settings: PianoKeyboardSettings,
    modifier: Modifier = Modifier,
    isPianoRecording: Boolean = false,
    bpm: Int = 120,
    beatsPerBar: Int = 4,
    isMetronomeOn: Boolean = false,
    onPlayToggle: () -> Unit = {},
    onStop: () -> Unit = {},
    onPianoRecordToggle: () -> Unit = {},
    /** Live tempo change while a -/+ button is held (no undo entry per step). */
    onBpmChange: (Int) -> Unit = {},
    /** Tempo edit finished (release, or typed value): one undo entry. */
    onBpmCommit: () -> Unit = {},
    onMetronomeToggle: () -> Unit = {},
    onOpenKeyboardSettings: () -> Unit = {},
    onKeyDown: (pitch: Int) -> Unit = {},
    onKeyUp: (pitch: Int) -> Unit = {}
) {
    var userPressedPitch by remember { mutableStateOf<Int?>(null) }
    val keysHeight by animateDpAsState(settings.keysSize.keysHeightDp.dp, label = "pianoKeysHeight")
    val startPitch = settings.baseOctave * 12 + 12
    val endPitch = startPitch + 12 * settings.octaveCount

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
            .background(SanwolfBlack)
            .border(androidx.compose.foundation.BorderStroke(1.dp, SanwolfPanelBorder))
    ) {
        PianoTransportStrip(
            selectedTrack = selectedTrack,
            currentBeat = currentBeat,
            isPlaying = isPlaying,
            isPianoRecording = isPianoRecording,
            bpm = bpm,
            beatsPerBar = beatsPerBar,
            isMetronomeOn = isMetronomeOn,
            keysVisible = settings.keysVisible,
            onPlayToggle = onPlayToggle,
            onStop = onStop,
            onPianoRecordToggle = onPianoRecordToggle,
            onBpmChange = onBpmChange,
            onBpmCommit = onBpmCommit,
            onMetronomeToggle = onMetronomeToggle,
            onToggleKeys = { settings.toggleKeys() },
            onOpenKeyboardSettings = onOpenKeyboardSettings
        )

        // --- Keys: white keys share the width, black keys sit on top ---
        if (keysHeight > 1.dp) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keysHeight)
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
                val press: (Int) -> Unit = { p ->
                    userPressedPitch = p
                    audioEngine.triggerMidiNote(p, selectedTrack, settings.velocity)
                    onKeyDown(p)
                }
                val release: (Int) -> Unit = { p ->
                    if (userPressedPitch == p) userPressedPitch = null
                    onKeyUp(p)
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Box(modifier = Modifier.width(keysWidth).fillMaxHeight()) {
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
                                onPress = press,
                                onRelease = release
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
                                onPress = press,
                                onRelease = release
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The fixed transport strip. Never scrolls; the track chip takes whatever width is left. */
@Composable
private fun PianoTransportStrip(
    selectedTrack: TrackData?,
    currentBeat: Double,
    isPlaying: Boolean,
    isPianoRecording: Boolean,
    bpm: Int,
    beatsPerBar: Int,
    isMetronomeOn: Boolean,
    keysVisible: Boolean,
    onPlayToggle: () -> Unit,
    onStop: () -> Unit,
    onPianoRecordToggle: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onBpmCommit: () -> Unit,
    onMetronomeToggle: () -> Unit,
    onToggleKeys: () -> Unit,
    onOpenKeyboardSettings: () -> Unit
) {
    // project.bpm is not Compose state: keep what we show here and resync when the parent changes it
    var shownBpm by remember(bpm) { mutableIntStateOf(bpm.coerceIn(MIN_BPM, MAX_BPM)) }
    var showBpmEntry by remember { mutableStateOf(false) }

    fun stepBpm(delta: Int) {
        val next = (shownBpm + delta).coerceIn(MIN_BPM, MAX_BPM)
        if (next != shownBpm) {
            shownBpm = next
            onBpmChange(next)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.pianoHeaderHeight)
            .background(SanwolfPanel)
            .padding(horizontal = 4.dp)
            .testTag("piano_transport_strip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Track the keys play (tap: keyboard settings in the right panel)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(SanwolfPanelElevated)
                .border(0.5.dp, selectedTrack?.let { Color(it.colorHex) } ?: SanwolfPanelBorder, RoundedCornerShape(4.dp))
                .clickable(onClick = onOpenKeyboardSettings)
                .semantics { contentDescription = "Keyboard settings for ${selectedTrack?.name ?: "no track"}" }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = selectedTrack?.name ?: "No track",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SanwolfTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        StripIconButton(
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            description = if (isPlaying) "Pause" else "Play",
            tint = if (isPlaying) SanwolfLime else SanwolfTextPrimary,
            onClick = onPlayToggle,
            tag = "strip_play_pause"
        )
        StripIconButton(
            icon = Icons.Default.Stop,
            description = "Stop",
            tint = SanwolfMagenta,
            onClick = onStop,
            tag = "strip_stop"
        )
        StripIconButton(
            icon = Icons.Default.FiberManualRecord,
            description = if (isPianoRecording) "Stop recording the piano" else "Record the piano into the selected track",
            tint = Color.Red,
            highlighted = isPianoRecording,
            onClick = onPianoRecordToggle,
            tag = "strip_piano_record"
        )

        // Tempo: -  128  +   (hold -/+ to repeat, tap the number to type)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SanwolfPanelElevated)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
        ) {
            RepeatStepButton(Icons.Default.Remove, "Tempo down", onStep = { stepBpm(-1) }, onFinished = onBpmCommit)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(34.dp)
                    .clickable { showBpmEntry = true }
                    .semantics { contentDescription = "Tempo $shownBpm BPM. Tap to type." }
            ) {
                Text("$shownBpm", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = SanwolfGold, maxLines = 1)
                Text("BPM", fontSize = 8.sp, color = SanwolfTextMuted, maxLines = 1)
            }
            RepeatStepButton(Icons.Default.Add, "Tempo up", onStep = { stepBpm(1) }, onFinished = onBpmCommit)
        }

        StripIconButton(
            icon = Icons.Default.Timer,
            description = if (isMetronomeOn) "Metronome off" else "Metronome on",
            tint = if (isMetronomeOn) SanwolfCyan else SanwolfTextSecondary,
            highlighted = isMetronomeOn,
            onClick = onMetronomeToggle,
            tag = "strip_metronome"
        )

        // Playhead position, bar.beat
        val perBar = beatsPerBar.coerceAtLeast(1)
        val beatSafe = currentBeat.coerceAtLeast(0.0)
        val barNo = (beatSafe / perBar).toInt() + 1
        val beatNo = (beatSafe % perBar).toInt() + 1
        Text(
            text = "$barNo.$beatNo",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = SanwolfTextSecondary,
            maxLines = 1,
            modifier = Modifier
                .width(34.dp)
                .semantics { contentDescription = "Position bar $barNo beat $beatNo" }
        )

        StripIconButton(
            icon = if (keysVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            description = if (keysVisible) "Hide piano keys" else "Show piano keys",
            tint = SanwolfTextPrimary,
            onClick = onToggleKeys,
            tag = "strip_toggle_keys"
        )
    }

    if (showBpmEntry) {
        var text by remember { mutableStateOf(shownBpm.toString()) }
        AlertDialog(
            onDismissRequest = { showBpmEntry = false },
            title = { Text("Tempo (BPM)") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("$MIN_BPM to $MAX_BPM") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val typed = text.toIntOrNull()
                    if (typed != null) {
                        val clamped = typed.coerceIn(MIN_BPM, MAX_BPM)
                        shownBpm = clamped
                        onBpmChange(clamped)
                        onBpmCommit()
                    }
                    showBpmEntry = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showBpmEntry = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StripIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    tag: String,
    highlighted: Boolean = false,
    size: Dp = 32.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlighted) tint.copy(alpha = 0.25f) else SanwolfPanelElevated)
            .border(1.dp, if (highlighted) tint else SanwolfPanelBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** Steps once on press, then repeats while held (after 400 ms, every 70 ms). */
@Composable
private fun RepeatStepButton(
    icon: ImageVector,
    description: String,
    onStep: () -> Unit,
    onFinished: () -> Unit
) {
    val latestStep by rememberUpdatedState(onStep)
    val latestFinished by rememberUpdatedState(onFinished)
    Box(
        modifier = Modifier
            .width(26.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        latestStep()
                        coroutineScope {
                            val repeater = launch {
                                delay(400)
                                while (true) {
                                    latestStep()
                                    delay(70)
                                }
                            }
                            tryAwaitRelease()
                            repeater.cancel()
                        }
                        latestFinished()
                    }
                )
            }
            .semantics { contentDescription = "$description. Hold to repeat." },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SanwolfTextPrimary, modifier = Modifier.size(16.dp))
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
