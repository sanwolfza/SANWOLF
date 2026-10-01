package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.ui.theme.*

@Composable
fun MasterMixerView(
    project: ProjectData,
    audioEngine: com.example.audio.SanwolfAudioEngine,
    onTrackVolumeChange: (Int, Float) -> Unit,
    onTrackInputGainChange: (Int, Float) -> Unit,
    onTrackPanChange: (Int, Float) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ticker to pull volatile values from audio thread at ~30 FPS
    var ticker by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(33) // ~30 FPS updates
            ticker++
        }
    }

    val phaseCorrelation = audioEngine.phaseCorrelation
    val goniometerPoints = audioEngine.goniometerPoints
    val frequencyBands = audioEngine.frequencyBands

    Surface(modifier = modifier.fillMaxSize(), color = SanwolfBlack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Real-Time Master Bus Frequency Spectrum Visualizer
            SpectrumVisualizerView(audioEngine = audioEngine)

            // Visualizer & Analyzer Panel: Row of Spectrum + Vector Scope + Phase Correlation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SanwolfPanel)
                    .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Spectrum Analyzer Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SPECTRUM ANALYZER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 8.dp)
                    ) {
                        frequencyBands.forEach { band ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(band.coerceIn(0.05f, 1.0f))
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(SanwolfCyan, SanwolfCyan.copy(alpha = 0.3f))
                                        )
                                    )
                            )
                        }
                    }
                }

                // 2. Pro Phase Scope / Goniometer (Lissajous Vector Scope)
                Column(
                    modifier = Modifier
                        .width(130.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "STEREO GONIOMETER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfTextSecondary
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SanwolfBlack)
                            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val cx = w / 2f
                            val cy = h / 2f
                            val r = w / 2f - 4f

                            // Draw polar background guides (Mono/Center, Out of Phase, Left/Right axes)
                            // Mid Axis (Vertical)
                            drawLine(
                                color = SanwolfPanelBorder.copy(alpha = 0.5f),
                                start = Offset(cx, 4f),
                                end = Offset(cx, h - 4f),
                                strokeWidth = 1f
                            )
                            // Side Axis (Horizontal)
                            drawLine(
                                color = SanwolfPanelBorder.copy(alpha = 0.5f),
                                start = Offset(4f, cy),
                                end = Offset(w - 4f, cy),
                                strokeWidth = 1f
                            )
                            // Left Channel Axis (45 degrees left)
                            drawLine(
                                color = SanwolfPanelBorder.copy(alpha = 0.25f),
                                start = Offset(cx - r * 0.7071f, cy + r * 0.7071f),
                                end = Offset(cx + r * 0.7071f, cy - r * 0.7071f),
                                strokeWidth = 0.8f
                            )
                            // Right Channel Axis (45 degrees right)
                            drawLine(
                                color = SanwolfPanelBorder.copy(alpha = 0.25f),
                                start = Offset(cx + r * 0.7071f, cy + r * 0.7071f),
                                end = Offset(cx - r * 0.7071f, cy - r * 0.7071f),
                                strokeWidth = 0.8f
                            )

                            // Plot Lissajous vector coordinates
                            val path = Path()
                            var initialized = false
                            for (p in 0 until 64) {
                                val rx = goniometerPoints[p * 2]
                                val ry = goniometerPoints[p * 2 + 1]

                                // Rotate and scale coordinates to fit circular Canvas
                                // Scaling: maximum expected sample is around 1.0f
                                val scale = r * 0.9f
                                val px = cx + rx * scale
                                val py = cy - ry * scale // Invert Y for screen coordinates

                                if (initialized) {
                                    path.lineTo(px, py)
                                } else {
                                    path.moveTo(px, py)
                                    initialized = true
                                }
                            }

                            // Render Lissajous glowing path
                            drawPath(
                                path = path,
                                color = SanwolfGold.copy(alpha = 0.85f),
                                style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                // 3. Horizontal Phase Correlation Meter Column
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PHASE CORRELATION",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfTextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Correlation Value Text
                    Text(
                        text = String.format("%+.2f", phaseCorrelation),
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = when {
                            phaseCorrelation > 0.3f -> SanwolfLime
                            phaseCorrelation < -0.1f -> SanwolfMagenta
                            else -> SanwolfGold
                        }
                    )

                    // Correlation Meter Strip
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("-1 (OUT)", fontSize = 7.sp, color = SanwolfMagenta)
                            Text("0", fontSize = 7.sp, color = SanwolfTextMuted)
                            Text("+1 (MONO)", fontSize = 7.sp, color = SanwolfLime)
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            val w = size.width
                            val h = size.height

                            // Draw back color background strip with color sections (Red -> Orange -> Green)
                            drawRect(
                                color = Color(0xFF15151A),
                                size = size
                            )

                            // Normalized position (-1.0 to +1.0 mapped to 0f to w)
                            val normalizedPos = ((phaseCorrelation + 1.0f) / 2.0f).coerceIn(0f, 1f)
                            val cursorX = normalizedPos * w

                            // Draw zero center line marker
                            drawLine(
                                color = SanwolfPanelBorder,
                                start = Offset(w / 2f, 0f),
                                end = Offset(w / 2f, h),
                                strokeWidth = 1f
                            )

                            // Left side out-of-phase section (Red)
                            drawRect(
                                color = SanwolfMagenta.copy(alpha = 0.2f),
                                topLeft = Offset(0f, 0f),
                                size = Size(w * 0.35f, h)
                            )

                            // Middle transitional section (Orange)
                            drawRect(
                                color = SanwolfOrange.copy(alpha = 0.2f),
                                topLeft = Offset(w * 0.35f, 0f),
                                size = Size(w * 0.3f, h)
                            )

                            // Right fully-in-phase section (Green)
                            drawRect(
                                color = SanwolfLime.copy(alpha = 0.2f),
                                topLeft = Offset(w * 0.65f, 0f),
                                size = Size(w * 0.35f, h)
                            )

                            // Glowing current correlation indicator bar
                            drawRect(
                                color = when {
                                    phaseCorrelation > 0.3f -> SanwolfLime
                                    phaseCorrelation < -0.1f -> SanwolfMagenta
                                    else -> SanwolfGold
                                },
                                topLeft = Offset(cursorX - 2.dp.toPx(), 0f),
                                size = Size(4.dp.toPx(), h)
                            )
                        }
                    }

                    // Explanatory Subtitle
                    Text(
                        text = when {
                            phaseCorrelation > 0.9f -> "Perfect Mono Compatibility"
                            phaseCorrelation > 0.3f -> "Wide Stereo (Safe)"
                            phaseCorrelation < -0.1f -> "Phase Cancellation Warning"
                            else -> "Extreme Stereo Width"
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = SanwolfTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Mixer Faders Grid
            LazyRow(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(project.tracks.size) { index ->
                    TrackChannelStrip(
                        track = project.tracks[index],
                        onVolumeChange = { onTrackVolumeChange(index, it) },
                        onInputGainChange = { onTrackInputGainChange(index, it) },
                        onPanChange = { onTrackPanChange(index, it) }
                    )
                }
                item {
                    MasterChannelStrip(
                        masterVolume = project.masterVolume,
                        onVolumeChange = onMasterVolumeChange
                    )
                }
            }
        }
    }
}

