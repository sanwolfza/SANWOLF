package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun SoundDesignToolsView(
    onPreviewRiser: () -> Unit,
    onPreviewTexture: () -> Unit,
    onAddGeneratedStem: (name: String, isRiser: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var riserBars by remember { mutableStateOf(4) }
    var riserNoiseAmount by remember { mutableStateOf(0.6f) }
    var riserPitchEndKhz by remember { mutableStateOf(6.5f) }

    var textureKey by remember { mutableStateOf("F Minor") }
    var textureShimmer by remember { mutableStateOf(0.75f) }
    var textureDetune by remember { mutableStateOf(0.35f) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(SanwolfBlack)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Tool 1: FX Riser Generator ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanel)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "FX RISER GENERATOR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfGold
                )
                Icon(Icons.Default.GraphicEq, contentDescription = "Riser", tint = SanwolfGold, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Riser Visualizer Curve
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0C0D10))
                    .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path()
                    path.moveTo(0f, size.height * 0.9f)
                    for (x in 0..size.width.toInt() step 4) {
                        val progress = x / size.width
                        val y = size.height * (0.9f - 0.8f * progress.pow(2.2f))
                        path.lineTo(x.toFloat(), y)
                    }
                    drawPath(path, color = Color(0xFFD9A441), style = Stroke(width = 2.5f))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Riser Length
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Build Length: $riserBars Bars", fontSize = 10.sp, color = SanwolfTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 2, 4, 8).forEach { b ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (riserBars == b) SanwolfGold else SanwolfPanelElevated)
                                .clickable { riserBars = b }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${b}B",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (riserBars == b) Color.Black else SanwolfTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Noise Blend
            Text("Noise Texture: ${(riserNoiseAmount * 100).toInt()}%", fontSize = 10.sp, color = SanwolfTextSecondary)
            Slider(
                value = riserNoiseAmount,
                onValueChange = { riserNoiseAmount = it },
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onPreviewRiser,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfPanelElevated)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PLAY RISER", fontSize = 10.sp, color = SanwolfGold, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onAddGeneratedStem("Riser Sweep $riserBars Bar", true) },
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ADD STEM", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }

        // --- Tool 2: Ambient Texture & Drone Generator ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanel)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AMBIENT TEXTURE GENERATOR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfCyan
                )
                Icon(Icons.Default.Waves, contentDescription = "Texture", tint = SanwolfCyan, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Texture Visualizer Waveform
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0C0D10))
                    .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path()
                    val mid = size.height / 2f
                    path.moveTo(0f, mid)
                    for (x in 0..size.width.toInt() step 3) {
                        val progress = x.toFloat() / size.width
                        val wave = (sin(progress * 24.0) * 12.0 + sin(progress * 5.0) * 16.0).toFloat()
                        path.lineTo(x.toFloat(), mid + wave)
                    }
                    drawPath(path, color = Color(0xFF00E5FF), style = Stroke(width = 2.5f))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fundamental Key
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Drone Tone: $textureKey", fontSize = 10.sp, color = SanwolfTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("C Min", "D Min", "F Min", "A Min").forEach { k ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (textureKey.startsWith(k.take(1))) SanwolfCyan else SanwolfPanelElevated)
                                .clickable { textureKey = k }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = k,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (textureKey.startsWith(k.take(1))) Color.Black else SanwolfTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Shimmer Amount
            Text("Granular Shimmer: ${(textureShimmer * 100).toInt()}%", fontSize = 10.sp, color = SanwolfTextSecondary)
            Slider(
                value = textureShimmer,
                onValueChange = { textureShimmer = it },
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(thumbColor = SanwolfCyan, activeTrackColor = SanwolfCyan)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onPreviewTexture,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfPanelElevated)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SanwolfCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PLAY DRONE", fontSize = 10.sp, color = SanwolfCyan, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onAddGeneratedStem("Ambient $textureKey Drone", false) },
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfCyan)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ADD STEM", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
