package com.example.ui.sidebar

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.LocalProjectSummary
import com.example.audio.ProjectManager
import com.example.model.ProjectData
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PROJECT section of the drawer: current project card, New / Save / Save As / Export,
 * the on-device "Open / Recent" list (rename, duplicate, delete) and the cloud entry points.
 */
@Composable
fun ProjectSection(
    project: ProjectData,
    projectManager: ProjectManager,
    currentFileId: String?,
    isDirty: Boolean,
    refreshKey: Int,
    onNewProject: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAs: (String) -> Unit,
    onCurrentProjectRenamed: (String) -> Unit,
    onCurrentProjectDeleted: () -> Unit,
    onExport: () -> Unit,
    onOpenCloudProjects: () -> Unit,
    onOpenCloudSync: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var projects by remember { mutableStateOf<List<LocalProjectSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var localRefresh by remember { mutableIntStateOf(0) }

    var showNewNameDialog by remember { mutableStateOf(false) }
    var showNewConfirm by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf<LocalProjectSummary?>(null) }
    var renameTarget by remember { mutableStateOf<LocalProjectSummary?>(null) }
    var duplicateTarget by remember { mutableStateOf<LocalProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalProjectSummary?>(null) }

    LaunchedEffect(refreshKey, localRefresh) {
        isLoading = true
        projects = withContext(Dispatchers.IO) { runCatching { projectManager.listProjects() }.getOrDefault(emptyList()) }
        isLoading = false
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // --- Current project card ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanelElevated)
                .border(1.dp, SanwolfGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                when {
                    currentFileId == null -> SidebarBadge("NOT SAVED", SanwolfGold)
                    isDirty -> SidebarBadge("UNSAVED", SanwolfGold)
                    else -> SidebarBadge("SAVED", SanwolfLime)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${project.bpm} BPM • ${project.timeSignatureNumerator}/${project.timeSignatureDenominator} • ${project.tracks.size} tracks",
                fontSize = 12.sp,
                color = SanwolfTextSecondary
            )
        }

        SidebarSectionHeader("FILE")
        SidebarActionRow(
            label = "New project",
            icon = Icons.Default.Add,
            tint = SanwolfCyan,
            subtitle = "Start a blank session",
            onClick = { if (isDirty) showNewConfirm = true else showNewNameDialog = true }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SidebarActionRow(
                label = "Save",
                icon = Icons.Default.Save,
                tint = SanwolfLime,
                onClick = { if (currentFileId == null) showSaveAsDialog = true else onSave() },
                modifier = Modifier.weight(1f)
            )
            SidebarActionRow(
                label = "Save as…",
                icon = Icons.Default.SaveAs,
                tint = SanwolfGold,
                onClick = { showSaveAsDialog = true },
                modifier = Modifier.weight(1f)
            )
        }
        SidebarActionRow(
            label = "Export",
            icon = Icons.Default.IosShare,
            tint = SanwolfTextPrimary,
            subtitle = "WAV / MIDI mixdown",
            onClick = onExport
        )

        SidebarSectionHeader("OPEN / RECENT (ON THIS DEVICE)")
        when {
            isLoading && projects.isEmpty() -> SidebarEmptyState("Loading projects…")
            projects.isEmpty() -> SidebarEmptyState("No saved projects yet — tap Save to keep this one, or New project to start fresh.")
            else -> projects.forEach { summary ->
                ProjectRow(
                    summary = summary,
                    isCurrent = summary.fileId == currentFileId,
                    onOpen = {
                        if (summary.fileId == currentFileId && !isDirty) {
                            toast("'${summary.title}' is already open")
                        } else if (isDirty) {
                            pendingOpen = summary
                        } else {
                            onOpenProject(summary.fileId)
                        }
                    },
                    onRename = { renameTarget = summary },
                    onDuplicate = { duplicateTarget = summary },
                    onDelete = { deleteTarget = summary }
                )
            }
        }

        SidebarSectionHeader("CLOUD")
        SidebarActionRow(
            label = "Cloud projects",
            icon = Icons.Default.CloudQueue,
            tint = SanwolfCyan,
            subtitle = "Firestore project repository & templates",
            onClick = onOpenCloudProjects
        )
        SidebarActionRow(
            label = "Sync & live jam room",
            icon = Icons.Default.Cloud,
            tint = if (project.isCloudSynced) SanwolfLime else SanwolfTextSecondary,
            onClick = onOpenCloudSync
        )
    }

    // --- Dialogs ---
    if (showNewConfirm) {
        SidebarConfirmDialog(
            title = "Unsaved changes",
            message = "'${project.title}' has changes that aren't saved. Start a new project anyway?",
            confirmLabel = "Discard",
            destructive = true,
            neutralLabel = if (currentFileId != null) "Save first" else null,
            onNeutral = if (currentFileId != null) {
                {
                    onSave()
                    showNewConfirm = false
                    showNewNameDialog = true
                }
            } else null,
            onConfirm = {
                showNewConfirm = false
                showNewNameDialog = true
            },
            onDismiss = { showNewConfirm = false }
        )
    }
    if (showNewNameDialog) {
        SidebarNameDialog(
            title = "New project",
            initialValue = "Untitled ${projects.size + 1}",
            confirmLabel = "Create",
            onConfirm = { name ->
                showNewNameDialog = false
                onNewProject(name)
            },
            onDismiss = { showNewNameDialog = false }
        )
    }
    if (showSaveAsDialog) {
        SidebarNameDialog(
            title = "Save as",
            initialValue = if (currentFileId == null) project.title else "${project.title} copy",
            confirmLabel = "Save",
            onConfirm = { name ->
                showSaveAsDialog = false
                onSaveAs(name)
            },
            onDismiss = { showSaveAsDialog = false }
        )
    }
    pendingOpen?.let { target ->
        SidebarConfirmDialog(
            title = "Open '${target.title}'?",
            message = "Your current project has unsaved changes. They'll be lost if you open another project.",
            confirmLabel = "Discard & open",
            destructive = true,
            neutralLabel = if (currentFileId != null) "Save & open" else null,
            onNeutral = if (currentFileId != null) {
                {
                    onSave()
                    pendingOpen = null
                    onOpenProject(target.fileId)
                }
            } else null,
            onConfirm = {
                pendingOpen = null
                onOpenProject(target.fileId)
            },
            onDismiss = { pendingOpen = null }
        )
    }
    renameTarget?.let { target ->
        SidebarNameDialog(
            title = "Rename project",
            initialValue = target.title,
            confirmLabel = "Rename",
            onConfirm = { name ->
                renameTarget = null
                scope.launch {
                    val res = withContext(Dispatchers.IO) { projectManager.rename(target.fileId, name) }
                    res.onSuccess {
                        if (target.fileId == currentFileId) onCurrentProjectRenamed(name)
                        toast("Renamed to '$name'")
                    }.onFailure { toast("Couldn't rename: ${it.message ?: "error"}") }
                    localRefresh++
                }
            },
            onDismiss = { renameTarget = null }
        )
    }
    duplicateTarget?.let { target ->
        SidebarNameDialog(
            title = "Duplicate project",
            initialValue = "${target.title} copy",
            confirmLabel = "Duplicate",
            onConfirm = { name ->
                duplicateTarget = null
                scope.launch {
                    val res = withContext(Dispatchers.IO) { projectManager.duplicate(target.fileId, name) }
                    res.onSuccess { toast("Duplicated as '$name'") }
                        .onFailure { toast("Couldn't duplicate: ${it.message ?: "error"}") }
                    localRefresh++
                }
            },
            onDismiss = { duplicateTarget = null }
        )
    }
    deleteTarget?.let { target ->
        SidebarConfirmDialog(
            title = "Delete '${target.title}'?",
            message = if (target.fileId == currentFileId)
                "This deletes the saved file from this device. The open session stays on screen but will be unsaved."
            else
                "This permanently deletes the saved project from this device.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    val res = withContext(Dispatchers.IO) { projectManager.delete(target.fileId) }
                    res.onSuccess {
                        if (target.fileId == currentFileId) onCurrentProjectDeleted()
                        toast("Deleted '${target.title}'")
                    }.onFailure { toast("Couldn't delete: ${it.message ?: "error"}") }
                    localRefresh++
                }
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
    summary: LocalProjectSummary,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val modified = DateUtils.getRelativeTimeSpanString(
        summary.lastModified,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isCurrent) SanwolfGold.copy(alpha = 0.12f) else SanwolfPanelElevated)
            .border(1.dp, if (isCurrent) SanwolfGold.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClickLabel = "Open project",
                onLongClickLabel = "Project options",
                onClick = onOpen,
                onLongClick = { menuOpen = true }
            )
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = if (isCurrent) SanwolfGold else SanwolfTextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SanwolfTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${summary.bpm} BPM • ${summary.trackCount} tracks • $modified" + if (isCurrent) " • open" else "",
                fontSize = 12.sp,
                color = SanwolfTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "More options for ${summary.title}" }
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = SanwolfTextSecondary)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(SanwolfPanel)
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = SanwolfTextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = SanwolfTextSecondary) },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Duplicate", color = SanwolfTextPrimary) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = SanwolfTextSecondary) },
                    onClick = { menuOpen = false; onDuplicate() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = SanwolfMagenta) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = SanwolfMagenta) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}
