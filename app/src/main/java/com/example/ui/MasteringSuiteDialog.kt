package com.example.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.MasteringConfig
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

@Composable
fun MasteringSuiteDialog(
    config: MasteringConfig,
    onConfigChange: (MasteringConfig) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, SanwolfGold, RoundedCornerShape(12.dp)),
            color = SanwolfPanel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Equalizer, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SANWOLF PRO MASTERING SUITE",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = SanwolfGold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "AI Dynamics • 5-Band EQ • Stereo Widener • True Peak Limiter",
                                fontSize = 10.sp,
                                color = SanwolfTextMuted
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (config.enabled) "ACTIVE" else "BYPASS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (config.enabled) SanwolfLime else SanwolfTextMuted
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SanwolfLime,
                                checkedTrackColor = SanwolfPanelElevated
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Presets Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MasteringPresetButton("Streaming (-14 LUFS)", config.profileName.contains("Streaming")) {
                        onConfigChange(
                            config.copy(
                                targetLufs = -14f,
                                lowGainDb = 1.2f,
                                midGainDb = 0f,
                                highGainDb = 1.8f,
                                stereoWidth = 1.2f,
                                profileName = "Streaming (-14 LUFS)"
                            )
                        )
                    }
                    MasteringPresetButton("Club Punch (-9 LUFS)", config.profileName.contains("Club")) {
                        onConfigChange(
                            config.copy(
                                targetLufs = -9f,
                                lowGainDb = 2.8f,
                                midGainDb = -0.5f,
                                highGainDb = 2.4f,
                                stereoWidth = 1.4f,
                                profileName = "Club Punch (-9 LUFS)"
                            )
                        )
                    }
                    MasteringPresetButton("Warm Analog Tape", config.profileName.contains("Tape")) {
                        onConfigChange(
                            config.copy(
                                targetLufs = -12f,
                                lowGainDb = 2.0f,
                                midGainDb = 1.0f,
                                highGainDb = -0.5f,
                                stereoWidth = 1.1f,
                                profileName = "Warm Analog Tape"
                            )
                        )
                    }
                    MasteringPresetButton("Trap 808 Max", config.profileName.contains("Trap")) {
                        onConfigChange(
                            config.copy(
                                targetLufs = -10f,
                                lowGainDb = 3.5f,
                                midGainDb = -1.2f,
                                highGainDb = 2.6f,
                                stereoWidth = 1.35f,
                                profileName = "Trap 808 Max"
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main Controls: LUFS & 5-Band EQ
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left: Target LUFS Meter Box & Limiter Controls
                    Column(
                        modifier = Modifier
                            .width(190.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SanwolfPanelElevated)
                            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TARGET LOUDNESS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfGold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${config.targetLufs.toInt()} LUFS",
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = SanwolfLime
                            )
                        }

                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(SanwolfPanelBorder))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LOOK-AHEAD LIMITER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SanwolfCyan)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Ceiling: ${String.format("%.1f", config.limiterCeilingDb)} dBTP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = config.limiterCeilingDb,
                                onValueChange = { onConfigChange(config.copy(limiterCeilingDb = it)) },
                                valueRange = -3.0f..0.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = SanwolfCyan,
                                    activeTrackColor = SanwolfCyan,
                                    inactiveTrackColor = SanwolfPanelBorder
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(SanwolfPanelBorder))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STEREO IMAGER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SanwolfMagenta)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${(config.stereoWidth * 100).toInt()}% Width", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = config.stereoWidth,
                                onValueChange = { onConfigChange(config.copy(stereoWidth = it)) },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = SanwolfMagenta,
                                    activeTrackColor = SanwolfMagenta,
                                    inactiveTrackColor = SanwolfPanelBorder
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }

                    // Right: 5-Band Equalizer Faders
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SanwolfPanelElevated)
                            .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EqBandFader("LOW (80Hz)", config.lowGainDb, -6f..6f) {
                            onConfigChange(config.copy(lowGainDb = it))
                        }
                        EqBandFader("LOW-MID (300Hz)", config.midLowGainDb, -6f..6f) {
                            onConfigChange(config.copy(midLowGainDb = it))
                        }
                        EqBandFader("MID (1.2kHz)", config.midGainDb, -6f..6f) {
                            onConfigChange(config.copy(midGainDb = it))
                        }
                        EqBandFader("HIGH-MID (4.5kHz)", config.highMidGainDb, -6f..6f) {
                            onConfigChange(config.copy(highMidGainDb = it))
                        }
                        EqBandFader("AIR (12kHz)", config.highGainDb, -6f..6f) {
                            onConfigChange(config.copy(highGainDb = it))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqBandFader(
    label: String,
    valueDb: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxHeight()
    ) {
        Text(
            text = String.format("%+.1f dB", valueDb),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (valueDb > 0) SanwolfGold else if (valueDb < 0) SanwolfCyan else SanwolfTextSecondary
        )

        Slider(
            value = valueDb,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.width(110.dp),
            colors = SliderDefaults.colors(
                thumbColor = SanwolfGold,
                activeTrackColor = SanwolfGold,
                inactiveTrackColor = SanwolfPanelBorder
            )
        )

        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = SanwolfTextSecondary
        )
    }
}

@Composable
private fun MasteringPresetButton(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) SanwolfGold else SanwolfPanelElevated)
            .border(1.dp, if (isSelected) SanwolfGold else SanwolfPanelBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = name,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
            color = if (isSelected) SanwolfBlack else SanwolfTextPrimary
        )
    }
}
