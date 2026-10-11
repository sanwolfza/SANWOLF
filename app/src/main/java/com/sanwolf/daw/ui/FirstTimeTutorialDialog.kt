package com.sanwolf.daw.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.sanwolf.daw.ui.theme.SanwolfBlack
import com.sanwolf.daw.ui.theme.SanwolfGold
import com.sanwolf.daw.ui.theme.SanwolfGoldDim
import com.sanwolf.daw.ui.theme.SanwolfPanel
import com.sanwolf.daw.ui.theme.SanwolfPanelBorder
import com.sanwolf.daw.ui.theme.SanwolfPanelElevated
import com.sanwolf.daw.ui.theme.SanwolfTextMuted
import com.sanwolf.daw.ui.theme.SanwolfTextPrimary
import com.sanwolf.daw.ui.theme.SanwolfTextSecondary

/** SharedPreferences file and key shared with SanwolfDawApp's first-launch check. */
private const val TUTORIAL_PREFS = "sanwolf_daw_prefs"
private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"

private class TutorialStep(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tips: List<String>
)

/** The five steps, written for the current app (blank New Project, Add-track picker, drawers…). */
private val TUTORIAL_STEPS = listOf(
    TutorialStep(
        title = "Start a song",
        subtitle = "New project, then + Track",
        icon = Icons.Default.AddCircle,
        tips = listOf(
            "Left panel › Project › New project starts a blank song: no tracks yet.",
            "Tap + Track (top left of the arranger) to open the track picker.",
            "Pick a tab (Drums, Keys, Synths, Bass, Strings & Plucked, Perc & Mallets, World or Audio) and tap a tile. The track is added, selected and its editor opens.",
            "Tap ▶ on a tile, or hold it, to hear it first. The Audio tab records your mic or imports a file."
        )
    ),
    TutorialStep(
        title = "Arranger & editors",
        subtitle = "Tap a clip to edit it",
        icon = Icons.Default.GridView,
        tips = listOf(
            "Tap a track or clip to select it. The editor opens in a drawer under the arranger: piano roll for instruments, step sequencer for drums.",
            "Tap the grid to add or remove notes and steps. The maximise button gives the editor the whole screen.",
            "The back arrow in the editor bar, or your phone's Back, returns to the arranger.",
            "Hold and drag a clip to move it; drag its right edge to resize. The track menu renames, moves, deletes or changes the instrument."
        )
    ),
    TutorialStep(
        title = "Piano strip & transport",
        subtitle = "Play, record, tempo, undo",
        icon = Icons.Default.Piano,
        tips = listOf(
            "Play / Pause and Stop sit on the strip above the keys. Record (top toolbar) records your mic as a new audio track.",
            "The strip's record button writes what you play on the keys into the selected instrument track.",
            "BPM − / + change the tempo (hold to repeat, tap the number to type it). The metronome button toggles the click.",
            "Undo and Redo are on the strip. The arrow at its end shows or hides the piano keys."
        )
    ),
    TutorialStep(
        title = "Side panels & FX",
        subtitle = "Edge tabs, Track · Master · Keys",
        icon = Icons.Default.Tune,
        tips = listOf(
            "Both side panels collapse completely. Tap the small edge tab on the left or right side of the screen to bring one back.",
            "Left panel: Project, Samples, Song info, Settings and Studio tools.",
            "Right panel tabs: Track (volume, pan and the FX rack), Master (master chain and inserts) and Keys (octave, range, key size, velocity).",
            "Each FX rack holds up to 8 slots (EQ, Compressor, Width, Limiter), processed in order."
        )
    ),
    TutorialStep(
        title = "Master, export & projects",
        subtitle = "Finish and share your song",
        icon = Icons.Default.AutoAwesome,
        tips = listOf(
            "Master tab › AI mastering assistant: play your song so it can measure, pick a target and genre, then Suggest. Compare A / B, then Apply (undoable).",
            "Export renders WAV (16 or 24-bit), MP3, AAC or FLAC, or MIDI. It saves to Music and has a Share button.",
            "Projects save automatically a moment after each edit and when you leave the app. The last project reopens on launch.",
            "Left panel › Project › All projects… opens, renames, duplicates or shares your songs."
        )
    )
)

