package com.sanwolf.daw.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanwolf.daw.ai.GeminiDawClient
import com.sanwolf.daw.ai.GeminiUnavailableException
import com.sanwolf.daw.ai.MasteringAssistant
import com.sanwolf.daw.ai.MasteringGenre
import com.sanwolf.daw.ai.MasteringSuggestion
import com.sanwolf.daw.ai.MasteringTarget
import com.sanwolf.daw.ai.MixMeasurement
import com.sanwolf.daw.ai.SuggestionSource
import com.sanwolf.daw.audio.LoudnessMeter
import com.sanwolf.daw.audio.SanwolfAudioEngine
import com.sanwolf.daw.audio.WavExporter
import com.sanwolf.daw.model.ProjectData
import com.sanwolf.daw.model.TrackType
import com.sanwolf.daw.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow

private const val TAG = "MasteringAssistant"

/**
 * Collects master-output readings while the song plays (UI thread, ~10 Hz). Momentary LUFS
 * samples every ~100 ms approximate BS.1770's 400 ms / 75 % overlap blocks, so the gated mean
 * is a usable integrated-loudness estimate.
 */
private class MixAccumulator {
    private val momentary = ArrayList<Float>()
    private var peakLinear = 0f
    private var rmsPower = 0.0
    private var lowPower = 0.0
    private var midPower = 0.0
    private var highPower = 0.0
    private var samples = 0
    private var maxMomentary = LoudnessMeter.SILENCE_LUFS
    private var compGrSum = 0.0
    private var limGrSum = 0.0
    var lastShortTerm: Float = LoudnessMeter.SILENCE_LUFS
        private set

    val seconds: Float get() = samples / 10f

    fun add(engine: SanwolfAudioEngine) {
        if (momentary.size >= 12_000) return // 20 minutes is plenty
        val m = engine.masterMomentaryLufs
        if (m > LoudnessMeter.SILENCE_LUFS) {
            momentary.add(m)
            if (m > maxMomentary) maxMomentary = m
        }
        val p = engine.masterBlockPeak
        if (p > peakLinear) peakLinear = p
        rmsPower += dbToPower(engine.masterRmsDb)
        lowPower += dbToPower(engine.masterLowBandDb)
        midPower += dbToPower(engine.masterMidBandDb)
        highPower += dbToPower(engine.masterHighBandDb)
        compGrSum += engine.compressorReductionDb
        limGrSum += engine.limiterReductionDb
        lastShortTerm = engine.masterShortTermLufs
        samples++
    }

    fun reset() {
        momentary.clear()
        peakLinear = 0f
        rmsPower = 0.0; lowPower = 0.0; midPower = 0.0; highPower = 0.0
        compGrSum = 0.0; limGrSum = 0.0
        samples = 0
        maxMomentary = LoudnessMeter.SILENCE_LUFS
        lastShortTerm = LoudnessMeter.SILENCE_LUFS
    }

    /** BS.1770 gating (absolute -70 LUFS, relative -10 LU) over the collected momentary blocks. */
    private fun integrated(): Float? {
        if (momentary.size < 30) return null // under ~3 s of signal
        var sum = 0.0
        for (l in momentary) sum += 10.0.pow((l + 0.691) / 10.0)
        val ungated = -0.691 + 10.0 * log10(sum / momentary.size)
        val gate = ungated - 10.0
        var gSum = 0.0
        var gCount = 0
        for (l in momentary) if (l > gate) { gSum += 10.0.pow((l + 0.691) / 10.0); gCount++ }
        if (gCount == 0) return null
        return (-0.691 + 10.0 * log10(gSum / gCount)).toFloat()
    }

