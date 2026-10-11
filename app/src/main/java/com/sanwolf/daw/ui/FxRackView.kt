package com.sanwolf.daw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanwolf.daw.model.FX_RACK_SLOTS
import com.sanwolf.daw.model.FxSlot
import com.sanwolf.daw.model.FxModules
import com.sanwolf.daw.model.FxType
import com.sanwolf.daw.ui.theme.*

/** A new slot of [type] at its module defaults (see [FxModules.newSlot]). */
fun defaultFxSlot(type: FxType): FxSlot = FxModules.newSlot(type)

/**
 * FL Studio Mobile style insert rack: ordered slots (max [FX_RACK_SLOTS]). Each slot shows its
 * module name and a bypass LED, can be moved up/down or removed, and expands into a dark module
 * panel with rotary knobs, presets and a small visualisation ([FxModulePanel]).
 *
 * [slots] is read for display; [ensureSlots] returns the live list (creating it on first add)
 * so merely viewing an old project's empty rack never changes the project.
 * [onStructureChanged] after add / remove / reorder (the engine rebuilds its routing);
 * [onParamsCommitted] after a knob release, double-tap reset, preset, switch or bypass toggle
 * (one undo entry each). Knob drags write the slot directly; the audio thread reads it every block.
 */
@Composable
fun FxRackView(
    title: String,
    slots: List<FxSlot>,
    ensureSlots: () -> MutableList<FxSlot>,
    revision: Int,
    onStructureChanged: () -> Unit,
    onParamsCommitted: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledNote: String? = null,
    footerNote: String? = null
) {
    // Slots are plain objects edited in place: bump this to redraw after an edit
    var tick by remember { mutableIntStateOf(0) }
    val rev = tick + revision
    var expandedId by remember { mutableStateOf<String?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    val list = slots.toList()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(6.dp))
            .padding(6.dp)
            .testTag("fx_rack"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SanwolfGold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.weight(1f)
            )
            Text("${list.size}/$FX_RACK_SLOTS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SanwolfTextMuted)
        }

        if (!enabled) {
            Text(disabledNote ?: "Inserts are not available here.", fontSize = 11.sp, color = SanwolfTextSecondary)
            return@Column
        }

        list.forEachIndexed { index, slot ->
            key(slot.id) {
                FxSlotRow(
                    index = index,
                    slot = slot,
                    revision = rev,
                    isFirst = index == 0,
                    isLast = index == list.lastIndex,
                    expanded = expandedId == slot.id,
                    onToggleExpand = { expandedId = if (expandedId == slot.id) null else slot.id },
                    onToggleBypass = {
                        slot.bypassed = !slot.bypassed
                        tick++
                        onParamsCommitted()
                    },
                    onMove = { delta ->
                        val live = ensureSlots()
                        val from = live.indexOfFirst { it.id == slot.id }
                        val to = from + delta
                        if (from >= 0 && to in live.indices) {
                            java.util.Collections.swap(live, from, to)
                            tick++
                            onStructureChanged()
                        }
                    },
                    onRemove = {
                        ensureSlots().removeAll { it.id == slot.id }
                        if (expandedId == slot.id) expandedId = null
                        tick++
                        onStructureChanged()
                    },
                    onParamChange = { tick++ },
                    onParamDone = {
                        tick++
                        onParamsCommitted()
                    }
                )
            }
        }

        if (list.size < FX_RACK_SLOTS) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(0.5.dp, SanwolfGoldDim, RoundedCornerShape(4.dp))
                        .clickable { showAddMenu = true }
                        .semantics { contentDescription = "Add a module to slot ${list.size + 1}" }
                        .padding(horizontal = 6.dp)
                        .testTag("fx_rack_add")
                ) {
                    Text("${list.size + 1}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SanwolfTextMuted, modifier = Modifier.width(14.dp))
                    Icon(Icons.Default.Add, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add module", fontSize = 11.sp, color = SanwolfGold, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    FxModules.byCategory().forEach { (category, specs) ->
                        // Category header (not selectable)
                        Text(
                            category.label.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SanwolfGold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp)
                        )
                        specs.forEach { spec ->
                            DropdownMenuItem(
                                text = { Text(spec.type.label, fontSize = 13.sp) },
                                onClick = {
                                    showAddMenu = false
                                    val live = ensureSlots()
                                    if (live.size < FX_RACK_SLOTS) {
                                        val slot = FxModules.newSlot(spec.type)
                                        live.add(slot)
                                        expandedId = slot.id
                                        tick++
                                        onStructureChanged()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (footerNote != null) {
            Text(footerNote, fontSize = 10.sp, color = SanwolfTextMuted)
        }
    }
}

@Composable
private fun FxSlotRow(
    index: Int,
    slot: FxSlot,
    revision: Int,
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleBypass: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onParamChange: () -> Unit,
    onParamDone: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE") val r = revision
    val type = slot.fxType
    val spec = type?.let { FxModules.spec(it) }
    val active = !slot.bypassed && type != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(SanwolfBlack)
            .border(
                0.5.dp,
                if (expanded && active) SanwolfGold.copy(alpha = 0.7f) else if (active) SanwolfGoldDim.copy(alpha = 0.6f) else SanwolfPanelBorder,
                RoundedCornerShape(4.dp)
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(start = 4.dp)
        ) {
            Text("${index + 1}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SanwolfTextMuted, modifier = Modifier.width(12.dp))
            // Bypass LED: lit gold = on, dark = bypassed
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 24.dp, height = 30.dp)
                    .clickable(enabled = type != null, onClick = onToggleBypass)
                    .semantics {
                        contentDescription = if (slot.bypassed) "Turn on ${type?.shortLabel ?: "module"}" else "Bypass ${type?.shortLabel ?: "module"}"
                    }
            ) {
                if (active) {
                    Box(Modifier.size(13.dp).clip(CircleShape).background(SanwolfOrange.copy(alpha = 0.25f)))
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (active) SanwolfGold else Color(0xFF2A2B30))
                        .border(0.5.dp, if (active) SanwolfOrange else SanwolfTextMuted, CircleShape)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onToggleExpand)
                    .semantics { contentDescription = "${type?.label ?: slot.type}. Tap to ${if (expanded) "hide" else "show"} controls." }
            ) {
                Text(
                    text = type?.shortLabel ?: "Unknown (${slot.type})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) SanwolfTextPrimary else SanwolfTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (spec != null) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        FxModules.matchingPreset(slot, spec) ?: spec.category.label,
                        fontSize = 9.sp,
                        color = if (active) SanwolfGoldDim else SanwolfTextMuted.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (active) SanwolfGold else SanwolfTextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(2.dp))
            }
            SlotIcon(Icons.Default.ArrowUpward, "Move up", enabled = !isFirst, onClick = { onMove(-1) })
            SlotIcon(Icons.Default.ArrowDownward, "Move down", enabled = !isLast, onClick = { onMove(1) })
            SlotIcon(Icons.Default.Close, "Remove", tint = SanwolfMagenta, onClick = onRemove)
        }
        if (expanded && type != null && spec != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
                FxModulePanel(
                    slot = slot,
                    spec = spec,
                    revision = revision,
                    enabled = !slot.bypassed,
                    onChange = onParamChange,
                    onCommit = onParamDone
                )
            }
        }
    }
}


@Composable
private fun SlotIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = SanwolfTextSecondary,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(width = 26.dp, height = 30.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else SanwolfTextMuted.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
    }
}

@Composable
fun RackSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    color: Color,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, color = if (enabled) SanwolfTextPrimary else SanwolfTextMuted)
            Text(display, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (enabled) color else SanwolfTextMuted)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .semantics { contentDescription = label },
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}
