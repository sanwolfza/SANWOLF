package com.sanwolf.daw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanwolf.daw.model.TrackData
import com.sanwolf.daw.model.TrackType
import com.sanwolf.daw.ui.theme.SanwolfBlack
import com.sanwolf.daw.ui.theme.SanwolfGold
import com.sanwolf.daw.ui.theme.SanwolfPanelBorder
import com.sanwolf.daw.ui.theme.SanwolfPanelElevated
import com.sanwolf.daw.ui.theme.SanwolfTextSecondary

/** Which editor the drawer under the arranger shows. */
enum class EditorKind(val label: String) {
    PIANO_ROLL("Piano roll"),
    STEPS("Steps"),
    NONE("Editor")
}

/** The editor that fits a track: drums -> step sequencer, instruments -> piano roll, audio -> none. */
fun editorKindFor(track: TrackData?): EditorKind = when (track?.type) {
    null -> EditorKind.NONE
    TrackType.DRUM_MACHINE -> EditorKind.STEPS
    TrackType.AUDIO_IMPORT -> EditorKind.NONE
    else -> EditorKind.PIANO_ROLL
}

/** Thin title bar of the bottom editor: kind, track, maximise / restore and close. */
@Composable
fun BottomEditorHeader(
    kind: EditorKind,
    trackName: String?,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(SanwolfPanelElevated)
            .border(0.5.dp, SanwolfPanelBorder)
            .padding(start = 8.dp, end = 2.dp)
            .testTag("bottom_editor_header"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = kind.label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = SanwolfGold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = trackName ?: "",
            fontSize = 11.sp,
            color = SanwolfTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleMaximize, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = if (isMaximized) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (isMaximized) "Show the arranger again" else "Maximise editor",
                tint = SanwolfTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close editor",
                tint = SanwolfTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Shown in the editor area when the selected track has no editor (audio tracks). */
@Composable
fun BottomEditorHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SanwolfBlack)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = SanwolfTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
