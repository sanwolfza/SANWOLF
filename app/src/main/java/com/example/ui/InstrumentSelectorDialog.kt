package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.InstrumentDefinition
import com.example.audio.InstrumentLibrary
import com.example.audio.SanwolfAudioEngine
import com.example.model.InstrumentCatalog
import com.example.model.InstrumentCategory
import com.example.model.InstrumentPreset
import com.example.model.SynthWaveform
import com.example.model.TrackData
import com.example.model.TrackType
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * High-performance Instrument and Preset browser providing a List Mode friendly view.
 * Enables rapid scanning, one-tap previewing, and track assignment with accessible touch targets.
 */
@Composable
fun InstrumentSelectorDialog(
    currentTrack: TrackData?,
    audioEngine: SanwolfAudioEngine?,
    onPresetSelected: (InstrumentPreset) -> Unit,
    onCreateTrackWithPreset: ((InstrumentPreset) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<InstrumentCategory?>(null) }
    var isListMode by remember { mutableStateOf(true) } // List mode friendly view by default
    var previewingPresetId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Consolidate both InstrumentCatalog presets and InstrumentLibrary definitions into unified browser
    val allPresets = remember {
        val catalogPresets = InstrumentCatalog.presets
        val libraryPresets = InstrumentLibrary.definitions.map { def -> def.toInstrumentPreset() }
        val combined = mutableListOf<InstrumentPreset>()
        combined.addAll(catalogPresets)
        libraryPresets.forEach { libPreset ->
            if (combined.none { it.id == libPreset.id || it.name.equals(libPreset.name, ignoreCase = true) }) {
                combined.add(libPreset)
            }
        }
        combined
    }

    val filteredPresets = remember(searchQuery, selectedCategory, allPresets) {
        allPresets.filter { preset ->
            val matchesCategory = selectedCategory == null || preset.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                    preset.name.contains(searchQuery, ignoreCase = true) ||
                    preset.description.contains(searchQuery, ignoreCase = true) ||
                    preset.category.displayName.contains(searchQuery, ignoreCase = true) ||
                    preset.waveform.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    // Browse by section: category headings (Drums, Bass, Keys, Synths...) over each group.
    // Headings are for browsing only; an added track is named after the instrument alone.
    val groupedPresets = remember(filteredPresets) {
        PICKER_SECTION_ORDER.mapNotNull { category ->
            val inSection = filteredPresets.filter { it.category == category }
            if (inSection.isEmpty()) null else category to inSection
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .testTag("instrument_selector_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SanwolfBlack),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SanwolfGold.copy(alpha = 0.85f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // --- Top Header Row: Title, Mode Switcher (List vs Cards), and Close ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SanwolfGold.copy(alpha = 0.2f))
                                .border(1.dp, SanwolfGold, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SanwolfGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "INSTRUMENTS & PRESETS",
                                    color = SanwolfGold,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SanwolfPanelElevated)
                                        .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${filteredPresets.size} SOUNDS",
                                        color = SanwolfCyan,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (currentTrack != null) {
                                Text(
                                    text = "Loading onto: ${currentTrack.name}",
                                    color = SanwolfTextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Select an instrument to create a new track",
                                    color = SanwolfTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // --- Friendly View Mode Toggle (LIST vs CARDS) ---
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SanwolfPanel)
                                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // List Mode Tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isListMode) SanwolfGold.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable { isListMode = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "List Mode",
                                        tint = if (isListMode) SanwolfGold else SanwolfTextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "LIST",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isListMode) SanwolfGold else SanwolfTextMuted
                                    )
                                }
                            }

                            // Cards Mode Tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (!isListMode) SanwolfCyan.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable { isListMode = false }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Card Mode",
                                        tint = if (!isListMode) SanwolfCyan else SanwolfTextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "CARDS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (!isListMode) SanwolfCyan else SanwolfTextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("close_instrument_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = SanwolfTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Search & Filter Bar ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Filter by name, kick, snare, 808, rhodes, pad, kalimba, saw...",
                            color = SanwolfTextMuted,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = SanwolfTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = SanwolfTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("instrument_search_field"),
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

                Spacer(modifier = Modifier.height(8.dp))

                // --- Category Filter Chips ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryFilterChip(
                        title = "ALL (${allPresets.size})",
                        isSelected = selectedCategory == null,
                        color = SanwolfGold,
                        onClick = { selectedCategory = null },
                        testTag = "filter_category_all"
                    )

                    InstrumentCategory.values().forEach { category ->
                        val count = allPresets.count { it.category == category }
                        val chipColor = getCategoryColor(category)
                        CategoryFilterChip(
                            title = "${category.displayName.uppercase()} ($count)",
                            isSelected = selectedCategory == category,
                            color = chipColor,
                            onClick = { selectedCategory = category },
                            testTag = "filter_category_${category.name.lowercase()}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Content List (List Mode Friendly vs Expanded Cards) ---
                if (filteredPresets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = SanwolfTextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No instruments or presets matching '$searchQuery'",
                                color = SanwolfTextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (isListMode) {
                    // =========================================================
                    // 1. LIST MODE FRIENDLY VIEW
                    // Sleek, high-density, ergonomic rows with clear touch targets
                    // =========================================================
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedPresets.forEach { (sectionCategory, sectionPresets) ->
                        item(key = "section_${sectionCategory.name}") {
                            PickerSectionHeading(sectionCategory, sectionPresets.size)
                        }
                        items(sectionPresets, key = { it.id }) { preset ->
                            val isCurrentlyLoaded = currentTrack?.synthPresetName?.equals(preset.name, ignoreCase = true) == true
                            val isPreviewing = previewingPresetId == preset.id

                            PresetListRow(
                                preset = preset,
                                isLoaded = isCurrentlyLoaded,
                                isPreviewing = isPreviewing,
                                onPreview = {
                                    previewingPresetId = preset.id
                                    scope.launch {
                                        playPresetPreview(audioEngine, preset)
                                        delay(700)
                                        if (previewingPresetId == preset.id) {
                                            previewingPresetId = null
                                        }
                                    }
                                },
                                onSelect = {
                                    onPresetSelected(preset)
                                    onDismiss()
                                },
                                onCreateNewTrack = if (onCreateTrackWithPreset != null) {
                                    {
                                        onCreateTrackWithPreset(preset)
                                        onDismiss()
                                    }
                                } else null
                            )
                        }
                        }
                    }
                } else {
                    // =========================================================
                    // 2. EXPANDED CARDS VIEW
                    // Detailed cards showing full ADSR envelopes and descriptions
                    // =========================================================
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedPresets.forEach { (sectionCategory, sectionPresets) ->
                        item(key = "section_${sectionCategory.name}") {
                            PickerSectionHeading(sectionCategory, sectionPresets.size)
                        }
                        items(sectionPresets, key = { it.id }) { preset ->
                            val isCurrentlyLoaded = currentTrack?.synthPresetName?.equals(preset.name, ignoreCase = true) == true
                            val isPreviewing = previewingPresetId == preset.id

                            PresetCard(
                                preset = preset,
                                isLoaded = isCurrentlyLoaded,
                                isPreviewing = isPreviewing,
                                onPreview = {
                                    previewingPresetId = preset.id
                                    scope.launch {
                                        playPresetPreview(audioEngine, preset)
                                        delay(700)
                                        if (previewingPresetId == preset.id) {
                                            previewingPresetId = null
                                        }
                                    }
                                },
                                onSelect = {
                                    onPresetSelected(preset)
                                    onDismiss()
                                },
                                onCreateNewTrack = if (onCreateTrackWithPreset != null) {
                                    {
                                        onCreateTrackWithPreset(preset)
                                        onDismiss()
                                    }
                                } else null
                            )
                        }
                        }
                    }
                }
            }
        }
    }
}

