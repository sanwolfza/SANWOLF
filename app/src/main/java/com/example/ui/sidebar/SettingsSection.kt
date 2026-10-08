package com.example.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.model.ProjectData
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary

/** SETTINGS section: metronome, count-in, master volume, focus mode, help and About. */
@Composable
fun SettingsSection(
    project: ProjectData,
    loadKey: Int,
    isMetronomeEnabled: Boolean,
    metronomeVolume: Float,
    isCountInEnabled: Boolean,
    onMetronomeToggle: () -> Unit,
    onMetronomeVolumeChange: (Float) -> Unit,
    onCountInToggle: () -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onEnterFocusMode: () -> Unit,
    onOpenTutorial: () -> Unit
) {
    var masterVol by remember(loadKey) { mutableFloatStateOf(project.masterVolume) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SidebarSectionHeader("CLICK & RECORDING")
        SettingSwitchRow(
            label = "Metronome",
            subtitle = "Click on every beat while playing",
            icon = Icons.Default.GraphicEq,
            checked = isMetronomeEnabled,
            onToggle = onMetronomeToggle
        )
        SettingSliderRow(
            label = "Click volume",
            value = metronomeVolume,
            onValueChange = onMetronomeVolumeChange
        )
        SettingSwitchRow(
            label = "Count-in before recording",
            subtitle = "One bar of clicks, then the mic starts",
            icon = Icons.Default.Timer,
            checked = isCountInEnabled,
            onToggle = onCountInToggle
        )

        SidebarSectionHeader("OUTPUT")
        SettingSliderRow(
            label = "Master volume",
            value = masterVol,
            valueText = "${(masterVol * 100).toInt()}%",
            onValueChange = {
                masterVol = it
                onMasterVolumeChange(it)
            }
        )

        SidebarSectionHeader("WORKSPACE")
        SidebarActionRow(
            label = "Focus mode",
            subtitle = "Hide this panel; tap the menu button in the toolbar to bring it back",
            icon = Icons.Default.Fullscreen,
            tint = SanwolfGold,
            onClick = onEnterFocusMode
        )
        SidebarActionRow(
            label = "Help & tutorial",
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            tint = SanwolfCyan,
            onClick = onOpenTutorial
        )

        SidebarSectionHeader("ABOUT")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SanwolfPanelElevated)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = SanwolfTextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("SANWOLF DAW", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SanwolfTextPrimary)
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = 12.sp,
                    color = SanwolfTextSecondary
                )
            }
        }
    }
}

/** STUDIO TOOLS section: the production dialogs that used to sit in the old sidebar. */
@Composable
fun ToolsSection(
    onOpenAiStudio: () -> Unit,
    onOpenCoProducerArranger: () -> Unit,
    onOpenAiAnalyzer: () -> Unit,
    onOpenMastering: () -> Unit,
    onOpenMixer: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SidebarSectionHeader("AI")
        SidebarActionRow("AI Studio co-producer", Icons.Default.AutoAwesome, onOpenAiStudio, tint = SanwolfGold,
            subtitle = "Generate, arrange and finish tracks")
        SidebarActionRow("Co-producer arranger", Icons.Default.GraphicEq, onOpenCoProducerArranger, tint = SanwolfCyan)
        SidebarActionRow("AI polyrhythm analyzer", Icons.Default.AutoAwesome, onOpenAiAnalyzer, tint = SanwolfLime)
        SidebarSectionHeader("MIX & MASTER")
        SidebarActionRow("Track mixer", Icons.Default.ListAlt, onOpenMixer, tint = SanwolfGold)
        SidebarActionRow("Mastering suite", Icons.Default.Equalizer, onOpenMastering, tint = SanwolfCyan)
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    subtitle: String?,
    icon: ImageVector,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (checked) SanwolfLime else SanwolfTextSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SanwolfTextPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = SanwolfTextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SanwolfLime,
                checkedTrackColor = SanwolfLime.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueText: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = SanwolfTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, color = SanwolfTextPrimary, modifier = Modifier.weight(1f))
            if (valueText != null) Text(valueText, fontSize = 12.sp, color = SanwolfTextSecondary)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = label },
            colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
        )
    }
}
