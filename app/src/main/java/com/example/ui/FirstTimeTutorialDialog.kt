package com.example.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary

private data class TutorialStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val tips: List<String>
)

@Composable
fun FirstTimeTutorialDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sanwolf_daw_prefs", Context.MODE_PRIVATE) }
    var dontShowAgain by remember { mutableStateOf(false) }

    val steps = remember {
        listOf(
            TutorialStep(
                title = "WELCOME TO SANWOLF DAW",
                subtitle = "Next-Generation Studio Workstation",
                description = "SANWOLF is a complete mobile music production studio featuring multi-track sequencing, real-time polyphonic synthesizers, modular patching, and AI song generation.",
                icon = Icons.Default.Layers,
                accentColor = SanwolfGold,
                tips = listOf(
                    "Switch DAW modes anytime via the top mode tabs (Arrange, Steps, Piano Roll, Modular, Pads, Tools).",
                    "Monitor stereo peak levels with the real-time VU meter in the top transport bar.",
                    "Use Immersion Focus mode to dissolve toolbars for distraction-free producing."
                )
            ),
            TutorialStep(
                title = "ARRANGER & TIMELINE",
                subtitle = "Multi-Track Stems & Routing",
                description = "Build full tracks with Melodic Synths, 808 Kits, Modular Sub-racks, Ambient Risers, and Vocal stem imports.",
                icon = Icons.Default.GridView,
                accentColor = SanwolfCyan,
                tips = listOf(
                    "Tap '+ Melodic Synth' or '+ 808' at the top left of the Arranger to add new tracks.",
                    "Slide volume or toggle Mute (M) / Solo (S) directly on any track lane.",
                    "Expand VST effect racks and filter automation curves with the sliders on each track."
                )
            ),
            TutorialStep(
                title = "FULL-SCREEN PIANO ROLL",
                subtitle = "6-Octave Polyphonic Composition",
                description = "Compose chords and leads with the dedicated full-screen Piano Roll editor spanning 73 semitones (C1 to C7).",
                icon = Icons.Default.Piano,
                accentColor = SanwolfLime,
                tips = listOf(
                    "Tap the note icon on any instrument track in the Arranger to edit in full screen.",
                    "Tap on the grid to place or remove notes snapped to the beat grid.",
                    "Use Chord Stamper to lay down instant Minor 7th, Major 9th, or Suspended chords.",
                    "Quantize your composition into Natural Minor, Dorian, Blues, or Pentatonic scales."
                )
            ),
            TutorialStep(
                title = "STEP SEQUENCER & DRUM PADS",
                subtitle = "Polyrhythms & Trap Grooves",
                description = "Lay down hard-hitting drum grooves with custom step counts, per-step velocity accents, and live MPC-style velocity drum pads.",
                icon = Icons.Default.MusicNote,
                accentColor = SanwolfMagenta,
                tips = listOf(
                    "Program polyrhythms by setting 12 or 7 steps on Hi-Hats against 16-step 808 kicks.",
                    "Apply pre-loaded rhythm presets like Trap 808, Polyrhythm 3:4, and Boom Bap.",
                    "Switch to PADS mode for tactile finger-drumming with instant sound previews."
                )
            ),
            TutorialStep(
                title = "AI COMPOSER & MASTERING",
                subtitle = "Afro House, Amapiano, Jazz & More",
                description = "Leverage Gemini AI to generate full multi-track productions with genuine genre-specific stems, then master to commercial loudness.",
                icon = Icons.Default.AutoAwesome,
                accentColor = SanwolfGold,
                tips = listOf(
                    "Tap 'AI MUSIC GEN' to generate full songs in Afro House, Amapiano, Gqom, Deep House, or Jazz.",
                    "Open the Mastering Suite to dial in target LUFS, Limiter ceiling, and 3-Band Parametric EQ.",
                    "Export lossless WAV stems or standard MIDI files with the Export button."
                )
            )
        )
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    val step = steps[currentStepIndex]

    fun finishTutorial() {
        if (dontShowAgain) {
            prefs.edit().putBoolean("tutorial_completed", true).apply()
        }
        onDismiss()
    }

    Dialog(onDismissRequest = { finishTutorial() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, step.accentColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            color = SanwolfPanel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with step badge & close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(step.accentColor.copy(alpha = 0.15f))
                                .border(1.dp, step.accentColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "STEP ${currentStepIndex + 1} OF ${steps.size}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = step.accentColor
                            )
                        }
                    }

                    IconButton(
                        onClick = { finishTutorial() },
                        modifier = Modifier.size(28.dp).testTag("close_tutorial_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tutorial",
                            tint = SanwolfTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Animated step content
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
                    },
                    label = "tutorial_step_transition"
                ) { targetStep ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Icon & Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(targetStep.accentColor.copy(alpha = 0.2f))
                                    .border(1.5.dp, targetStep.accentColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = targetStep.icon,
                                    contentDescription = targetStep.title,
                                    tint = targetStep.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = targetStep.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = SanwolfTextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = targetStep.subtitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = targetStep.accentColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Description
                        Text(
                            text = targetStep.description,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = SanwolfTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Tips Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SanwolfBlack)
                                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "QUICK TIPS:",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = targetStep.accentColor
                            )
                            targetStep.tips.forEach { tip ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "•",
                                        color = targetStep.accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = tip,
                                        fontSize = 11.sp,
                                        color = SanwolfTextPrimary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Step Indicators (Dots)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { idx, s ->
                        val isSelected = idx == currentStepIndex
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isSelected) 20.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) s.accentColor else SanwolfPanelElevated)
                                .clickable { currentStepIndex = idx }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // "Don't show again" Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = SanwolfGold,
                            uncheckedColor = SanwolfTextMuted,
                            checkmarkColor = SanwolfBlack
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Don't show this tutorial on startup",
                        fontSize = 11.sp,
                        color = SanwolfTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        Button(
                            onClick = { currentStepIndex-- },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SanwolfPanelElevated,
                                contentColor = SanwolfTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("BACK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { finishTutorial() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = SanwolfTextMuted
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("SKIP", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (currentStepIndex < steps.size - 1) {
                        Button(
                            onClick = { currentStepIndex++ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = step.accentColor,
                                contentColor = SanwolfBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("next_tutorial_button")
                        ) {
                            Text("NEXT", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    } else {
                        Button(
                            onClick = { finishTutorial() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SanwolfLime,
                                contentColor = SanwolfBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("finish_tutorial_button")
                        ) {
                            Text("GET STARTED", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