/**
 * List Mode Friendly Row:
 * Compact, modern, ergonomic item row with 48.dp minimum interactive size,
 * category color stripe, waveform badge, and one-tap preview/assignment.
 */
@Composable
private fun PresetListRow(
    preset: InstrumentPreset,
    isLoaded: Boolean,
    isPreviewing: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
    onCreateNewTrack: (() -> Unit)?
) {
    val accentColor = Color(preset.colorHex)
    val categoryIcon = getCategoryIcon(preset.category)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isLoaded) SanwolfPanelElevated else SanwolfPanel)
            .border(
                1.dp,
                if (isLoaded) SanwolfGold else if (isPreviewing) SanwolfCyan else SanwolfPanelBorder.copy(alpha = 0.6f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onSelect() }
            .testTag("instrument_preset_${preset.id}")
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Color Accent Strip & Category Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Vertical Category Strip
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Circular Category Icon Badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Metadata
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = preset.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLoaded) SanwolfGold else SanwolfTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isLoaded) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SanwolfGold.copy(alpha = 0.25f))
                                    .border(0.5.dp, SanwolfGold, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = SanwolfGold
                                )
                            }
                        }
                    }

                    // Metadata Pill Row: Category, Waveform, Quick Param
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = preset.category.displayName,
                            fontSize = 9.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "•",
                            fontSize = 9.sp,
                            color = SanwolfTextMuted
                        )

                        Text(
                            text = preset.waveform.name,
                            fontSize = 9.sp,
                            color = SanwolfCyan,
                            fontFamily = FontFamily.Monospace
                        )

                        if (preset.description.isNotBlank()) {
                            Text(
                                text = "•",
                                fontSize = 9.sp,
                                color = SanwolfTextMuted
                            )
                            Text(
                                text = preset.description,
                                fontSize = 9.sp,
                                color = SanwolfTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Action Buttons (Preview and Assign)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quick Preview Button
                Button(
                    onClick = onPreview,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPreviewing) SanwolfCyan else SanwolfBlack
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isPreviewing) SanwolfCyan else SanwolfPanelBorder
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("preview_preset_${preset.id}")
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = "Preview",
                        tint = if (isPreviewing) SanwolfBlack else SanwolfCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isPreviewing) "PLAYING" else "PLAY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (isPreviewing) SanwolfBlack else SanwolfCyan
                    )
                }

                // Quick Select / Assign Button
                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLoaded) SanwolfGold else SanwolfPanelElevated
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isLoaded) SanwolfGold else SanwolfLime
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("select_preset_${preset.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Select",
                        tint = if (isLoaded) SanwolfBlack else SanwolfLime,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isLoaded) "LOADED" else "LOAD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (isLoaded) SanwolfBlack else SanwolfLime
                    )
                }
            }
        }
    }
}