/**
 * Getting-started tutorial: opened on first launch and from Settings › Help & tutorial.
 *
 * Every way out (Close, Skip, Finish, Back outside the dialog, "Don't show again") dismisses it
 * and writes `tutorial_completed`. "Don't show on startup" (checked by default) decides the value
 * written by Close / Skip / Finish, so unticking it brings the tutorial back next launch; the
 * "Don't show again" button always writes true. Content scrolls, so nothing clips at compact UI
 * sizes. It never reads the project, so it is safe with zero tracks.
 */
@Composable
fun FirstTimeTutorialDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(TUTORIAL_PREFS, Context.MODE_PRIVATE) }
    // Opened on first launch: not completed yet, so default to "don't show on startup" ticked
    // (closing it once is enough). Reopened from Settings: reflects the stored choice.
    var dontShowOnStartup by remember {
        mutableStateOf(
            if (prefs.contains(KEY_TUTORIAL_COMPLETED)) prefs.getBoolean(KEY_TUTORIAL_COMPLETED, true) else true
        )
    }
    var stepIndex by remember { mutableIntStateOf(0) }
    var dismissed by remember { mutableStateOf(false) }

    fun close(completed: Boolean) {
        if (dismissed) return
        dismissed = true
        // commit(): tiny write, and it must survive even if the app is killed right after
        runCatching { prefs.edit().putBoolean(KEY_TUTORIAL_COMPLETED, completed).commit() }
        onDismiss()
    }

    val steps = TUTORIAL_STEPS
    val index = stepIndex.coerceIn(0, steps.size - 1)
    val step = steps[index]
    val isLast = index == steps.size - 1

    ScaledDialog(
        onDismissRequest = { close(dontShowOnStartup) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val maxH: Dp = maxHeight * 0.94f
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.94f)
                    .heightIn(max = maxH)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SanwolfBlack)
                    .border(1.5.dp, SanwolfGold.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("tutorial_dialog")
            ) {
                // Header: step badge, title, close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SanwolfGold.copy(alpha = 0.15f))
                            .border(1.dp, SanwolfGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(step.icon, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STEP ${index + 1} OF ${steps.size}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfGoldDim
                        )
                        Text(
                            text = step.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = SanwolfGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = step.subtitle,
                            fontSize = 11.sp,
                            color = SanwolfTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SanwolfPanelElevated)
                            .clickable { close(dontShowOnStartup) }
                            .testTag("close_tutorial_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close tutorial", tint = SanwolfTextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable body: takes what is left and never pushes the buttons off screen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfPanel)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    step.tips.forEach { tip ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text("•", color = SanwolfGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tip, color = SanwolfTextPrimary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Step dots (tap to jump)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { i ->
                        val selected = i == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (selected) 18.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) SanwolfGold else SanwolfPanelElevated)
                                .clickable { stepIndex = i }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // "Don't show on startup" (works both ways)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowOnStartup = !dontShowOnStartup },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowOnStartup,
                        onCheckedChange = { dontShowOnStartup = it },
                        modifier = Modifier.size(32.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = SanwolfGold,
                            uncheckedColor = SanwolfTextMuted,
                            checkmarkColor = SanwolfBlack
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Don't show on startup", fontSize = 11.sp, color = SanwolfTextSecondary)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Buttons: Skip / Back · Don't show again · Next / Finish
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (index == 0) {
                        TutorialButton("SKIP", SanwolfPanelElevated, SanwolfTextPrimary, Modifier.weight(1f).testTag("skip_tutorial_button")) {
                            close(dontShowOnStartup)
                        }
                    } else {
                        TutorialButton("BACK", SanwolfPanelElevated, SanwolfTextPrimary, Modifier.weight(1f).testTag("back_tutorial_button")) {
                            stepIndex = (index - 1).coerceAtLeast(0)
                        }
                    }
                    TutorialButton(
                        "DON'T SHOW AGAIN",
                        Color.Transparent,
                        SanwolfGold,
                        Modifier.weight(1.4f).border(1.dp, SanwolfGold, RoundedCornerShape(8.dp)).testTag("dont_show_again_button")
                    ) {
                        close(true)
                    }
                    if (isLast) {
                        TutorialButton("FINISH", SanwolfGold, SanwolfBlack, Modifier.weight(1f).testTag("finish_tutorial_button")) {
                            close(dontShowOnStartup)
                        }
                    } else {
                        TutorialButton("NEXT", SanwolfGold, SanwolfBlack, Modifier.weight(1f).testTag("next_tutorial_button")) {
                            stepIndex = (index + 1).coerceAtMost(steps.size - 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialButton(label: String, background: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = content,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
