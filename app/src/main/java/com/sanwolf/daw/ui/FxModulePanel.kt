package com.sanwolf.daw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanwolf.daw.audio.Biquad
import com.sanwolf.daw.audio.BiquadMath
import com.sanwolf.daw.audio.FxMeters
import com.sanwolf.daw.model.FxIdx
import com.sanwolf.daw.model.FxModuleSpec
import com.sanwolf.daw.model.FxModules
import com.sanwolf.daw.model.FxParam
import com.sanwolf.daw.model.FxSlot
import com.sanwolf.daw.model.FxType
import com.sanwolf.daw.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val ModuleFace = Color(0xFF0B0B0D)
private val VizBg = Color(0xFF050506)
private val VizGrid = Color(0xFF1E1F24)

/**
 * The expanded module: preset picker, a small visualisation where it is cheap, choice switches
 * and a grid of [RotaryKnob]s. Every parameter goes through [FxModules.get]/[FxModules.set].
 * [onChange] while dragging (redraw), [onCommit] once per gesture / preset (one undo entry).
 * [revision] only forces recomposition after in-place slot edits.
 */
@Composable
fun FxModulePanel(
    slot: FxSlot,
    spec: FxModuleSpec,
    revision: Int,
    enabled: Boolean,
    onChange: () -> Unit,
    onCommit: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE") val r = revision
    var presetMenu by remember { mutableStateOf(false) }
    val presetName = FxModules.matchingPreset(slot, spec) ?: "Custom"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF17171A), ModuleFace)))
            .border(0.5.dp, SanwolfGoldDim.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Preset picker + one-line description
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                spec.description,
                fontSize = 9.sp,
                color = SanwolfTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .border(0.5.dp, SanwolfGoldDim, RoundedCornerShape(3.dp))
                        .clickable(enabled = enabled) { presetMenu = true }
                        .semantics { contentDescription = "Preset: $presetName. Tap to choose a factory preset." }
                        .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Text(presetName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (enabled) SanwolfGold else SanwolfTextMuted, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SanwolfGold, modifier = Modifier.width(14.dp))
                }
                DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                    spec.presets.forEach { pr ->
                        DropdownMenuItem(
                            text = { Text(pr.name, fontSize = 13.sp) },
                            onClick = {
                                presetMenu = false
                                FxModules.applyPreset(slot, spec, pr)
                                onChange()
                                onCommit()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Reset to default", fontSize = 13.sp, color = SanwolfTextMuted) },
                        onClick = {
                            presetMenu = false
                            for (i in spec.params.indices) FxModules.set(slot, spec, i, spec.params[i].default)
                            onChange()
                            onCommit()
                        }
                    )
                }
            }
        }

        FxVisual(slot, spec, enabled)

        // Few-option choices as segmented switches; long choice lists (tempo sync) stay knobs
        val switches = spec.params.indices.filter { spec.params[it].isChoice && (spec.params[it].choices?.size ?: 0) <= 4 }
        val knobs = spec.params.indices.filter { it !in switches }
        switches.forEach { i ->
            ChoiceSwitch(spec.params[i], FxModules.get(slot, spec, i).toInt(), enabled) { idx ->
                FxModules.set(slot, spec, i, idx.toFloat())
                onChange()
                onCommit()
            }
        }
        knobs.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { i ->
                    val prm = spec.params[i]
                    val v = FxModules.get(slot, spec, i)
                    // A free-rate / free-time knob is inactive while its sync is not "Free"
                    val syncedAway = freeRateSyncIndex(spec.type, i)?.let { FxModules.get(slot, spec, it) >= 0.5f } ?: false
                    RotaryKnob(
                        label = prm.label,
                        norm = prm.toNorm(v),
                        valueText = prm.format(v),
                        defaultNorm = prm.toNorm(prm.default),
                        bipolar = prm.bipolar,
                        steps = prm.choices?.size ?: 0,
                        enabled = enabled && !syncedAway,
                        onChange = { n -> FxModules.set(slot, spec, i, prm.fromNorm(n)); onChange() },
                        onCommit = onCommit,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keep knob widths equal in a short last row
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** For a free rate / time parameter, the index of the sync parameter that overrides it. */
private fun freeRateSyncIndex(type: FxType, index: Int): Int? = when {
    type == FxType.DELAY && index == FxIdx.Delay.TIME -> FxIdx.Delay.SYNC
    type == FxType.AUTO_FILTER && index == FxIdx.Filter.RATE -> FxIdx.Filter.SYNC
    type == FxType.TREMOLO && index == FxIdx.Tremolo.RATE -> FxIdx.Tremolo.SYNC
    else -> null
}

@Composable
private fun ChoiceSwitch(param: FxParam, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val choices = param.choices ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(param.label, fontSize = 9.sp, color = SanwolfTextMuted, modifier = Modifier.width(58.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(3.dp))
                .border(0.5.dp, SanwolfPanelBorder, RoundedCornerShape(3.dp))
        ) {
            choices.forEachIndexed { idx, label ->
                val on = idx == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 22.dp)
                        .background(if (on) (if (enabled) SanwolfGold else SanwolfTextMuted) else Color.Transparent)
                        .clickable(enabled = enabled && !on) { onSelect(idx) }
                        .semantics { contentDescription = "${param.label} $label${if (on) ", selected" else ""}" }
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (on) SanwolfBlack else SanwolfTextSecondary, maxLines = 1)
                }
            }
        }
    }
}

