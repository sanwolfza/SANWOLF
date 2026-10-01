package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ModularGraph
import com.example.model.ModularNode
import com.example.model.ModularNodeType
import com.example.model.NodeCable
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfLime
import com.example.ui.theme.SanwolfMagenta
import com.example.ui.theme.SanwolfOrange
import com.example.ui.theme.SanwolfPanel
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfPanelElevated
import com.example.ui.theme.SanwolfPurple
import com.example.ui.theme.SanwolfTextMuted
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary
import kotlin.math.roundToInt

@Composable
fun ModularSynthView(
    graph: ModularGraph,
    onTestSound: () -> Unit,
    onGenerateAiPatch: (description: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var cableConnectingStart by remember { mutableStateOf<Pair<String, String>?>(null) } // NodeId, Port

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SanwolfBlack)
            .padding(8.dp)
    ) {
        // --- Modular Top Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SanwolfPanel)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AI MODULAR SYNTHESIS MATRIX",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfGold,
                    letterSpacing = 0.8.sp
                )

                Text(
                    text = "${graph.nodes.size} Nodes • ${graph.cables.size} Patch Cables",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SanwolfCyan
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Test Sound Button
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfPanelElevated)
                        .border(1.dp, SanwolfLime, RoundedCornerShape(4.dp))
                        .clickable { onTestSound() }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Test", tint = SanwolfLime, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PREVIEW VOICE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfLime)
                    }
                }

                // AI Patch Preset Generator
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SanwolfGold)
                        .clickable { onGenerateAiPatch("Aggressive Cyberpunk Lead with screaming filter and stereo delay") }
                        .padding(horizontal = 8.dp)
                        .testTag("ai_patch_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = SanwolfBlack, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI RE-PATCH", fontSize = 10.sp, fontWeight = FontWeight.Black, color = SanwolfBlack)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- Interactive Node Canvas & Patch Cables ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0A0D))
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(8.dp))
        ) {
            // Background grid pattern & Patch Cables Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 32f
                for (x in 0..(size.width / step).toInt()) {
                    drawLine(
                        color = Color(0xFF13141A),
                        start = Offset(x * step, 0f),
                        end = Offset(x * step, size.height),
                        strokeWidth = 0.5f
                    )
                }
                for (y in 0..(size.height / step).toInt()) {
                    drawLine(
                        color = Color(0xFF13141A),
                        start = Offset(0f, y * step),
                        end = Offset(size.width, y * step),
                        strokeWidth = 0.5f
                    )
                }

                // Draw glowing patch cables connecting nodes
                for (cable in graph.cables) {
                    val fromNode = graph.nodes.find { it.id == cable.fromNodeId }
                    val toNode = graph.nodes.find { it.id == cable.toNodeId }

                    if (fromNode != null && toNode != null) {
                        val start = Offset(fromNode.x + 140f, fromNode.y + 60f)
                        val end = Offset(toNode.x + 10f, toNode.y + 60f)

                        val ctrl1 = Offset(start.x + 80f, start.y + 40f)
                        val ctrl2 = Offset(end.x - 80f, end.y + 40f)

                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, end.x, end.y)
                        }

                        // Glow shadow
                        drawPath(
                            path = path,
                            color = Color(cable.colorHex).copy(alpha = 0.3f),
                            style = Stroke(width = 6f)
                        )
                        // Core wire
                        drawPath(
                            path = path,
                            color = Color(cable.colorHex),
                            style = Stroke(width = 2.5f)
                        )

                        // Cable jacks
                        drawCircle(color = Color(cable.colorHex), radius = 5f, center = start)
                        drawCircle(color = Color(cable.colorHex), radius = 5f, center = end)
                    }
                }
            }

            // Draggable Node Components
            graph.nodes.forEach { node ->
                ModularNodeCard(
                    node = node,
                    isSelected = selectedNodeId == node.id,
                    onSelect = { selectedNodeId = node.id },
                    onPositionChange = { dx, dy ->
                        node.x = (node.x + dx).coerceIn(10f, 1200f)
                        node.y = (node.y + dy).coerceIn(10f, 500f)
                    },
                    onPortClick = { portName ->
                        val start = cableConnectingStart
                        if (start == null) {
                            cableConnectingStart = Pair(node.id, portName)
                        } else {
                            if (start.first != node.id) {
                                graph.cables.add(
                                    NodeCable(
                                        fromNodeId = start.first,
                                        fromPort = start.second,
                                        toNodeId = node.id,
                                        toPort = portName,
                                        colorHex = 0xFF00E5FF
                                    )
                                )
                            }
                            cableConnectingStart = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ModularNodeCard(
    node: ModularNode,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPositionChange: (Float, Float) -> Unit,
    onPortClick: (String) -> Unit
) {
    val headerColor = when (node.type) {
        ModularNodeType.OSCILLATOR -> SanwolfCyan
        ModularNodeType.FILTER -> SanwolfGold
        ModularNodeType.ADSR_ENV -> SanwolfLime
        ModularNodeType.LFO -> SanwolfPurple
        ModularNodeType.WOLF_DRIVE -> SanwolfMagenta
        ModularNodeType.SPACE_REVERB -> SanwolfOrange
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
            .width(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SanwolfPanel)
            .border(
                1.5.dp,
                if (isSelected) headerColor else SanwolfPanelBorder,
                RoundedCornerShape(8.dp)
            )
            .pointerInput(node) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPositionChange(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(headerColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = node.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfTextPrimary,
                        maxLines = 1
                    )
                }

                Text(
                    text = node.type.name.take(3),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = headerColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body Controls
            when (node.type) {
                ModularNodeType.OSCILLATOR -> {
                    Text("Freq: 440 Hz", fontSize = 9.sp, color = SanwolfTextSecondary)
                    Text("Wave: Sawtooth", fontSize = 9.sp, color = SanwolfCyan)
                }
                ModularNodeType.FILTER -> {
                    Text("Cutoff: 2.2 kHz", fontSize = 9.sp, color = SanwolfTextSecondary)
                    Text("Reso: 1.4 Q", fontSize = 9.sp, color = SanwolfGold)
                }
                ModularNodeType.ADSR_ENV -> {
                    Text("A: 15ms | D: 120ms", fontSize = 9.sp, color = SanwolfTextSecondary)
                    Text("S: 65% | R: 200ms", fontSize = 9.sp, color = SanwolfLime)
                }
                ModularNodeType.WOLF_DRIVE -> {
                    Text("Drive: 3.2x Hard", fontSize = 9.sp, color = SanwolfMagenta)
                }
                else -> {
                    Text("Signal: 100% OK", fontSize = 9.sp, color = SanwolfTextMuted)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // In / Out Jacks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // In Jack
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onPortClick("In") }
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(SanwolfPanelElevated)
                            .border(1.5.dp, headerColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("IN", fontSize = 8.sp, fontWeight = FontWeight.Black, color = SanwolfTextMuted)
                }

                // Out Jack
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onPortClick("Out") }
                ) {
                    Text("OUT", fontSize = 8.sp, fontWeight = FontWeight.Black, color = SanwolfTextMuted)
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(headerColor)
                    )
                }
            }
        }
    }
}
