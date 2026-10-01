package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectData
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated

enum class DawViewMode {
    ARRANGER,
    STEP_SEQUENCER,
    PIANO_ROLL,
    MODULAR_SYNTH,
    DRUM_PADS,
    SOUND_TOOLS
}

@Composable
fun TopToolbar(
    project: ProjectData,
    isPlaying: Boolean,
    currentBeat: Double,
    peakLeft: Float,
    peakRight: Float,
    isSidebarExpanded: Boolean,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        color = SanwolfPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, SanwolfPanelBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger menu toggle + Logo Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfPanelElevated)
                        .testTag("menu_sidebar_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Toggle Sidebar",
                        tint = SanwolfGold,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Logo brand indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfPanelElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) SanwolfLime else SanwolfGold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SANWOLF STUDIO",
                        color = SanwolfGold,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            // Center: Playback Status Display (Time Code & Tempo & Stereo VU meter)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfBlack)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bar = (currentBeat / 4).toInt() + 1
                    val beat = (currentBeat % 4).toInt() + 1
                    val tick = ((currentBeat % 1) * 100).toInt()

                    Text(
                        text = String.format("%02d:%02d:%02d", bar, beat, tick),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfCyan,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "${project.bpm} BPM",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfGold,
                        fontSize = 12.sp
                    )
                }

                // Stereo VU Meter
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .width(18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SanwolfBlack)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Left VU
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clip(RoundedCornerShape(1.dp))
                            .background(SanwolfPanelElevated),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(peakLeft.coerceIn(0.05f, 1f))
                                .background(if (peakLeft > 0.85f) Color.Magenta else SanwolfLime)
                        )
                    }
                    // Right VU
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clip(RoundedCornerShape(1.dp))
                            .background(SanwolfPanelElevated),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(peakRight.coerceIn(0.05f, 1f))
                                .background(if (peakRight > 0.85f) Color.Magenta else SanwolfLime)
                        )
                    }
                }
            }
        }
    }
}