// =============================================================================================
// Visualisations (cheap Canvas drawings from the current parameters; meters poll at ~16 Hz)
// =============================================================================================

@Composable
private fun FxVisual(slot: FxSlot, spec: FxModuleSpec, enabled: Boolean) {
    val accent = if (enabled) SanwolfGold else SanwolfTextMuted
    when (spec.type) {
        FxType.COMPRESSOR, FxType.GATE, FxType.LIMITER -> GainReductionMeter(slot, spec, enabled)
        FxType.STEREO_WIDTH -> {}
        else -> {
            // Values read here (composition) so a parameter change redraws the canvas
            val values = FloatArray(spec.params.size) { FxModules.get(slot, spec, it) }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(VizBg)
            ) {
                // Squeezed to nothing (e.g. while a panel animates open): nothing to draw
                if (size.width < 4f || size.height < 4f) return@Canvas
                drawGrid()
                when (spec.type) {
                    FxType.EQ -> drawEqCurve(accent) { bq, sr, i ->
                        when (i) {
                            0 -> { bq.setLowShelf(sr, 120f, values[0]); true }
                            1 -> { bq.setHighShelf(sr, 6000f, values[1]); true }
                            else -> false
                        }
                    }
                    FxType.PARAM_EQ -> drawEqCurve(accent) { bq, sr, i ->
                        when (i) {
                            0 -> { bq.setLowShelf(sr, values[FxIdx.ParamEq.LOW_FREQ], values[FxIdx.ParamEq.LOW_GAIN]); true }
                            1 -> { bq.setPeak(sr, values[FxIdx.ParamEq.MID_FREQ], values[FxIdx.ParamEq.MID_GAIN], values[FxIdx.ParamEq.MID_Q]); true }
                            2 -> { bq.setHighShelf(sr, values[FxIdx.ParamEq.HIGH_FREQ], values[FxIdx.ParamEq.HIGH_GAIN]); true }
                            else -> false
                        }
                    }
                    FxType.AUTO_FILTER -> drawFilterCurve(values, accent)
                    FxType.DELAY -> drawDelayTaps(values, accent)
                    FxType.REVERB -> drawReverbDecay(values, accent)
                    FxType.CHORUS -> drawLfo(values[FxIdx.Chorus.DEPTH], 0, cycles = 2f, accent = accent)
                    FxType.FLANGER -> drawLfo(values[FxIdx.Flanger.DEPTH], 0, cycles = 2f, accent = accent)
                    FxType.PHASER -> drawLfo(values[FxIdx.Phaser.DEPTH], 0, cycles = 2f, accent = accent)
                    FxType.TREMOLO -> drawLfo(values[FxIdx.Tremolo.DEPTH], values[FxIdx.Tremolo.SHAPE].toInt(), cycles = 3f, accent = accent)
                    FxType.DISTORTION -> drawTransfer(values, accent)
                    FxType.BITCRUSHER -> drawCrush(values, accent)
                    else -> {}
                }
            }
        }
    }
}

