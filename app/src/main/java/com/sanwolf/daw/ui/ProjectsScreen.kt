package com.sanwolf.daw.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.sanwolf.daw.audio.LocalProjectSummary
import com.sanwolf.daw.audio.ProjectManager
import com.sanwolf.daw.data.ProjectBundle
import com.sanwolf.daw.model.ProjectData
import com.sanwolf.daw.ui.sidebar.SidebarActionRow
import com.sanwolf.daw.ui.sidebar.SidebarConfirmDialog
import com.sanwolf.daw.ui.sidebar.SidebarEmptyState
import com.sanwolf.daw.ui.sidebar.SidebarNameDialog
import com.sanwolf.daw.ui.theme.SanwolfCyan
import com.sanwolf.daw.ui.theme.SanwolfGold
import com.sanwolf.daw.ui.theme.SanwolfLime
import com.sanwolf.daw.ui.theme.SanwolfMagenta
import com.sanwolf.daw.ui.theme.SanwolfPanel
import com.sanwolf.daw.ui.theme.SanwolfPanelBorder
import com.sanwolf.daw.ui.theme.SanwolfPanelElevated
import com.sanwolf.daw.ui.theme.SanwolfTextPrimary
import com.sanwolf.daw.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * `.sanwolf` project-file actions (share / save to device / import). Create with
 * [rememberSanwolfFileActions]; it owns the document-picker launchers.
 */
class SanwolfFileActions internal constructor(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val projectManager: ProjectManager,
    private val launchImport: () -> Unit,
    private val launchSaveTo: (String) -> Unit,
    private val setPendingSave: (File?) -> Unit
) {
    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    private fun buildThen(json: String?, then: (File) -> Unit) {
        if (json == null) { toast("Couldn't read that project"); return }
        scope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { ProjectBundle.export(context, json) } }
            res.onSuccess(then).onFailure {
                Log.e("SanwolfFileActions", "Bundle failed", it)
                toast("Couldn't build project file: ${it.message ?: "error"}")
            }
        }
    }

    private fun share(file: File) {
        runCatching {
            context.startActivity(Intent.createChooser(ProjectBundle.shareIntent(context, file), "Share ${file.name}"))
        }.onFailure { toast("Couldn't open the share sheet") }
    }

    /** Shares the open project (as it is right now) as a .sanwolf file. */
    fun shareCurrent(project: ProjectData) = buildThen(runCatching { projectManager.toJson(project) }.getOrNull()) { share(it) }

    /** Shares a saved project as a .sanwolf file. */
    fun shareSaved(fileId: String) {
        scope.launch {
            val json = withContext(Dispatchers.IO) { projectManager.readJson(fileId) }
            buildThen(json) { share(it) }
        }
    }

    /** Lets the user pick where to save the open project's .sanwolf file (Files, Drive, SD card…). */
    fun saveCurrentToDevice(project: ProjectData) = buildThen(runCatching { projectManager.toJson(project) }.getOrNull()) { file ->
        setPendingSave(file)
        launchSaveTo(file.name)
    }

    fun saveSavedToDevice(fileId: String) {
        scope.launch {
            val json = withContext(Dispatchers.IO) { projectManager.readJson(fileId) }
            buildThen(json) { file ->
                setPendingSave(file)
                launchSaveTo(file.name)
            }
        }
    }

    /** Opens the document picker to import a .sanwolf as a new editable project. */
    fun importFile() = launchImport()
}

