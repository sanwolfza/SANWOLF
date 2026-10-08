package com.example.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ProjectManager
import com.example.model.ProjectData
import com.example.ui.sidebar.ProjectSection
import com.example.ui.sidebar.SampleBrowserSection
import com.example.ui.sidebar.SettingsSection
import com.example.ui.sidebar.SidebarBadge
import com.example.ui.sidebar.SongInfoSection
import com.example.ui.sidebar.ToolsSection
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfSurfaceDark
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary

/** The drawer's sections, shown as icons on the collapsed rail. */
enum class SidebarTab(val title: String, val icon: ImageVector) {
    PROJECT("Project", Icons.Default.Folder),
    SAMPLES("Samples", Icons.Default.LibraryMusic),
    SONG("Song info", Icons.Default.MusicNote),
    SETTINGS("Settings", Icons.Default.Settings),
    TOOLS("Studio tools", Icons.Default.AutoAwesome)
}

/**
 * Left drawer: a slim icon rail that is always visible (outside focus mode) plus an
 * animated ~320dp panel for the selected section. Tapping a rail icon opens that section;
 * tapping the open section's icon again collapses the panel.
 */
@Composable
fun DawSidebarPanel(
    // Drawer state
    expanded: Boolean,
    selectedTab: SidebarTab,
    onSelectTab: (SidebarTab) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    // Project
    project: ProjectData,
    projectManager: ProjectManager,
    currentFileId: String?,
    isDirty: Boolean,
    projectListVersion: Int,
    loadKey: Int,
    onNewProject: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onSaveProject: () -> Unit,
    onSaveProjectAs: (String) -> Unit,
    onCurrentProjectRenamed: (String) -> Unit,
    onCurrentProjectDeleted: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenCloudProjects: () -> Unit,
    onOpenCloudSync: () -> Unit,
    // Samples
    onPreviewFactory: (FactoryWavSample) -> Unit,
    onAddFactorySample: (FactoryWavSample) -> Unit,
    onAddAudioFile: (String, Uri) -> Unit,
    onImportAudio: () -> Unit,
    onOpenSampleManager: () -> Unit,
    // Song info
    onBpmChange: (Int) -> Unit,
    onSongInfoChanged: () -> Unit,
    // Settings
    isMetronomeEnabled: Boolean,
    metronomeVolume: Float,
    isCountInEnabled: Boolean,
    onMetronomeToggle: () -> Unit,
    onMetronomeVolumeChange: (Float) -> Unit,
    onCountInToggle: () -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onEnterFocusMode: () -> Unit,
    onOpenTutorial: () -> Unit,
    // Studio tools
    onOpenAiStudio: () -> Unit,
    onOpenCoProducerArranger: () -> Unit,
    onOpenAiAnalyzer: () -> Unit,
    onOpenMastering: () -> Unit,
    onOpenMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .testTag("daw_sidebar")
    ) {
        // --- Icon rail (always visible) ---
        Column(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(SanwolfSurfaceDark)
                .border(BorderStroke(1.dp, SanwolfPanelBorder))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse side panel" else "Expand side panel",
                    tint = SanwolfTextSecondary
                )
            }
            HorizontalDivider(color = SanwolfPanelBorder, modifier = Modifier.width(32.dp))
            SidebarTab.values().forEach { tab ->
                val isSelected = expanded && tab == selectedTab
                RailButton(
                    tab = tab,
                    isSelected = isSelected,
                    showDot = tab == SidebarTab.PROJECT && isDirty,
                    onClick = {
                        if (isSelected) {
                            onExpandedChange(false)
                        } else {
                            onSelectTab(tab)
                            if (!expanded) onExpandedChange(true)
                        }
                    }
                )
            }
        }

        // --- Expanded panel ---
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(SanwolfPanel)
                    .border(BorderStroke(1.dp, SanwolfPanelBorder))
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(selectedTab.icon, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedTab.title.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = SanwolfGold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isDirty) {
                        SidebarBadge("UNSAVED", SanwolfGold)
                    }
                    IconButton(onClick = { onExpandedChange(false) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Collapse side panel", tint = SanwolfTextSecondary)
                    }
                }
                HorizontalDivider(color = SanwolfPanelBorder)

                key(selectedTab) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        when (selectedTab) {
                            SidebarTab.PROJECT -> ProjectSection(
                                project = project,
                                projectManager = projectManager,
                                currentFileId = currentFileId,
                                isDirty = isDirty,
                                refreshKey = projectListVersion,
                                onNewProject = onNewProject,
                                onOpenProject = onOpenProject,
                                onSave = onSaveProject,
                                onSaveAs = onSaveProjectAs,
                                onCurrentProjectRenamed = onCurrentProjectRenamed,
                                onCurrentProjectDeleted = onCurrentProjectDeleted,
                                onExport = onOpenExport,
                                onOpenCloudProjects = onOpenCloudProjects,
                                onOpenCloudSync = onOpenCloudSync
                            )
                            SidebarTab.SAMPLES -> SampleBrowserSection(
                                project = project,
                                refreshKey = projectListVersion + loadKey,
                                onPreviewFactory = onPreviewFactory,
                                onAddFactorySample = onAddFactorySample,
                                onAddAudioFile = onAddAudioFile,
                                onImportAudio = onImportAudio,
                                onOpenFullSampleManager = onOpenSampleManager
                            )
                            SidebarTab.SONG -> SongInfoSection(
                                project = project,
                                loadKey = loadKey,
                                onBpmChange = onBpmChange,
                                onChanged = onSongInfoChanged
                            )
                            SidebarTab.SETTINGS -> SettingsSection(
                                project = project,
                                loadKey = loadKey,
                                isMetronomeEnabled = isMetronomeEnabled,
                                metronomeVolume = metronomeVolume,
                                isCountInEnabled = isCountInEnabled,
                                onMetronomeToggle = onMetronomeToggle,
                                onMetronomeVolumeChange = onMetronomeVolumeChange,
                                onCountInToggle = onCountInToggle,
                                onMasterVolumeChange = onMasterVolumeChange,
                                onEnterFocusMode = onEnterFocusMode,
                                onOpenTutorial = onOpenTutorial
                            )
                            SidebarTab.TOOLS -> ToolsSection(
                                onOpenAiStudio = onOpenAiStudio,
                                onOpenCoProducerArranger = onOpenCoProducerArranger,
                                onOpenAiAnalyzer = onOpenAiAnalyzer,
                                onOpenMastering = onOpenMastering,
                                onOpenMixer = onOpenMixer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RailButton(
    tab: SidebarTab,
    isSelected: Boolean,
    showDot: Boolean,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) SanwolfGold.copy(alpha = 0.18f) else Color.Transparent)
                .semantics {
                    contentDescription = if (showDot) "${tab.title} (unsaved changes)" else tab.title
                    selected = isSelected
                }
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = if (isSelected) SanwolfGold else SanwolfTextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SanwolfGold)
            )
        }
    }
}