@Composable
fun TrackChannelStrip(
    track: TrackData,
    onVolumeChange: (Float) -> Unit,
    onInputGainChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(SanwolfPanel)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(track.name, style = MaterialTheme.typography.bodySmall, color = SanwolfTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.weight(1f)) {
            // Input Gain (Trim)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("IN", style = MaterialTheme.typography.labelSmall, color = SanwolfTextMuted, fontSize = 8.sp)
                Slider(
                    value = track.inputGain,
                    onValueChange = onInputGainChange,
                    valueRange = 0f..2f,
                    modifier = Modifier.fillMaxHeight().graphicsLayer(rotationZ = -90f),
                    colors = SliderDefaults.colors(thumbColor = SanwolfMagenta, activeTrackColor = SanwolfMagenta)
                )
            }
            
            // Fader (Vertical Slider)
            Slider(
                value = track.volume,
                onValueChange = onVolumeChange,
                modifier = Modifier
                    .fillMaxHeight()
                    .graphicsLayer(rotationZ = -90f),
                colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Pan Knob
        Text("PAN", style = MaterialTheme.typography.labelSmall, color = SanwolfTextMuted)
        Slider(
            value = track.pan,
            onValueChange = onPanChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(thumbColor = SanwolfCyan, activeTrackColor = SanwolfCyan)
        )
    }
}

@Composable
fun MasterChannelStrip(
    masterVolume: Float,
    onVolumeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(SanwolfPanelElevated)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MASTER", style = MaterialTheme.typography.bodySmall, color = SanwolfGold)
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(modifier = Modifier.weight(1f)) {
            Slider(
                value = masterVolume,
                onValueChange = onVolumeChange,
                modifier = Modifier
                    .fillMaxHeight()
                    .graphicsLayer(rotationZ = -90f),
                colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
            )
        }
    }
}