@Composable
fun rememberSanwolfFileActions(
    projectManager: ProjectManager,
    onImported: (fileId: String) -> Unit
): SanwolfFileActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope { com.sanwolf.daw.CrashReporter.coroutineHandler }
    var pendingSave by remember { mutableStateOf<File?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) { runCatching { ProjectBundle.importBundle(context.applicationContext, projectManager, uri) } }
                res.onSuccess { id ->
                    Toast.makeText(context, "Project imported", Toast.LENGTH_SHORT).show()
                    onImported(id)
                }.onFailure {
                    Log.e("SanwolfFileActions", "Import failed", it)
                    Toast.makeText(context, "Couldn't import: ${it.message ?: "not a SANWOLF project"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ProjectBundle.MIME)) { uri: Uri? ->
        val file = pendingSave
        pendingSave = null
        if (uri != null && file != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) { runCatching { ProjectBundle.copyTo(context, file, uri) } }
                Toast.makeText(
                    context,
                    if (res.isSuccess) "Saved ${file.name}" else "Couldn't save: ${res.exceptionOrNull()?.message ?: "error"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    return remember(projectManager) {
        SanwolfFileActions(
            context = context, // activity context: needed for startActivity
            scope = scope,
            projectManager = projectManager,
            // Any document: many file managers don't know a MIME type for .sanwolf
            launchImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            launchSaveTo = { name -> saveLauncher.launch(name) },
            setPendingSave = { pendingSave = it }
        )
    }
}

/**
 * Projects screen: every project saved on this device (name, last edited, BPM) with open,
 * rename, duplicate, delete, share/save as a .sanwolf file, plus New, Save as and Import.
 * Opened from the drawer's Project section.
 */
@Composable
fun ProjectsScreenDialog(
    project: ProjectData,
    projectManager: ProjectManager,
    currentFileId: String?,
    refreshKey: Int,
    fileActions: SanwolfFileActions,
    onOpenProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onSaveAs: (String) -> Unit,
    onCurrentProjectRenamed: (String) -> Unit,
    onCurrentProjectDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope { com.sanwolf.daw.CrashReporter.coroutineHandler }
    var projects by remember { mutableStateOf<List<LocalProjectSummary>?>(null) }
    var localRefresh by remember { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<LocalProjectSummary?>(null) }
    var duplicateTarget by remember { mutableStateOf<LocalProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalProjectSummary?>(null) }
    var showSaveAs by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, localRefresh) {
        projects = withContext(Dispatchers.IO) { runCatching { projectManager.listProjects() }.getOrDefault(emptyList()) }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    ScaledDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(12.dp))
                .background(SanwolfPanel)
                .border(1.5.dp, SanwolfGold, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PROJECTS", fontSize = 14.sp, fontWeight = FontWeight.Black, color = SanwolfGold, letterSpacing = 0.8.sp)
                    Text(
                        "Saved automatically on this device. Share a .sanwolf file to move a project to another phone or a collaborator.",
                        fontSize = 10.sp,
                        color = SanwolfTextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close projects", tint = SanwolfTextPrimary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SidebarActionRow(label = "New", icon = Icons.Default.Add, tint = SanwolfCyan, onClick = onNewProject, modifier = Modifier.weight(1f))
                SidebarActionRow(label = "Save as…", icon = Icons.Default.SaveAs, tint = SanwolfGold, onClick = { showSaveAs = true }, modifier = Modifier.weight(1f))
                SidebarActionRow(label = "Import .sanwolf", icon = Icons.Default.FileUpload, tint = SanwolfLime, onClick = { fileActions.importFile() }, modifier = Modifier.weight(1.3f))
            }

            val list = projects
            when {
                list == null -> SidebarEmptyState("Loading projects…")
                list.isEmpty() -> SidebarEmptyState("No saved projects yet. Your work is saved automatically as soon as you change something.")
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(list, key = { it.fileId }) { summary ->
                        ProjectsScreenRow(
                            summary = summary,
                            isCurrent = summary.fileId == currentFileId,
                            onOpen = {
                                if (summary.fileId == currentFileId) toast("'${summary.title}' is already open")
                                else { onOpenProject(summary.fileId); onDismiss() }
                            },
                            onRename = { renameTarget = summary },
                            onDuplicate = { duplicateTarget = summary },
                            onDelete = { deleteTarget = summary },
                            onShare = {
                                if (summary.fileId == currentFileId) fileActions.shareCurrent(project) else fileActions.shareSaved(summary.fileId)
                            },
                            onSaveToDevice = {
                                if (summary.fileId == currentFileId) fileActions.saveCurrentToDevice(project) else fileActions.saveSavedToDevice(summary.fileId)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSaveAs) {
        SidebarNameDialog(
            title = "Save as",
            initialValue = if (currentFileId == null) project.title else "${project.title} copy",
            confirmLabel = "Save",
            onConfirm = { name ->
                showSaveAs = false
                onSaveAs(name)
                localRefresh++
            },
            onDismiss = { showSaveAs = false }
        )
    }
    renameTarget?.let { target ->
        SidebarNameDialog(
            title = "Rename project",
            initialValue = target.title,
            confirmLabel = "Rename",
            onConfirm = { name ->
                renameTarget = null
                if (target.fileId == currentFileId) {
                    // The open project: change the working copy; autosave writes it.
                    onCurrentProjectRenamed(name)
                    scope.launch {
                        withContext(Dispatchers.IO) { projectManager.rename(target.fileId, name) }
                        localRefresh++
                    }
                } else {
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { projectManager.rename(target.fileId, name) }
                        res.onFailure { toast("Couldn't rename: ${it.message ?: "error"}") }
                        localRefresh++
                    }
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
                    res.onSuccess { toast("Duplicated as '$name'") }.onFailure { toast("Couldn't duplicate: ${it.message ?: "error"}") }
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
                "This deletes the saved project and its audio from this device. The open session stays on screen and will be saved again as a new project if you keep editing."
            else
                "This permanently deletes the project and its audio from this device.",
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

@Composable
private fun ProjectsScreenRow(
    summary: LocalProjectSummary,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSaveToDevice: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val modified = DateUtils.getRelativeTimeSpanString(summary.lastModified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isCurrent) SanwolfGold.copy(alpha = 0.12f) else SanwolfPanelElevated)
            .border(1.dp, if (isCurrent) SanwolfGold.copy(alpha = 0.6f) else SanwolfPanelBorder.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = if (isCurrent) SanwolfGold else SanwolfTextSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SanwolfTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Edited $modified • ${summary.bpm} BPM • ${summary.trackCount} tracks" + if (isCurrent) " • open" else "",
                fontSize = 11.sp,
                color = SanwolfTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Share, contentDescription = "Share ${summary.title} as a project file", tint = SanwolfCyan)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options for ${summary.title}", tint = SanwolfTextSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.background(SanwolfPanel)) {
                DropdownMenuItem(
                    text = { Text("Open", color = SanwolfTextPrimary) },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = SanwolfTextSecondary) },
                    onClick = { menuOpen = false; onOpen() }
                )
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
                    text = { Text("Share .sanwolf file", color = SanwolfTextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = SanwolfTextSecondary) },
                    onClick = { menuOpen = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text("Save .sanwolf to device…", color = SanwolfTextPrimary) },
                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = SanwolfTextSecondary) },
                    onClick = { menuOpen = false; onSaveToDevice() }
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