private fun DrawScope.drawGrid() {
    val h = size.height
    for (k in 1..3) {
        val x = size.width * k / 4f
        drawLine(VizGrid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
    }
    drawLine(VizGrid, Offset(0f, h / 2f), Offset(size.width, h / 2f), strokeWidth = 1f)
}

/** Log frequency axis 20 Hz .. 20 kHz. */
private fun xToFreq(x: Float, w: Float): Float = 20f * 1000f.pow(x / w)
private fun freqToX(f: Float, w: Float): Float = (ln(f / 20f) / ln(1000f)) * w

private fun DrawScope.drawCurve(ys: FloatArray, accent: Color, fillTo: Float) {
    val n = ys.size
    val w = size.width
    val line = Path()
    val fill = Path()
    for (i in 0 until n) {
        val x = w * i / (n - 1)
        if (i == 0) { line.moveTo(x, ys[i]); fill.moveTo(x, fillTo); fill.lineTo(x, ys[i]) }
        else { line.lineTo(x, ys[i]); fill.lineTo(x, ys[i]) }
    }
    fill.lineTo(w, fillTo)
    fill.close()
    drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.02f))))
    drawPath(line, accent, style = Stroke(width = 1.5.dp.toPx()))
}

/** Sum of up to three biquads' magnitude; [setup] configures band i and returns false when there is none. */
private fun DrawScope.drawEqCurve(accent: Color, setup: (Biquad, Int, Int) -> Boolean) {
    val sr = 44100
    val bands = ArrayList<Biquad>(3)
    for (i in 0 until 3) {
        val bq = Biquad()
        if (setup(bq, sr, i)) bands.add(bq)
    }
    val n = 64
    val ys = FloatArray(n)
    val h = size.height
    val rangeDb = 18f
    for (i in 0 until n) {
        val f = xToFreq(size.width * i / (n - 1), size.width)
        var db = 0f
        for (bq in bands) db += BiquadMath.magnitudeDb(bq, f, sr)
        ys[i] = (h / 2f - db / rangeDb * (h / 2f)).coerceIn(1f, h - 1f)
    }
    drawCurve(ys, accent, h / 2f)
}

private fun DrawScope.drawFilterCurve(v: FloatArray, accent: Color) {
    val mode = v[FxIdx.Filter.MODE].toInt()
    val cutoff = v[FxIdx.Filter.CUTOFF]
    val k = 2f - 1.94f * v[FxIdx.Filter.RESONANCE]
    val depth = v[FxIdx.Filter.DEPTH]
    val w = size.width
    val h = size.height
    // LFO sweep range (+/- 3 octaves x depth)
    if (depth > 0.01f) {
        val lo = freqToX((cutoff / 2f.pow(3f * depth)).coerceAtLeast(20f), w)
        val hi = freqToX((cutoff * 2f.pow(3f * depth)).coerceAtMost(20000f), w)
        drawRect(SanwolfOrange.copy(alpha = 0.12f), topLeft = Offset(lo, 0f), size = Size((hi - lo).coerceAtLeast(1f), h))
    }
    val n = 64
    val ys = FloatArray(n)
    for (i in 0 until n) {
        val f = xToFreq(w * i / (n - 1), w)
        val om = f / cutoff
        val den = sqrt((1f - om * om) * (1f - om * om) + (k * om) * (k * om)).coerceAtLeast(1e-6f)
        val mag = when (mode) { 1 -> om * om / den; 2 -> k * om / den; else -> 1f / den }
        val db = 20f * log10(mag.coerceAtLeast(1e-4f))
        ys[i] = (h * 0.3f - db / 30f * (h * 0.7f)).coerceIn(1f, h - 1f) // 0 dB at 30% height, -30 dB at the bottom
    }
    drawCurve(ys, accent, h)
    val cx = freqToX(cutoff, w)
    drawLine(SanwolfOrange, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1f)
}