    fun snapshot(): MixMeasurement {
        val n = samples.coerceAtLeast(1)
        val exportLufs = WavExporter.lastExportIntegratedLufs.takeIf { it > LoudnessMeter.SILENCE_LUFS + 0.5f }
        val hasAudio = momentary.isNotEmpty()
        return MixMeasurement(
            seconds = seconds,
            integratedLufs = integrated(),
            shortTermLufs = lastShortTerm.takeIf { it > LoudnessMeter.SILENCE_LUFS },
            maxMomentaryLufs = maxMomentary.takeIf { hasAudio },
            peakDbfs = if (peakLinear > 1e-5f) MasteringAssistant.linearToDb(peakLinear) else null,
            rmsDbfs = if (hasAudio) powerToDb(rmsPower / n) else null,
            lowBandDb = if (hasAudio) powerToDb(lowPower / n) else null,
            midBandDb = if (hasAudio) powerToDb(midPower / n) else null,
            highBandDb = if (hasAudio) powerToDb(highPower / n) else null,
            compGrDb = (compGrSum / n).toFloat(),
            limGrDb = (limGrSum / n).toFloat(),
            lastExportLufs = exportLufs
        )
    }

    private fun dbToPower(db: Float): Double = if (db <= -89f) 0.0 else 10.0.pow(db / 10.0)
    private fun powerToDb(p: Double): Float = if (p > 1e-12) (10.0 * log10(p)).toFloat() else -90f
}

/** One assistant run: the suggestion, what it replaces, and whether B is being auditioned. */
private class AssistantSession {
    var suggestion by mutableStateOf<MasteringSuggestion?>(null)
    var before: MasteringSuggestion? = null
    var beforeEnabled: Boolean = true
    var previewingB by mutableStateOf(false)
}

/**
 * Right panel › Master: "AI mastering assistant". Measures the master while the song plays,
 * asks Gemini (or the offline rules) for settings for the EXISTING chain and master inserts,
 * shows a before/after diff, lets you A/B it live, and applies it as one undo entry.
 *
 * [onApplied] must bump projectVersion (undo snapshot + engine refresh).
 * [onMasterVolumeChange] keeps the engine's stem volume in step when master volume changes.
 */
