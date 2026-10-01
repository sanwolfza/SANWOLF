package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfOrange
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfPurple
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary

data class PadInfo(
    val id: Int,
    val name: String,
    val shortcut: String,
    val color: Color
)

@Composable
fun DrumPadView(
    onTriggerPad: (name: String, velocity: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedKit by remember { mutableStateOf("Wolf Dark 808 Trap") }
    var showKitMenu by remember { mutableStateOf(false) }

    // 16 MPC-style Pads
    val pads = remember {
        listOf(
            PadInfo(1, "Kick 808", "C1", Color(0xFFFF9100)),
            PadInfo(2, "Snare Hard", "D1", Color(0xFF00E5FF)),
            PadInfo(3, "Clap Trap", "E1", Color(0xFFFF2A6D)),
            PadInfo(4, "HiHat Cl", "F#1", Color(0xFF76FF03)),

            PadInfo(5, "HiHat Open", "A#1", Color(0xFF76FF03)),
            PadInfo(6, "Tom Low", "G1", Color(0xFFB388FF)),
            PadInfo(7, "Tom High", "B1", Color(0xFFB388FF)),
            PadInfo(8, "Perc Rim", "C#2", Color(0xFF00E5FF)),

            PadInfo(9, "808 Sub Drop", "C2", Color(0xFFFF9100)),
            PadInfo(10, "Crash Cymbal", "C#1", Color(0xFFD9A441)),
            PadInfo(11, "Vocal Chant", "D2", Color(0xFFFF2A6D)),
            PadInfo(12, "Laser FX", "E2", Color(0xFF00E5FF)),

            PadInfo(13, "Riser Sweep", "F2", Color(0xFFD9A441)),
            PadInfo(14, "Ambient Drone", "G2", Color(0xFF76FF03)),
            PadInfo(15, "Synth Stab", "A2", Color(0xFFB388FF)),
            PadInfo(16, "Shaker 16th", "B2", Color(0xFF76FF03))
        )
    }

    val activePadFlashes = remember { mutableStateMapOf<Int, Long>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SanwolfBlack)
            .padding(8.dp)
    ) {
        // --- Top Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanel)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PERFORMANCE DRUM PADS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfGold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "16 Velocity-Sensitive RGB Trigger Pads",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SanwolfTextMuted
                )
            }

            // Kit Selector
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                        .clickable { showKitMenu = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Kit: $selectedKit",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfCyan
                    )
                }

                DropdownMenu(
                    expanded = showKitMenu,
                    onDismissRequest = { showKitMenu = false },
                    modifier = Modifier.background(SanwolfPanelElevated)
                ) {
                    listOf(
                        "Wolf Dark 808 Trap",
                        "Cyber Synthwave 84",
                        "Club Peaktime Techno 909",
                        "Lo-Fi Midnight Chill"
                    ).forEach { kit ->
                        DropdownMenuItem(
                            text = { Text(kit, color = SanwolfTextPrimary, fontSize = 11.sp) },
                            onClick = {
                                selectedKit = kit
                                showKitMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- 4x4 Pads Matrix ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (row in 0..3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (col in 0..3) {
                        val index = row * 4 + col
                        val pad = pads[index]

                        PadButton(
                            pad = pad,
                            onTrigger = { vel ->
                                onTriggerPad(pad.name, vel)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PadButton(
    pad: PadInfo,
    onTrigger: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 60),
        label = "padScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) pad.color else SanwolfPanel)
            .border(
                1.5.dp,
                if (isPressed) Color.White else pad.color.copy(alpha = 0.6f),
                RoundedCornerShape(8.dp)
            )
            .pointerInput(pad.id) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onTrigger(0.9f)
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
            .testTag("pad_${pad.id}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = pad.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) Color.Black else SanwolfTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pad.shortcut,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isPressed) Color.Black.copy(alpha = 0.7f) else pad.color
            )
        }
    }
}