/**
 * Expanded Card View for presets with complete parameter metrics.
 */
@Composable
private fun PresetCard(
    preset: InstrumentPreset,
    isLoaded: Boolean,
    isPreviewing: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
    onCreateNewTrack: (() -> Unit)?
) {
    val accentColor = Color(preset.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("instrument_preset_${preset.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) SanwolfPanelElevated else SanwolfPanel
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLoaded) SanwolfGold else if (isPreviewing) SanwolfCyan else SanwolfPanelBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: Name, Category, Badges, Preview and Select Actions
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
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = preset.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isLoaded) SanwolfGold else SanwolfTextPrimary
                            )
                            if (isLoaded) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(SanwolfGold.copy(alpha = 0.2f))
                                        .border(0.5.dp, SanwolfGold, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = SanwolfGold
                                    )
                                }
                            }
                        }

                        Text(
                            text = preset.category.displayName,
                            fontSize = 10.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onPreview,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPreviewing) SanwolfCyan else SanwolfBlack
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isPreviewing) SanwolfCyan else SanwolfPanelBorder
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("preview_preset_${preset.id}")
                    ) {
                        Icon(
                            imageVector = if (isPreviewing) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = if (isPreviewing) SanwolfBlack else SanwolfCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPreviewing) "PLAYING" else "PREVIEW",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isPreviewing) SanwolfBlack else SanwolfCyan
                        )
                    }

                    Button(
                        onClick = onSelect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLoaded) SanwolfGold else SanwolfPanelElevated
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isLoaded) SanwolfGold else SanwolfLime
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("select_preset_${preset.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Select",
                            tint = if (isLoaded) SanwolfBlack else SanwolfLime,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLoaded) "LOADED" else "ASSIGN",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isLoaded) SanwolfBlack else SanwolfLime
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Description
            Text(
                text = preset.description,
                color = SanwolfTextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Parameter Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ParamBadge(label = "WAVE", value = preset.waveform.name, color = SanwolfCyan)
                ParamBadge(label = "CUTOFF", value = "${preset.cutoffHz.toInt()} Hz", color = SanwolfGold)
                ParamBadge(label = "RES", value = String.format("%.1f", preset.resonance), color = SanwolfLime)
                ParamBadge(label = "ATK", value = "${preset.attackMs.toInt()}ms", color = SanwolfOrange)
                ParamBadge(label = "DEC", value = "${preset.decayMs.toInt()}ms", color = SanwolfOrange)
                ParamBadge(label = "SUS", value = "${(preset.sustainLevel * 100).toInt()}%", color = SanwolfMagenta)
                ParamBadge(label = "REL", value = "${preset.releaseMs.toInt()}ms", color = SanwolfMagenta)
            }
        }
    }
}

