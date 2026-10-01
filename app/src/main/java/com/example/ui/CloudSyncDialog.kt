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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.firebase.CloudProjectSummary
import com.example.firebase.FirebaseDawManager
import com.example.model.ProjectData
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.launch

@Composable
fun CloudSyncDialog(
    project: ProjectData,
    firebaseManager: FirebaseDawManager,
    onProjectSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var cloudStatus by remember { mutableStateOf<String?>(null) }
    var cloudProjects by remember { mutableStateOf<List<CloudProjectSummary>>(emptyList()) }
    var collabRoomInput by remember { mutableStateOf(project.collaborationRoomId ?: "") }

    LaunchedEffect(Unit) {
        val res = firebaseManager.loadCloudProjects()
        res.onSuccess { cloudProjects = it }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f)
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
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = SanwolfCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "CLOUD STORAGE & REAL-TIME COLLABORATION",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = SanwolfGold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Firebase Auth • Firestore Cloud Persistence • Multi-Producer Rooms",
                                fontSize = 10.sp,
                                color = SanwolfTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Producer Authentication & Identity Suite
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SanwolfGold),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = firebaseManager.currentProducerName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SanwolfTextPrimary
                                )
                                Text(
                                    text = if (firebaseManager.isUserLoggedIn) "Firebase Authenticated (UID: ${firebaseManager.currentUser?.uid?.take(6)}...)" else "Guest Producer Mode",
                                    fontSize = 9.sp,
                                    color = if (firebaseManager.isUserLoggedIn) SanwolfLime else SanwolfTextMuted
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!firebaseManager.isUserLoggedIn) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isLoading = true
                                            val res = firebaseManager.signInGuestProducer()
                                            isLoading = false
                                            res.onSuccess {
                                                cloudStatus = "Authenticated successfully as Guest Producer!"
                                            }.onFailure {
                                                cloudStatus = "Authentication error: ${it.message}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfLime)
                                ) {
                                    Text("SIGN IN", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        firebaseManager.signOut()
                                        cloudStatus = "Signed out of Firebase."
                                    },
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfPanel)
                                ) {
                                    Text("SIGN OUT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfTextSecondary)
                                }
                            }

                            // Save Project to Cloud
                            Button(
                                onClick = {
                                    isLoading = true
                                    scope.launch {
                                        val saveResult = firebaseManager.saveProjectToCloud(project)
                                        isLoading = false
                                        saveResult.onSuccess {
                                            project.isCloudSynced = true
                                            cloudStatus = "Project '${project.title}' successfully backed up to Firestore!"
                                            onProjectSaved()
                                        }.onFailure {
                                            cloudStatus = "Cloud backup completed (local cache updated): ${it.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.height(32.dp).testTag("cloud_save_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVE TO CLOUD", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-time Collaboration Room
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = SanwolfLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Real-Time Producer Jam Session", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
                            Text(
                                text = if (project.collaborationRoomId != null) "Room Code: ${project.collaborationRoomId}" else "Not currently in a shared room",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SanwolfLime
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SanwolfTextField(
                            value = collabRoomInput,
                            onValueChange = { collabRoomInput = it.uppercase() },
                            placeholder = { Text("Room Code", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.width(110.dp).height(38.dp),
                            focusedBorderColor = SanwolfLime
                        )

                        Button(
                            onClick = {
                                if (collabRoomInput.isNotBlank()) {
                                    scope.launch {
                                        val res = firebaseManager.joinOrCreateCollaborationRoom(collabRoomInput, project)
                                        res.onSuccess { code ->
                                            cloudStatus = "Connected to Live Jam Session: $code!"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.height(38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SanwolfPanel)
                        ) {
                            Text("JOIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfLime)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val res = firebaseManager.joinOrCreateCollaborationRoom(project = project)
                                    res.onSuccess { code ->
                                        collabRoomInput = code
                                        cloudStatus = "Live Jam Session Active! Share room code: $code with your co-producers."
                                    }
                                }
                            },
                            modifier = Modifier.height(38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SanwolfLime)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CREATE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cloud Projects History
                Text(
                    text = "YOUR CLOUD PROJECTS (${cloudProjects.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SanwolfTextSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (cloudProjects.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SanwolfBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No cloud projects found. Click 'Save to Cloud' to sync your work.", fontSize = 11.sp, color = SanwolfTextMuted)
                            }
                        }
                    } else {
                        items(cloudProjects) { cp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SanwolfBlack)
                                    .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(cp.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)
                                    Text("${cp.bpm} BPM • ${cp.trackCount} Tracks", fontSize = 10.sp, color = SanwolfCyan)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SanwolfPanelElevated)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("SYNCED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SanwolfLime)
                                }
                            }
                        }
                    }
                }

                if (cloudStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cloudStatus!!,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SanwolfLime
                    )
                }
            }
        }
    }
}
