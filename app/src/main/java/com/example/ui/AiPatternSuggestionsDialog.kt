package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.ai.AiArrangementAnalyzer
import com.example.ai.PolyrhythmicPatternSuggestion
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.model.TrackType
import com.example.ui.theme.*

@Composable
fun AiPatternSuggestionsDialog(
    project: ProjectData,
    onApplySuggestion: (PolyrhythmicPatternSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGenre by remember { mutableStateOf("Techno") }
    val genres = listOf("Techno", "Trap", "Electronic / Ambient")
    val suggestions = remember(selectedGenre, project) {
        AiArrangementAnalyzer.analyzeAndSuggest(project, selectedGenre)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, SanwolfGold, RoundedCornerShape(16.dp)),
            color = SanwolfPanel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AI ARRANGEMENT & POLYRHYTHM ANALYZER",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = SanwolfGold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Optimal Block Placements & Polyrhythmic Patterns by Genre",
                                fontSize = 11.sp,
                                color = SanwolfCyan
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SanwolfTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Genre Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genres.forEach { genre ->
                        val isSelected = genre == selectedGenre
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) SanwolfGold else SanwolfPanelElevated)
                                .border(1.dp, if (isSelected) SanwolfGold else SanwolfPanelBorder, RoundedCornerShape(6.dp))
                                .clickable { selectedGenre = genre }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("genre_chip_$genre"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = genre.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) Color.Black else SanwolfTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Suggestions List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(suggestions) { suggestion ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp)),
                            color = SanwolfPanelElevated
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = suggestion.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SanwolfTextPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SanwolfCyan.copy(alpha = 0.2f))
                                            .border(0.5.dp, SanwolfCyan, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${suggestion.recommendedStepCount} Steps",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = SanwolfCyan
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = suggestion.description,
                                    fontSize = 11.sp,
                                    color = SanwolfTextSecondary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { onApplySuggestion(suggestion) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .testTag("apply_suggestion_${suggestion.title.take(10)}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold)
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "APPLY PATTERN & BLOCK PLACEMENT",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