@Composable
private fun ParamBadge(
    label: String,
    value: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SanwolfBlack.copy(alpha = 0.6f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = SanwolfTextMuted
            )
            Text(
                text = value,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
private fun CategoryFilterChip(
    title: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) color.copy(alpha = 0.25f) else SanwolfPanel)
            .border(
                1.dp,
                if (isSelected) color else SanwolfPanelBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) color else SanwolfTextSecondary
        )
    }
}

private fun getCategoryColor(category: InstrumentCategory): Color {
    return when (category) {
        InstrumentCategory.SYNTH_LEAD -> SanwolfCyan
        InstrumentCategory.BASS_808 -> SanwolfOrange
        InstrumentCategory.KEYS_PAD -> SanwolfGold
        InstrumentCategory.ORCHESTRAL_STRINGS -> SanwolfLime
        InstrumentCategory.BRASS_WINDS -> SanwolfGold
        InstrumentCategory.DRUM_KIT -> SanwolfMagenta
        InstrumentCategory.WORLD_PERCUSSION -> SanwolfOrange
        InstrumentCategory.ATMOSPHERE_FX -> SanwolfCyan
    }
}

private fun getCategoryIcon(category: InstrumentCategory): ImageVector {
    return when (category) {
        InstrumentCategory.KEYS_PAD -> Icons.Default.MusicNote
        InstrumentCategory.ORCHESTRAL_STRINGS -> Icons.Default.Tune
        InstrumentCategory.BRASS_WINDS -> Icons.Default.GraphicEq
        InstrumentCategory.BASS_808 -> Icons.Default.Waves
        InstrumentCategory.SYNTH_LEAD -> Icons.Default.Tune
        InstrumentCategory.DRUM_KIT -> Icons.Default.Equalizer
        InstrumentCategory.WORLD_PERCUSSION -> Icons.Default.MusicNote
        InstrumentCategory.ATMOSPHERE_FX -> Icons.Default.Waves
    }
}

