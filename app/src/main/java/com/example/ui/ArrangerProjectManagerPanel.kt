package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.firebase.CloudProjectSummary
import com.example.firebase.FirebaseDawManager
import com.example.model.ProjectData
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArrangerProjectManagerPanel(
    currentProject: ProjectData,
    firebaseManager: FirebaseDawManager,
    onLoadProject: (ProjectData) -> Unit,
    onProjectSaved: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var projectList by remember { mutableStateOf<List<CloudProjectSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog States
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showRenameDialogForProject by remember { mutableStateOf<CloudProjectSummary?>(null) }
    var showDeleteConfirmForProject by remember { mutableStateOf<CloudProjectSummary?>(null) }
    var showTemplateMenu by remember { mutableStateOf(false) }

    var newProjectTitleInput by remember { mutableStateOf(currentProject.title) }
    var renameInput by remember { mutableStateOf("") }

    fun refreshProjects() {
        scope.launch {
            isLoading = true
            val res = firebaseManager.loadCloudProjects()
            isLoading = false
            res.onSuccess {
                projectList = it
            }.onFailure { err ->
                statusMessage = "Error loading projects: ${err.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshProjects()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("project_mgmt_panel"),
        color = SanwolfBlack.copy(alpha = 0.98f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // --- Panel Header Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SanwolfPanelElevated)
                    .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = SanwolfGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PROJECT MANAGEMENT & FIRESTORE REPO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = SanwolfGold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Active: '${currentProject.title}' • ${currentProject.bpm} BPM • ${currentProject.tracks.size} Tracks",
                            fontSize = 10.sp,
                            color = SanwolfCyan
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick Save
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                val res = firebaseManager.saveProjectToCloud(currentProject)
                                isLoading = false
                                res.onSuccess {
                                    statusMessage = "Project '${currentProject.title}' saved to Firestore!"
                                    onProjectSaved()
                                    refreshProjects()
                                    Toast.makeText(context, "Saved to Firestore!", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    statusMessage = "Save completed to local cache: ${it.message}"
                                    refreshProjects()
                                }
                            }
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("panel_save_firestore_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE (FIRESTORE)", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }

                    // Save As New Copy
                    Button(
                        onClick = {
                            newProjectTitleInput = "${currentProject.title}_COPY"
                            showSaveAsDialog = true
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("panel_save_as_new_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfPanel)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE AS...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfGold)
                    }

                    // Templates / New Session
                    Button(
                        onClick = { showTemplateMenu = !showTemplateMenu },
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("panel_new_project_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfLime)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("NEW SESSION", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }

                    // Refresh
                    IconButton(
                        onClick = { refreshProjects() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("panel_refresh_firestore_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SanwolfTextSecondary)
                    }

                    // Close Panel
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- New Project Templates Bar (if expanded or persistent) ---
            AnimatedVisibility(visible = showTemplateMenu) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfPanel)
                        .border(1.dp, SanwolfLime.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "START NEW DAW PROJECT FROM PRESET TEMPLATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfLime
                        )
                        IconButton(onClick = { showTemplateMenu = false }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = SanwolfTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val templates = listOf(
                            Triple("Trap 808 Drill", "140 BPM • 808 sub, hard kicks & rolls", SanwolfMagenta),
                            Triple("Cyberpunk Synthwave", "128 BPM • Saw leads, 909 drums & 303 bass", SanwolfCyan),
                            Triple("Amapiano Groove", "113 BPM • Log drum, shakers & rhodes", SanwolfGold),
                            Triple("Lo-Fi Midnight Chill", "84 BPM • Tape organ, dusty vinyl beats", SanwolfOrange),
                            Triple("Empty 4-Track Session", "120 BPM • Clean multitrack starter", SanwolfTextSecondary)
                        )

                        items(templates) { (tmplName, desc, color) ->
                            Column(
                                modifier = Modifier
                                    .width(170.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SanwolfBlack)
                                    .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .clickable {
                                        val newProject = firebaseManager.createProjectFromTemplate(tmplName)
                                        scope.launch {
                                            firebaseManager.saveProjectToCloud(newProject)
                                            onLoadProject(newProject)
                                            showTemplateMenu = false
                                            refreshProjects()
                                            Toast.makeText(context, "Loaded template '$tmplName'", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(8.dp)
                            ) {
                                Text(tmplName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(desc, fontSize = 9.sp, color = SanwolfTextMuted, lineHeight = 12.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("LOAD TEMPLATE →", fontSize = 9.sp, fontWeight = FontWeight.Black, color = color)
                            }
                        }
                    }
                }
            }

            // --- Search & Status Summary Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Search Bar
                Row(
                    modifier = Modifier
                        .width(260.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfPanel)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SanwolfTextMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    SanwolfTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter projects...", fontSize = 11.sp, color = SanwolfTextMuted) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_search_input")
                    )
                }

                // Producer / Status info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = SanwolfGold,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "Producer: ${firebaseManager.currentProducerName} • ${projectList.size} Cloud Project(s)",
                        fontSize = 11.sp,
                        color = SanwolfTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- Projects List ---
            val filteredProjects = projectList.filter {
                searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
            }

            if (filteredProjects.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfPanel)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = SanwolfTextMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No projects match '$searchQuery'" else "No Firestore projects saved yet.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Click 'SAVE (FIRESTORE)' or 'NEW SESSION' to create your first cloud project.",
                            fontSize = 11.sp,
                            color = SanwolfTextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredProjects) { projSummary ->
                        val isCurrentSession = projSummary.id == currentProject.id
                        ProjectCardItem(
                            projectSummary = projSummary,
                            isCurrentSession = isCurrentSession,
                            onLoad = {
                                scope.launch {
                                    isLoading = true
                                    val res = firebaseManager.loadFullProject(projSummary.id)
                                    isLoading = false
                                    res.onSuccess { loadedProj ->
                                        onLoadProject(loadedProj)
                                        statusMessage = "Project '${loadedProj.title}' loaded into Arranger!"
                                        Toast.makeText(context, "Loaded '${loadedProj.title}'", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        statusMessage = "Error loading project: ${err.message}"
                                        Toast.makeText(context, "Load failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDuplicate = {
                                scope.launch {
                                    isLoading = true
                                    val res = firebaseManager.duplicateProjectInCloud(projSummary.id, "${projSummary.title}_COPY")
                                    isLoading = false
                                    res.onSuccess { dup ->
                                        statusMessage = "Duplicated project '${dup.title}' in Firestore!"
                                        refreshProjects()
                                        Toast.makeText(context, "Cloned '${dup.title}'", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        statusMessage = "Duplicate failed: ${err.message}"
                                    }
                                }
                            },
                            onRename = {
                                showRenameDialogForProject = projSummary
                                renameInput = projSummary.title
                            },
                            onDelete = {
                                showDeleteConfirmForProject = projSummary
                            }
                        )
                    }
                }
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusMessage!!,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SanwolfLime
                )
            }
        }
    }

    // --- Save As Dialog ---
    if (showSaveAsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            containerColor = SanwolfPanel,
            title = {
                Text("Save Project As New Snapshot", color = SanwolfGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Enter a title for this new Firestore project file:", fontSize = 12.sp, color = SanwolfTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    SanwolfTextField(
                        value = newProjectTitleInput,
                        onValueChange = { newProjectTitleInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectTitleInput.isNotBlank()) {
                            showSaveAsDialog = false
                            scope.launch {
                                isLoading = true
                                val newProj = currentProject.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    title = newProjectTitleInput.trim(),
                                    lastSavedTimestamp = System.currentTimeMillis()
                                )
                                // Deep copy tracks list
                                val tracksCopy = currentProject.tracks.map { t ->
                                    val copy = t.copy(id = java.util.UUID.randomUUID().toString())
                                    System.arraycopy(t.steps, 0, copy.steps, 0, 32)
                                    System.arraycopy(t.stepVelocities, 0, copy.stepVelocities, 0, 32)
                                    t.notes.forEach { n -> copy.notes.add(n.copy(id = java.util.UUID.randomUUID().toString())) }
                                    t.automationLanes.forEach { al ->
                                        copy.automationLanes.add(al.copy(id = java.util.UUID.randomUUID().toString(), points = al.points.map { it.copy() }.toMutableList()))
                                    }
                                    copy
                                }
                                newProj.tracks.clear()
                                newProj.tracks.addAll(tracksCopy)

                                val res = firebaseManager.saveProjectToCloud(newProj)
                                isLoading = false
                                res.onSuccess {
                                    onLoadProject(newProj)
                                    refreshProjects()
                                    Toast.makeText(context, "Saved as '${newProj.title}'!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                ) {
                    Text("SAVE PROJECT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) {
                    Text("CANCEL", color = SanwolfTextSecondary, fontSize = 11.sp)
                }
            }
        )
    }

    // --- Rename Dialog ---
    if (showRenameDialogForProject != null) {
        val targetProj = showRenameDialogForProject!!
        AlertDialog(
            onDismissRequest = { showRenameDialogForProject = null },
            containerColor = SanwolfPanel,
            title = {
                Text("Rename Project", color = SanwolfCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Enter new name for '${targetProj.title}':", fontSize = 12.sp, color = SanwolfTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    SanwolfTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            val newName = renameInput.trim()
                            showRenameDialogForProject = null
                            scope.launch {
                                isLoading = true
                                firebaseManager.renameProjectInCloud(targetProj.id, newName)
                                if (targetProj.id == currentProject.id) {
                                    currentProject.title = newName
                                }
                                isLoading = false
                                refreshProjects()
                                Toast.makeText(context, "Renamed to '$newName'", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfCyan)
                ) {
                    Text("RENAME", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogForProject = null }) {
                    Text("CANCEL", color = SanwolfTextSecondary, fontSize = 11.sp)
                }
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    if (showDeleteConfirmForProject != null) {
        val targetProj = showDeleteConfirmForProject!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmForProject = null },
            containerColor = SanwolfPanel,
            title = {
                Text("Delete Project from Firestore?", color = SanwolfMagenta, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete '${targetProj.title}'? This action cannot be undone.",
                    fontSize = 12.sp,
                    color = SanwolfTextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmForProject = null
                        scope.launch {
                            isLoading = true
                            firebaseManager.deleteProjectFromCloud(targetProj.id)
                            isLoading = false
                            refreshProjects()
                            Toast.makeText(context, "Deleted '${targetProj.title}'", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfMagenta)
                ) {
                    Text("DELETE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmForProject = null }) {
                    Text("CANCEL", color = SanwolfTextSecondary, fontSize = 11.sp)
                }
            }
        )
    }
}

@Composable
private fun ProjectCardItem(
    projectSummary: CloudProjectSummary,
    isCurrentSession: Boolean,
    onLoad: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(projectSummary.lastSavedTimestamp) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(projectSummary.lastSavedTimestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrentSession) SanwolfPanelElevated else SanwolfPanel)
            .border(
                1.dp,
                if (isCurrentSession) SanwolfGold else SanwolfPanelBorder,
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Column: Details
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isCurrentSession) SanwolfGold.copy(alpha = 0.2f) else SanwolfBlack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCurrentSession) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isCurrentSession) SanwolfGold else SanwolfCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = projectSummary.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfTextPrimary
                    )
                    if (isCurrentSession) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(SanwolfGold)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("CURRENT OPEN", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${projectSummary.bpm} BPM",
                        fontSize = 10.sp,
                        color = SanwolfCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("•", fontSize = 10.sp, color = SanwolfTextMuted)
                    Text(
                        text = "${projectSummary.trackCount} Tracks",
                        fontSize = 10.sp,
                        color = SanwolfOrange
                    )
                    Text("•", fontSize = 10.sp, color = SanwolfTextMuted)
                    Text(
                        text = "Saved $dateStr",
                        fontSize = 10.sp,
                        color = SanwolfTextMuted
                    )
                    if (!projectSummary.isLocalOnly) {
                        Text("•", fontSize = 10.sp, color = SanwolfTextMuted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = SanwolfLime, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Firestore", fontSize = 9.sp, color = SanwolfLime)
                        }
                    }
                }
            }
        }

        // Right Actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Load Button
            Button(
                onClick = onLoad,
                modifier = Modifier
                    .height(28.dp)
                    .testTag("load_project_btn_${projectSummary.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentSession) SanwolfPanelBorder else SanwolfCyan
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (isCurrentSession) "RELOAD" else "LOAD",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }

            // Duplicate Button
            IconButton(
                onClick = onDuplicate,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("duplicate_project_btn_${projectSummary.id}")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Clone", tint = SanwolfGold, modifier = Modifier.size(14.dp))
            }

            // Rename Button
            IconButton(
                onClick = onRename,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("rename_project_btn_${projectSummary.id}")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = SanwolfTextSecondary, modifier = Modifier.size(14.dp))
            }

            // Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("delete_project_btn_${projectSummary.id}")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SanwolfMagenta, modifier = Modifier.size(14.dp))
            }
        }
    }
}
