package com.sanwolf.daw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import com.sanwolf.daw.model.FxType
import com.sanwolf.daw.ui.theme.*

/** A new slot of [type] with sensible starting values. */
fun defaultFxSlot(type: FxType): FxSlot = when (type) {
    FxType.EQ -> FxSlot(type = type.key, lowGainDb = 0f, highGainDb = 0f)
    FxType.COMPRESSOR -> FxSlot(type = type.key, thresholdDb = -18f)
    FxType.STEREO_WIDTH -> FxSlot(type = type.key, width = 1f)
    FxType.LIMITER -> FxSlot(type = type.key, driveDb = 0f, ceilingDb = -1f)
}

/**
 * FL Studio Mobile style insert rack: ordered slots (max [FX_RACK_SLOTS]). Each slot can be
 * bypassed, moved up/down, removed, and tapped to show its controls inline.
 *
 * [slots] is read for display; [ensureSlots] returns the live list (creating it on first add)
 * so merely viewing an old project's empty rack never changes the project.
 * [onStructureChanged] after add / remove / reorder (the engine rebuilds its routing);
 * [onParamsCommitted] after a slider release or bypass toggle (one undo entry). Slider drags
 * write the slot fields directly; the audio thread reads them every block.
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
    @Suppress("UNUSED_VARIABLE") val redraw = tick + revision
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
                color = SanwolfTextMuted,
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
                    onParamDone = onParamsCommitted
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
                        .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(4.dp))
                        .clickable { showAddMenu = true }
                        .semantics { contentDescription = "Add a module to slot ${list.size + 1}" }
                        .padding(horizontal = 6.dp)
                        .testTag("fx_rack_add")
                ) {
                    Text("${list.size + 1}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SanwolfTextMuted, modifier = Modifier.width(14.dp))
                    Icon(Icons.Default.Add, contentDescription = null, tint = SanwolfCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add module", fontSize = 11.sp, color = SanwolfCyan, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    FxType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label, fontSize = 13.sp) },
                            onClick = {
                                showAddMenu = false
                                val live = ensureSlots()
                                if (live.size < FX_RACK_SLOTS) {
                                    val slot = defaultFxSlot(type)
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

        if (footerNote != null) {
            Text(footerNote, fontSize = 10.sp, color = SanwolfTextMuted)
        }
    }
}

@Composable
private fun FxSlotRow(
    index: Int,
    slot: FxSlot,
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
    val type = slot.fxType
    val active = !slot.bypassed && type != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(SanwolfBlack)
            .border(0.5.dp, if (active) SanwolfCyan.copy(alpha = 0.45f) else SanwolfPanelBorder, RoundedCornerShape(4.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(start = 6.dp)
        ) {
            Text("${index + 1}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SanwolfTextMuted, modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) SanwolfLime else SanwolfTextMuted)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = type?.shortLabel ?: "Unknown (${slot.type})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) SanwolfTextPrimary else SanwolfTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleExpand)
                    .semantics { contentDescription = "${type?.label ?: slot.type}. Tap to ${if (expanded) "hide" else "show"} controls." }
                    .padding(vertical = 6.dp)
            )
            SlotIcon(
                Icons.Default.PowerSettingsNew,
                if (slot.bypassed) "Turn on ${type?.shortLabel ?: "module"}" else "Bypass ${type?.shortLabel ?: "module"}",
                tint = if (slot.bypassed) SanwolfTextMuted else SanwolfLime,
                onClick = onToggleBypass
            )
            SlotIcon(Icons.Default.KeyboardArrowUp, "Move up", enabled = !isFirst, onClick = { onMove(-1) })
            SlotIcon(Icons.Default.KeyboardArrowDown, "Move down", enabled = !isLast, onClick = { onMove(1) })
            SlotIcon(Icons.Default.Close, "Remove", tint = SanwolfMagenta, onClick = onRemove)
        }
        if (expanded && type != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            ) {
                FxSlotControls(type, slot, enabled = !slot.bypassed, onChange = onParamChange, onDone = onParamDone)
            }
        }
    }
}

@Composable
private fun FxSlotControls(type: FxType, slot: FxSlot, enabled: Boolean, onChange: () -> Unit, onDone: () -> Unit) {
    when (type) {
        FxType.EQ -> {
            RackSlider("Low shelf 120 Hz", slot.lowGainDb, -12f..12f, signedDb(slot.lowGainDb), SanwolfOrange, enabled,
                { slot.lowGainDb = it; onChange() }, onDone)
            RackSlider("High shelf 6 kHz", slot.highGainDb, -12f..12f, signedDb(slot.highGainDb), SanwolfCyan, enabled,
                { slot.highGainDb = it; onChange() }, onDone)
        }
        FxType.COMPRESSOR -> {
            val t = slot.thresholdDb
            RackSlider("Threshold", t, -40f..0f, if (t >= -0.5f) "Off" else "%.1f dB".format(t), SanwolfGold, enabled,
                { slot.thresholdDb = it; onChange() }, onDone)
            Text("2:1, 10 ms attack, 150 ms release, auto make-up.", fontSize = 10.sp, color = SanwolfTextMuted)
        }
        FxType.STEREO_WIDTH -> {
            RackSlider("Width", slot.width, 0f..2f, "${(slot.width * 100).toInt()}%", SanwolfPurple, enabled,
                { slot.width = it; onChange() }, onDone)
            Text("0% mono, 100% unchanged, 200% extra wide.", fontSize = 10.sp, color = SanwolfTextMuted)
        }
        FxType.LIMITER -> {
            RackSlider("Drive", slot.driveDb, 0f..12f, signedDb(slot.driveDb), SanwolfMagenta, enabled,
                { slot.driveDb = it; onChange() }, onDone)
            RackSlider("Ceiling", slot.ceilingDb, -6f..0f, "%.1f dB".format(slot.ceilingDb), SanwolfLime, enabled,
                { slot.ceilingDb = it; onChange() }, onDone)
            Text("5 ms look-ahead (adds 5 ms latency to this channel).", fontSize = 10.sp, color = SanwolfTextMuted)
        }
    }
}

private fun signedDb(v: Float): String = (if (v > 0.05f) "+" else "") + "%.1f dB".format(v)

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