private fun InstrumentDefinition.toInstrumentPreset(): InstrumentPreset {
    val modelCat = when (category) {
        com.example.audio.InstrumentCategory.DRUMS, com.example.audio.InstrumentCategory.DRUMS_PERCUSSION -> InstrumentCategory.DRUM_KIT
        com.example.audio.InstrumentCategory.BASS -> InstrumentCategory.BASS_808
        com.example.audio.InstrumentCategory.SYNTHS, com.example.audio.InstrumentCategory.SYNTHESIZERS -> InstrumentCategory.SYNTH_LEAD
        com.example.audio.InstrumentCategory.PADS -> InstrumentCategory.KEYS_PAD
        com.example.audio.InstrumentCategory.KEYS_PIANO, com.example.audio.InstrumentCategory.KEYBOARDS, com.example.audio.InstrumentCategory.PIANO_KEYS -> InstrumentCategory.KEYS_PAD
        com.example.audio.InstrumentCategory.STRINGS, com.example.audio.InstrumentCategory.ORCHESTRAL_STRINGS -> InstrumentCategory.ORCHESTRAL_STRINGS
        com.example.audio.InstrumentCategory.BRASS_WINDS -> InstrumentCategory.BRASS_WINDS
        com.example.audio.InstrumentCategory.PLUCKS_MALLETS -> InstrumentCategory.SYNTH_LEAD
        com.example.audio.InstrumentCategory.WORLD_PERCUSSION -> InstrumentCategory.WORLD_PERCUSSION
        com.example.audio.InstrumentCategory.SOUND_EFFECTS_TEXTURES, com.example.audio.InstrumentCategory.AMBIENT_TEXTURES, com.example.audio.InstrumentCategory.VOX_FX -> InstrumentCategory.ATMOSPHERE_FX
        else -> InstrumentCategory.SYNTH_LEAD
    }
    val hex = when (category) {
        com.example.audio.InstrumentCategory.DRUMS, com.example.audio.InstrumentCategory.DRUMS_PERCUSSION -> 0xFFFF5722
        com.example.audio.InstrumentCategory.BASS -> 0xFFFF9100
        com.example.audio.InstrumentCategory.SYNTHS, com.example.audio.InstrumentCategory.SYNTHESIZERS -> 0xFF00E5FF
        com.example.audio.InstrumentCategory.PADS -> 0xFFFFD700
        com.example.audio.InstrumentCategory.KEYS_PIANO, com.example.audio.InstrumentCategory.KEYBOARDS, com.example.audio.InstrumentCategory.PIANO_KEYS -> 0xFFFFC107
        com.example.audio.InstrumentCategory.STRINGS, com.example.audio.InstrumentCategory.ORCHESTRAL_STRINGS -> 0xFF76FF03
        com.example.audio.InstrumentCategory.BRASS_WINDS -> 0xFFFFAB00
        com.example.audio.InstrumentCategory.PLUCKS_MALLETS -> 0xFF00B0FF
        com.example.audio.InstrumentCategory.WORLD_PERCUSSION -> 0xFFFF6D00
        com.example.audio.InstrumentCategory.SOUND_EFFECTS_TEXTURES, com.example.audio.InstrumentCategory.AMBIENT_TEXTURES, com.example.audio.InstrumentCategory.VOX_FX -> 0xFFE040FB
        else -> 0xFF00E5FF
    }
    return InstrumentPreset(
        id = id,
        name = name,
        category = modelCat,
        waveform = defaultWaveform,
        cutoffHz = filterCutoffHz,
        resonance = filterResonance,
        attackMs = attackMs,
        decayMs = decayMs,
        sustainLevel = sustainLevel,
        releaseMs = releaseMs,
        description = if (subCategory.isNotEmpty()) "$subCategory • Studio Instrument" else "${category.displayName} Instrument",
        defaultTrackType = if (isDrum) TrackType.DRUM_MACHINE else TrackType.SYNTH,
        colorHex = hex
    )
}