private fun DrawScope.drawDelayTaps(v: FloatArray, accent: Color) {
    val sync = v[FxIdx.Delay.SYNC].toInt().coerceIn(0, FxModules.DELAY_SYNC_BEATS.size - 1)
    val beats = FxModules.DELAY_SYNC_BEATS[sync]
    val bpm = FxMeters.songBpm.coerceAtLeast(1)
    val ms = if (beats > 0f) beats * 60000f / bpm else v[FxIdx.Delay.TIME]
    val fb = v[FxIdx.Delay.FEEDBACK]
    val ping = v[FxIdx.Delay.PING_PONG].toInt() == 1
    val windowMs = 2000f
    val w = size.width
    val h = size.height
    val barW = 3.dp.toPx()
    // Dry hit at 0
    drawRect(SanwolfTextSecondary, topLeft = Offset(0f, 0f), size = Size(barW, h))
    var amp = 0.4f + 0.5f * v[FxIdx.Delay.MIX]
    var t = ms
    var n = 0
    while (t <= windowMs && n < 32 && amp > 0.01f) {
        val x = t / windowMs * (w - barW)
        val col = if (ping && n % 2 == 1) SanwolfCyan else accent
        val bh = h * amp.coerceAtMost(1f)
        drawRect(col, topLeft = Offset(x, h - bh), size = Size(barW, bh))
        amp *= fb
        t += ms
        n++
    }
}

private fun DrawScope.drawReverbDecay(v: FloatArray, accent: Color) {
    val w = size.width
    val h = size.height
    val windowMs = 4000f
    val pre = v[FxIdx.Reverb.PRE_DELAY]
    // Freeverb comb feedback -> rough RT60: 60 dB / (dB lost per average comb loop)
    val fb = v[FxIdx.Reverb.SIZE] * 0.28f + 0.7f
    val loopMs = 1380f / 44.1f
    val rt60 = (60f / (-20f * log10(fb)) * loopMs * (1f - 0.35f * v[FxIdx.Reverb.DAMPING])).coerceIn(150f, windowMs)
    val n = 48
    val ys = FloatArray(n)
    val mix = 0.3f + 0.7f * v[FxIdx.Reverb.MIX]
    for (i in 0 until n) {
        val t = windowMs * i / (n - 1)
        // Drawn in dB (linear fall to -60 dB at RT60), as a reverb decay is usually shown
        val a = if (t < pre) 0f else (1f - (t - pre) / rt60).coerceAtLeast(0f) * mix
        ys[i] = (h - a * h * 0.95f).coerceIn(1f, h)
    }
    drawCurve(ys, accent, h)
    val px = pre / windowMs * w
    drawLine(SanwolfOrange, Offset(px, 0f), Offset(px, h), strokeWidth = 1f)
}

private fun DrawScope.drawLfo(depth: Float, shape: Int, cycles: Float, accent: Color) {
    val n = 96
    val ys = FloatArray(n)
    val h = size.height
    for (i in 0 until n) {
        val ph = (cycles * i / (n - 1)) % 1f
        val s = when (shape) {
            1 -> if (ph < 0.5f) 4f * ph - 1f else 3f - 4f * ph
            2 -> if (ph < 0.5f) 1f else -1f
            else -> sin(2f * PI.toFloat() * ph)
        }
        ys[i] = h / 2f - s * depth.coerceIn(0.05f, 1f) * (h / 2f - 2f)
    }
    drawCurve(ys, accent, h / 2f)
}

