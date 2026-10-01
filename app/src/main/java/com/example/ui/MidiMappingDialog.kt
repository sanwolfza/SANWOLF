package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.MidiMapping
import com.example.model.ProjectData

@Composable
fun MidiMappingDialog(
    project: ProjectData,
    onDismiss: () -> Unit,
    onAddMapping: (MidiMapping) -> Unit,
    onDeleteMapping: (MidiMapping) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.8f),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("MIDI Controller Mapping", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn {
                    items(project.midiMappings) { mapping ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("CC ${mapping.midiCc}: ${mapping.targetParam}")
                            IconButton(onClick = { onDeleteMapping(mapping) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
                
                Button(onClick = { /* Implement add logic */ }) {
                    Text("Add Mapping")
                }
            }
        }
    }
}