@Composable
fun MasteringAssistantCard(
    project: ProjectData,
    audioEngine: SanwolfAudioEngine,
    isVisible: Boolean,
    onApplied: () -> Unit,
    onMasterVolumeChange: (Float) -> Unit
) {
    val scope = rememberCoroutineScope { com.sanwolf.daw.CrashReporter.coroutineHandler }
    val gemini = remember { GeminiDawClient() }
    val accumulator = remember { MixAccumulator() }
    val session = remember { AssistantSession() }
    var open by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(MasteringTarget.STREAMING) }
    var customLufs by remember { mutableFloatStateOf(-12f) }
    var genre by remember { mutableStateOf(MasteringGenre.GQOM) }
    var notes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var measureTick by remember { mutableIntStateOf(0) }

    // Measure only while open, playing, and not auditioning B (readings must match the current settings)
    LaunchedEffect(isVisible, open) {
        while (isVisible && open) {
            if (audioEngine.isPlaying && !session.previewingB && !busy) {
                accumulator.add(audioEngine)
                // Redraw the status line about once a second
                if ((accumulator.seconds * 10f).toInt() % 10 == 0) measureTick++
            }
            delay(100)
        }
    }

    // Leaving the tab / collapsing the panel while auditioning B puts the original settings back
    DisposableEffect(Unit) {
        onDispose {
            if (session.previewingB) {
                session.before?.let { MasteringAssistant.restore(project, it, session.beforeEnabled) }
                session.previewingB = false
                audioEngine.masterVolume = project.masterVolume
            }
        }
    }

    fun setPreview(b: Boolean) {
        val s = session.suggestion ?: return
        val before = session.before ?: return
        if (b == session.previewingB) return
        if (b) MasteringAssistant.write(project, s) else MasteringAssistant.restore(project, before, session.beforeEnabled)
        session.previewingB = b
        onMasterVolumeChange(project.masterVolume)
    }

    fun present(s: MasteringSuggestion) {
        session.before = MasteringAssistant.capture(project)
        session.beforeEnabled = project.masteringConfig.enabled
        session.previewingB = false
        session.suggestion = s
    }

    fun runLocal(reason: String?) {
        val tl = MasteringAssistant.targetLufs(target, customLufs)
        present(MasteringAssistant.localSuggestion(project, accumulator.snapshot(), target, tl, genre))
        status = reason
    }

    fun runGemini() {
        val tl = MasteringAssistant.targetLufs(target, customLufs)
        val measurement = accumulator.snapshot()
        val prompt = MasteringAssistant.buildPrompt(
            MasteringAssistant.buildSummaryJson(project, measurement, target, tl, genre, notes)
        )
        busy = true
        status = null
        scope.launch {
            try {
                val text = gemini.requestMasteringSuggestion(prompt)
                present(MasteringAssistant.parseGeminiSuggestion(text, project, tl))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeminiUnavailableException) {
                runLocal("Gemini unavailable (${e.message}). Showing the offline suggestion.")
            } catch (e: Exception) {
                Log.w(TAG, "Could not read Gemini's mastering answer", e)
                runLocal("Couldn't read Gemini's answer. Showing the offline suggestion.")
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .border(0.5.dp, SanwolfGold.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .testTag("mastering_assistant"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .semantics { contentDescription = if (open) "Hide mastering assistant" else "Show mastering assistant" }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SanwolfGold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("MASTERING ASSISTANT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfGold, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = SanwolfTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (!open) return@Column

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val locked = busy || session.suggestion != null

            SmallLabel("Target")
            ChipGrid(MasteringTarget.values().toList(), columns = 2, selected = target, label = { it.label }, enabled = !locked) { target = it }
            if (target == MasteringTarget.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = customLufs,
                        onValueChange = { customLufs = (it * 2f).toInt() / 2f },
                        valueRange = MasteringAssistant.CUSTOM_TARGET_RANGE,
                        enabled = !locked,
                        modifier = Modifier.weight(1f).height(28.dp).semantics { contentDescription = "Custom target loudness" },
                        colors = SliderDefaults.colors(thumbColor = SanwolfGold, activeTrackColor = SanwolfGold)
                    )
                    Text("%.1f".format(customLufs), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SanwolfGold, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                }
            }

            SmallLabel("Genre")
            ChipGrid(MasteringGenre.values().toList(), columns = 3, selected = genre, label = { it.label }, enabled = !locked) { genre = it }

            SanwolfTextField(
                value = notes,
                onValueChange = { notes = it.take(300) },
                placeholder = { Text("Optional: e.g. \"more log drum, keep vocals clear\"", fontSize = 11.sp, color = SanwolfTextMuted) },
                textStyle = TextStyle(color = SanwolfTextPrimary, fontSize = 12.sp),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            )

            // Measurement status
            @Suppress("UNUSED_VARIABLE") val t = measureTick
            val m = accumulator.snapshot()
            val measuredText = when {
                m.integratedLufs != null -> "Measured %.0f s · ≈%.1f LUFS · peak %s".format(
                    m.seconds, m.integratedLufs, m.peakDbfs?.let { "%.1f dB".format(it) } ?: "--"
                )
                m.lastExportLufs != null -> "Last export: %.1f LUFS. Press play to measure live.".format(m.lastExportLufs)
                audioEngine.isPlaying -> "Listening… %.0f s (need about 3 s)".format(m.seconds)
                else -> "Press play: I measure the master while the song plays (play the loudest part)."
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(measuredText, fontSize = 10.sp, color = SanwolfTextSecondary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (m.seconds > 0f && !locked) {
                    Text(
                        "Reset",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SanwolfCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { accumulator.reset(); measureTick++ }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            if (project.tracks.any { it.type == TrackType.AUDIO_IMPORT && !it.muted }) {
                Text(
                    "Audio-clip tracks play outside the master bus live, so they aren't in this reading. Export once to include them.",
                    fontSize = 9.sp,
                    color = SanwolfTextMuted
                )
            }

            val canSuggest = m.hasLoudness && !locked
            if (session.suggestion == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (gemini.hasApiKey()) runGemini()
                            else runLocal("No Gemini key in this build. Showing the offline suggestion.")
                        },
                        enabled = canSuggest,
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp).testTag("mastering_assistant_suggest"),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SanwolfGold, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Asking Gemini…", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Suggest (AI)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = { runLocal(null) },
                        enabled = canSuggest,
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Offline rules", fontSize = 11.sp, color = SanwolfTextPrimary)
                    }
                }
            }

            status?.let { Text(it, fontSize = 10.sp, color = SanwolfOrange) }

            session.suggestion?.let { s ->
                SuggestionView(
                    project = project,
                    suggestion = s,
                    session = session,
                    onPreview = { setPreview(it) },
                    onApply = {
                        MasteringAssistant.write(project, s)
                        onMasterVolumeChange(project.masterVolume)
                        session.previewingB = false
                        session.suggestion = null
                        session.before = null
                        status = "Applied. Undo restores the previous settings."
                        // The old readings were taken with the old settings
                        accumulator.reset()
                        measureTick++
                        onApplied()
                    },
                    onDiscard = {
                        if (session.previewingB) {
                            session.before?.let { MasteringAssistant.restore(project, it, session.beforeEnabled) }
                            onMasterVolumeChange(project.masterVolume)
                        }
                        session.previewingB = false
                        session.suggestion = null
                        session.before = null
                        status = null
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestionView(
    project: ProjectData,
    suggestion: MasteringSuggestion,
    session: AssistantSession,
    onPreview: (Boolean) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit
) {
    val before = session.before ?: return
    val rows = remember(suggestion) { MasteringAssistant.diff(before, suggestion, session.beforeEnabled, project) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfSurfaceDark)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            if (suggestion.source == SuggestionSource.GEMINI) "GEMINI SUGGESTION" else "OFFLINE SUGGESTION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (suggestion.source == SuggestionSource.GEMINI) SanwolfGold else SanwolfCyan,
            letterSpacing = 0.6.sp
        )
        Text(suggestion.explanation, fontSize = 11.sp, color = SanwolfTextPrimary)
        if (rows.isEmpty()) {
            Text("No changes: the current settings already fit.", fontSize = 10.sp, color = SanwolfTextMuted)
        } else {
            rows.forEach { r ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, fontSize = 10.sp, color = SanwolfTextSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.before, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SanwolfTextMuted)
                    Text("  →  ", fontSize = 10.sp, color = SanwolfTextMuted)
                    Text(r.after, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = SanwolfLime)
                }
            }
        }
        if (suggestion.clampedFields.isNotEmpty()) {
            Text("Kept in safe range: " + suggestion.clampedFields.joinToString(", "), fontSize = 9.sp, color = SanwolfOrange)
        }

        // A/B: audition live without committing
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            AssistantChip("A · Current", selected = !session.previewingB, color = SanwolfMagenta, modifier = Modifier.weight(1f)) { onPreview(false) }
            AssistantChip("B · Suggested", selected = session.previewingB, color = SanwolfLime, modifier = Modifier.weight(1f)) { onPreview(true) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onApply,
                enabled = rows.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 34.dp).testTag("mastering_assistant_apply"),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SanwolfLime, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Apply", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Discard", fontSize = 11.sp, color = SanwolfTextPrimary) }
        }
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SanwolfTextMuted, letterSpacing = 0.6.sp)
}

@Composable
private fun <T> ChipGrid(
    items: List<T>,
    columns: Int,
    selected: T,
    label: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    AssistantChip(
                        label = label(item),
                        selected = item == selected,
                        color = SanwolfGold,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) { onSelect(item) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AssistantChip(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else SanwolfPanel)
            .border(1.dp, if (selected) color else SanwolfPanelBorder, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 4.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                selected -> color
                enabled -> SanwolfTextPrimary
                else -> SanwolfTextMuted
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
