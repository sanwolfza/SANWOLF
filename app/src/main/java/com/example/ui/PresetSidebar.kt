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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.firebase.FirebasePresetManager
import com.example.model.Preset
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

/**
 * List-mode friendly Preset Sidebar browser.
 * Provides high-density, accessible browsing for cloud and genre presets.
 */
@Composable
fun PresetSidebar(
    presetManager: FirebasePresetManager,
    onLoadPreset: (Preset) -> Unit
) {
    var presets by remember { mutableStateOf<List<Preset>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        presetManager.loadPresets().onSuccess { presets = it }
    }

    val filteredPresets = remember(searchQuery, presets) {
        if (searchQuery.isBlank()) presets
        else presets.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.genre.contains(searchQuery, ignoreCase = true) ||
                    it.style.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(SanwolfBlack)
            .border(1.dp, SanwolfPanelBorder)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SanwolfGold.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SanwolfGold,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "PRESET LIBRARY",
                color = SanwolfGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SanwolfPanelElevated)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${filteredPresets.size}",
                    color = SanwolfCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Search presets...", color = SanwolfTextMuted, fontSize = 11.sp)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = SanwolfTextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SanwolfPanel,
                unfocusedContainerColor = SanwolfPanel,
                focusedBorderColor = SanwolfCyan,
                unfocusedBorderColor = SanwolfPanelBorder,
                focusedTextColor = SanwolfTextPrimary,
                unfocusedTextColor = SanwolfTextPrimary
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // List Mode View
        val grouped = filteredPresets.groupBy { it.genre.ifBlank { "General" } }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            grouped.forEach { (genre, genrePresets) ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(getGenreColor(genre))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = genre.uppercase(),
                            color = SanwolfTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                items(genrePresets, key = { it.name + it.genre }) { preset ->
                    val isSelected = selectedPresetId == preset.name
                    PresetSidebarListRow(
                        preset = preset,
                        isSelected = isSelected,
                        onClick = {
                            selectedPresetId = preset.name
                            onLoadPreset(preset)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetSidebarListRow(
    preset: Preset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val genreColor = getGenreColor(preset.genre)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) SanwolfPanelElevated else SanwolfPanel)
            .border(
                1.dp,
                if (isSelected) SanwolfGold else SanwolfPanelBorder.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(genreColor)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = preset.name,
                        color = if (isSelected) SanwolfGold else SanwolfTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = preset.style.ifBlank { preset.genre },
                        color = SanwolfTextMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Load Action Icon
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) SanwolfGold.copy(alpha = 0.2f) else SanwolfPanelBorder.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Load",
                    tint = if (isSelected) SanwolfGold else SanwolfLime,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

private fun getGenreColor(genre: String): Color {
    val lower = genre.lowercase()
    return when {
        lower.contains("afro") || lower.contains("tribal") -> SanwolfOrange
        lower.contains("house") || lower.contains("edm") -> SanwolfCyan
        lower.contains("trap") || lower.contains("hiphop") -> SanwolfMagenta
        lower.contains("ambient") || lower.contains("chill") -> SanwolfGold
        lower.contains("techno") || lower.contains("club") -> SanwolfLime
        else -> SanwolfCyan
    }
}
