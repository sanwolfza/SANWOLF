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
    var baseOctave by remember { mutableIntStateOf(3) }
    var userPressedPitch by remember { mutableStateOf<Int?>(null) }
    var barHeight by remember { mutableFloatStateOf(190f) }
    var keyWidth by remember { mutableFloatStateOf(44f) }

    val startPitch = baseOctave * 12 + 12
    val endPitch = startPitch + 24

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
            .height(barHeight.dp)
            .background(SanwolfBlack)
            .border(androidx.compose.foundation.BorderStroke(1.dp, SanwolfPanelBorder))
    ) {
        // Draggable Resize Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(SanwolfPanelElevated)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        barHeight = (barHeight - dragAmount).coerceIn(110f, 340f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
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
                            text = "${selectedTrack.name.uppercase()} (${selectedTrack.synthPresetName})",
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
                        onClick = { if (baseOctave > 1) baseOctave-- },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Octave Down", tint = SanwolfTextPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { if (baseOctave < 6) baseOctave++ },
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
                    Text("KEYS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanwolfGold)
                    IconButton(
                        onClick = { if (keyWidth > 25f) keyWidth -= 5f },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Narrower keys", tint = SanwolfTextPrimary, modifier = Modifier.size(18.dp))
                    }
                    Text("${keyWidth.toInt()}dp", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = SanwolfTextPrimary)
                    IconButton(
                        onClick = { if (keyWidth < 120f) keyWidth += 5f },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Wider Keys", tint = SanwolfTextPrimary, modifier = Modifier.size(18.dp))
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
                    onClick = onToggleExpanded,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isExpanded) "Hide piano keyboard" else "Show piano keyboard",
                        tint = SanwolfTextPrimary
                    )
                }
            }
        }

        // --- Keyboard Body ---
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier.weight(1f)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SanwolfBlack)
                    .padding(4.dp)
            ) {
                val totalKeys = 25 // 2 octaves + 1 note
                val whiteKeyWidth = maxWidth / 15 // ~15 white keys in 2 octaves

                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    for (i in 0 until totalKeys) {
                        val pitch = startPitch + i
                        val isBlack = isBlackKey(pitch)
                        val isPlayingNote = playingPitches.contains(pitch) || userPressedPitch == pitch
                        val keyColor = when {
                            isPlayingNote -> SanwolfCyan
                            isBlack -> Color(0xFF14161B)
                            else -> Color(0xFFE2E8F0)
                        }
                        val textColor = if (isBlack) Color.White else Color.DarkGray

                        Box(
                            modifier = Modifier
                                .width(keyWidth.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(keyColor)
                                .border(0.5.dp, SanwolfPanelBorder)
                                .pointerInput(pitch) {
                                    detectTapGestures(
                                        onPress = {
                                            userPressedPitch = pitch
                                            audioEngine.triggerMidiNote(pitch, selectedTrack)
                                            onKeyTriggered(pitch)
                                            try {
                                                awaitRelease()
                                            } finally {
                                                if (userPressedPitch == pitch) userPressedPitch = null
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (!isBlack && (pitch % 12 == 0)) {
                                Text(
                                    text = getNoteName(pitch),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