private fun playPresetPreview(audioEngine: SanwolfAudioEngine?, preset: InstrumentPreset) {
    if (audioEngine == null) return

    when (preset.category) {
        InstrumentCategory.DRUM_KIT, InstrumentCategory.WORLD_PERCUSSION -> {
            audioEngine.triggerDrumSound(preset.name, 0.95f)
        }
        InstrumentCategory.BASS_808 -> {
            audioEngine.triggerSynthNote(
                frequency = 65.41f,
                durationSec = 0.65f,
                waveform = preset.waveform,
                velocity = 0.92f,
                cutoff = preset.cutoffHz,
                resonance = preset.resonance,
                attackMs = preset.attackMs,
                decayMs = preset.decayMs,
                sustainLevel = preset.sustainLevel,
                releaseMs = preset.releaseMs,
                presetName = preset.name,
                category = preset.category.displayName
            )
        }
        InstrumentCategory.ORCHESTRAL_STRINGS -> {
            audioEngine.triggerSynthNote(
                frequency = 261.63f,
                durationSec = 0.8f,
                waveform = preset.waveform,
                velocity = 0.88f,
                cutoff = preset.cutoffHz,
                resonance = preset.resonance,
                attackMs = preset.attackMs,
                decayMs = preset.decayMs,
                sustainLevel = preset.sustainLevel,
                releaseMs = preset.releaseMs,
                presetName = preset.name,
                category = preset.category.displayName
            )
        }
        InstrumentCategory.BRASS_WINDS -> {
            audioEngine.triggerSynthNote(
                frequency = 261.63f,
                durationSec = 0.7f,
                waveform = preset.waveform,
                velocity = 0.9f,
                cutoff = preset.cutoffHz,
                resonance = preset.resonance,
                attackMs = preset.attackMs,
                decayMs = preset.decayMs,
                sustainLevel = preset.sustainLevel,
                releaseMs = preset.releaseMs,
                presetName = preset.name,
                category = preset.category.displayName
            )
        }
        InstrumentCategory.KEYS_PAD, InstrumentCategory.SYNTH_LEAD -> {
            audioEngine.triggerSynthNote(
                frequency = 261.63f,
                durationSec = 0.6f,
                waveform = preset.waveform,
                velocity = 0.85f,
                cutoff = preset.cutoffHz,
                resonance = preset.resonance,
                attackMs = preset.attackMs,
                decayMs = preset.decayMs,
                sustainLevel = preset.sustainLevel,
                releaseMs = preset.releaseMs,
                presetName = preset.name,
                category = preset.category.displayName
            )
        }
        InstrumentCategory.ATMOSPHERE_FX -> {
            audioEngine.triggerSynthNote(
                frequency = 440.0f,
                durationSec = 0.8f,
                waveform = preset.waveform,
                velocity = 0.8f,
                cutoff = preset.cutoffHz,
                resonance = preset.resonance,
                attackMs = preset.attackMs,
                decayMs = preset.decayMs,
                sustainLevel = preset.sustainLevel,
                releaseMs = preset.releaseMs,
                presetName = preset.name,
                category = preset.category.displayName
            )
        }
    }
}

/** Order of the browse sections in the instrument picker. */
private val PICKER_SECTION_ORDER = listOf(
    InstrumentCategory.DRUM_KIT,
    InstrumentCategory.BASS_808,
    InstrumentCategory.KEYS_PAD,
    InstrumentCategory.SYNTH_LEAD,
    InstrumentCategory.ORCHESTRAL_STRINGS,
    InstrumentCategory.BRASS_WINDS,
    InstrumentCategory.WORLD_PERCUSSION,
    InstrumentCategory.ATMOSPHERE_FX
)

private fun pickerSectionTitle(category: InstrumentCategory): String = when (category) {
    InstrumentCategory.DRUM_KIT -> "Drums"
    InstrumentCategory.BASS_808 -> "Bass"
    InstrumentCategory.KEYS_PAD -> "Keys"
    InstrumentCategory.SYNTH_LEAD -> "Synths"
    InstrumentCategory.ORCHESTRAL_STRINGS -> "Strings"
    InstrumentCategory.BRASS_WINDS -> "Brass & Winds"
    InstrumentCategory.WORLD_PERCUSSION -> "Percussion"
    InstrumentCategory.ATMOSPHERE_FX -> "FX & Textures"
}

/** Section heading in the picker list (browse only; never copied onto the track). */
@Composable
private fun PickerSectionHeading(category: InstrumentCategory, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pickerSectionTitle(category).uppercase(),
            color = SanwolfGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$count", color = SanwolfTextMuted, fontSize = 12.sp)
    }
}
