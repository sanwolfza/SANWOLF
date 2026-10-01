package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SanwolfAudioEngine
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SpectrumVisualizerView(
    audioEngine: SanwolfAudioEngine,
    modifier: Modifier = Modifier
) {
    var ticker by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30) // ~30 FPS smooth animation
            ticker++
        }
    }

    val bands = audioEngine.frequencyBands
    val peakLeft = audioEngine.currentPeakLeft
    val peakRight = audioEngine.currentPeakRight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SanwolfPanel)
            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MASTER BUS FREQUENCY SPECTRUM & AUDIO LEVELS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = SanwolfGold,
                letterSpacing = 0.8.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "L: ${String.format("%.1f", peakLeft * 100f)}%",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SanwolfCyan
                )
                Text(
                    text = "R: ${String.format("%.1f", peakRight * 100f)}%",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SanwolfMagenta
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Canvas API Real-Time Visualizer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(SanwolfBlack)
                .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw background grid lines (frequency & dB axes)
                val gridCols = 8
                val gridRows = 4
                for (c in 1 until gridCols) {
                    val x = (w / gridCols) * c
                    drawLine(
                        color = SanwolfPanelBorder.copy(alpha = 0.3f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 0.5f
                    )
                }
                for (r in 1 until gridRows) {
                    val y = (h / gridRows) * r
                    drawLine(
                        color = SanwolfPanelBorder.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 0.5f
                    )
                }

                // Render Frequency Spectrum Bars with Peak Gradient & Glow
                val numBands = bands.size
                val barWidth = w / numBands.toFloat()

                bands.forEachIndexed { idx, valRaw ->
                    val value = valRaw.coerceIn(0.02f, 1.0f)
                    val barHeight = h * value
                    val left = idx * barWidth + 4f
                    val right = (idx + 1) * barWidth - 4f
                    val top = h - barHeight

                    // Draw gradient bar
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(SanwolfCyan, SanwolfGold, SanwolfMagenta),
                            startY = top,
                            endY = h
                        ),
                        topLeft = Offset(left, top),
                        size = Size(right - left, barHeight)
                    )

                    // Draw peak cap line
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top - 2f),
                        size = Size(right - left, 2f)
                    )
                }

                // Draw smoothed frequency waveform curve overlay
                val path = Path()
                var initialized = false
                bands.forEachIndexed { idx, b ->
                    val cx = idx * barWidth + barWidth / 2f
                    val cy = h - (b.coerceIn(0f, 1f) * h * 0.9f)
                    if (!initialized) {
                        path.moveTo(cx, cy)
                        initialized = true
                    } else {
                        path.cubicTo(cx - barWidth / 2f, cy, cx - barWidth / 2f, cy, cx, cy)
                    }
                }
                drawPath(
                    path = path,
                    color = SanwolfLime,
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )
            }
        }
    }
}