private fun DrawScope.drawTransfer(v: FloatArray, accent: Color) {
    val type = v[FxIdx.Distortion.TYPE].toInt()
    val drive = 10f.pow(v[FxIdx.Distortion.DRIVE] / 20f)
    val n = 64
    val ys = FloatArray(n)
    val h = size.height
    val fuzz0 = 1f - exp(-0.5f)
    for (i in 0 until n) {
        val x = -1f + 2f * i / (n - 1)
        val d = x * drive
        val y = when (type) {
            1 -> d.coerceIn(-1f, 1f)
            2 -> { val b = d + 0.25f; (if (b > 0f) 1f - exp(-2f * b) else -0.6f * (1f - exp(3f * b))) - fuzz0 }
            else -> if (d > 3f) 1f else if (d < -3f) -1f else d * (27f + d * d) / (27f + 9f * d * d)
        }
        ys[i] = (h / 2f - y.coerceIn(-1f, 1f) * (h / 2f - 2f))
    }
    drawCurve(ys, accent, h / 2f)
}

private fun DrawScope.drawCrush(v: FloatArray, accent: Color) {
    val levels = 2f.pow(v[FxIdx.Crusher.BITS] - 1f)
    val hold = v[FxIdx.Crusher.DOWNSAMPLE].coerceAtLeast(1f)
    val n = 128
    val ys = FloatArray(n)
    val h = size.height
    var held = 0f
    var acc = 1f
    for (i in 0 until n) {
        acc += 1f / (hold * 0.5f)
        val s = sin(2f * PI.toFloat() * 2f * i / n)
        if (acc >= 1f) { acc -= 1f; held = kotlin.math.floor(s * levels + 0.5f) / levels }
        ys[i] = h / 2f - held.coerceIn(-1f, 1f) * (h / 2f - 2f)
    }
    drawCurve(ys, accent, h / 2f)
}

/** Live gain-reduction bar (dB) for compressor / gate; limiter shows its ceiling. */
@Composable
private fun GainReductionMeter(slot: FxSlot, spec: FxModuleSpec, enabled: Boolean) {
    var gr by remember { mutableFloatStateOf(0f) }
    var held by remember { mutableFloatStateOf(0f) }
    if (spec.type != FxType.LIMITER) {
        LaunchedEffect(slot.id) {
            while (true) {
                val now = FxMeters.read(slot.id)
                gr = now
                held = if (now < held) now else held * 0.9f + now * 0.1f
                delay(60)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(if (spec.type == FxType.LIMITER) "CEIL" else "GR", fontSize = 9.sp, color = SanwolfTextMuted, fontFamily = FontFamily.Monospace, modifier = Modifier.width(30.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(VizBg)
        ) {
            val w = size.width
            if (spec.type == FxType.LIMITER) {
                val ceil = FxModules.get(slot, spec, 1) // -6..0 dB
                val x = w * (1f + ceil / 24f)
                drawRect(SanwolfGoldDim, size = Size(x, size.height))
                drawLine(SanwolfOrange, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            } else {
                val range = 24f
                val frac = (abs(gr.coerceAtLeast(-range)) / range)
                val col = if (enabled) SanwolfOrange else SanwolfTextMuted
                // Reduction grows from the right edge leftwards, like FLM's meters
                drawRect(col, topLeft = Offset(w * (1f - frac), 0f), size = Size(w * frac, size.height))
                val hx = w * (1f - abs(held.coerceAtLeast(-range)) / range)
                drawLine(SanwolfGold, Offset(hx, 0f), Offset(hx, size.height), strokeWidth = 2f)
            }
        }
        Text(
            if (spec.type == FxType.LIMITER) spec.params[1].format(FxModules.get(slot, spec, 1))
            else if (gr > -0.05f) "0.0 dB" else String.format(java.util.Locale.US, "%.1f dB", gr),
            fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = SanwolfGold,
            modifier = Modifier.padding(start = 4.dp).width(46.dp), maxLines = 1
        )
    }
}
