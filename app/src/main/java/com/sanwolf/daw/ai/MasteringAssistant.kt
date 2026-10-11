package com.sanwolf.daw.ai

import com.sanwolf.daw.model.FxSlot
import com.sanwolf.daw.model.FxType
import com.sanwolf.daw.model.MasteringConfig
import com.sanwolf.daw.model.ProjectData
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Mastering assistant (right panel › Master). It never invents processors: every value it
 * returns maps onto the existing mastering chain ([MasteringConfig]), master volume, or a
 * parameter of an EXISTING master insert slot ([FxSlot]). No slots are added or removed.
 *
 * Flow: the UI measures the master output while the song plays ([MixMeasurement]), builds a
 * compact JSON summary ([buildPrompt]), asks Gemini, then [parseGeminiSuggestion] validates and
 * clamps every value. When Gemini is unavailable, [localSuggestion] gives a rule-based answer.
 */

enum class MasteringTarget(val label: String, val lufs: Float?, val ceilingDb: Float) {
    STREAMING("Streaming −14", -14f, -1.0f),
    CLUB("Club −8", -8f, -0.5f),
    PODCAST("Podcast −16", -16f, -1.0f),
    CUSTOM("Custom", null, -1.0f)
}

enum class MasteringGenre(val label: String) {
    GQOM("Gqom"),
    AMAPIANO("Amapiano"),
    AFRO_HOUSE("Afro House"),
    HIP_HOP("Hip-Hop"),
    POP("Pop"),
    OTHER("Other")
}

/**
 * What the meters saw while the assistant listened. All readings are taken AFTER the current
 * master inserts + mastering chain (post limiter), so suggestions are relative to the current
 * settings. [integratedLufs] is an approximation: BS.1770 gating applied to the 400 ms momentary
 * readings sampled every ~100 ms by the UI.
 */
data class MixMeasurement(
    val seconds: Float,
    val integratedLufs: Float?,
    val shortTermLufs: Float?,
    val maxMomentaryLufs: Float?,
    val peakDbfs: Float?,
    val rmsDbfs: Float?,
    val lowBandDb: Float?,
    val midBandDb: Float?,
    val highBandDb: Float?,
    val compGrDb: Float,
    val limGrDb: Float,
    /** Integrated loudness of the last WAV export, if any (exact, whole render). */
    val lastExportLufs: Float?
) {
    /** Best loudness figure available: live approximation first, then the last export. */
    val bestIntegratedLufs: Float? get() = integratedLufs ?: lastExportLufs
    val hasLoudness: Boolean get() = bestIntegratedLufs != null
}

/** Mastering-chain values the assistant reads and sets (a full set; unchanged fields keep the current value). */
data class ChainValues(
    val lowGainDb: Float,
    val highGainDb: Float,
    val compressorThresholdDb: Float,
    val stereoWidth: Float,
    val limiterGainDb: Float,
    val limiterCeilingDb: Float,
    val masterVolume: Float
) {
    companion object {
        fun from(project: ProjectData): ChainValues {
            val c = project.masteringConfig
            return ChainValues(
                lowGainDb = c.lowGainDb,
                highGainDb = c.highGainDb,
                compressorThresholdDb = c.compressorThresholdDb,
                stereoWidth = c.stereoWidth,
                limiterGainDb = c.limiterGainDb,
                limiterCeilingDb = c.limiterCeilingDb,
                masterVolume = project.masterVolume
            )
        }
    }
}

/** New parameter values for one existing master insert slot (by id). */
data class InsertValues(
    val slotId: String,
    val type: FxType,
    val lowGainDb: Float,
    val highGainDb: Float,
    val thresholdDb: Float,
    val width: Float,
    val driveDb: Float,
    val ceilingDb: Float
) {
    companion object {
        fun from(slot: FxSlot, type: FxType) = InsertValues(
            slotId = slot.id, type = type,
            lowGainDb = slot.lowGainDb, highGainDb = slot.highGainDb, thresholdDb = slot.thresholdDb,
            width = slot.width, driveDb = slot.driveDb, ceilingDb = slot.ceilingDb
        )
    }
}

enum class SuggestionSource { GEMINI, LOCAL }

data class MasteringSuggestion(
    val source: SuggestionSource,
    val targetLufs: Float,
    val chain: ChainValues,
    val inserts: List<InsertValues>,
    val explanation: String,
    /** Values the model sent outside the safe range (they were clamped), for the diff footer. */
    val clampedFields: List<String> = emptyList()
)

/** One row of the preview diff. */
data class SettingDiff(val label: String, val before: String, val after: String)

object MasteringAssistant {

    // Safe ranges. Chain ranges match the Master tab sliders (stricter than the DSP's own clamps).
    val LOW_SHELF_RANGE = -6f..6f
    val HIGH_SHELF_RANGE = -6f..6f
    val COMP_THRESHOLD_RANGE = -30f..0f
    val WIDTH_RANGE = 0.5f..1.6f
    val DRIVE_RANGE = 0f..12f
    val CEILING_RANGE = -3f..-0.1f
    val MASTER_VOLUME_RANGE = 0.1f..1.5f
    // Insert slot ranges (match FxProcessors clamps; width narrowed for mastering use)
    val SLOT_EQ_RANGE = -12f..12f
    val SLOT_THRESHOLD_RANGE = -40f..0f
    val SLOT_WIDTH_RANGE = 0.5f..1.6f
    val SLOT_DRIVE_RANGE = 0f..12f
    val SLOT_CEILING_RANGE = -6f..-0.1f
    val CUSTOM_TARGET_RANGE = -24f..-6f
    private const val MAX_EXPLANATION_CHARS = 420

    fun targetLufs(target: MasteringTarget, customLufs: Float): Float =
        target.lufs ?: customLufs.coerceIn(CUSTOM_TARGET_RANGE.start, CUSTOM_TARGET_RANGE.endInclusive)

    // -----------------------------------------------------------------------------------------
    // Prompt
    // -----------------------------------------------------------------------------------------

    /** Compact JSON summary of the mix and the current settings (also shown nowhere; sent as-is). */
    fun buildSummaryJson(
        project: ProjectData,
        measurement: MixMeasurement,
        target: MasteringTarget,
        targetLufs: Float,
        genre: MasteringGenre,
        userNotes: String
    ): JSONObject {
        val c = project.masteringConfig
        return JSONObject().apply {
            put("target", JSONObject().apply {
                put("name", target.name.lowercase())
                put("integratedLufs", r1(targetLufs))
                put("ceilingDb", r1(target.ceilingDb))
            })
            put("genre", genre.label)
            if (userNotes.isNotBlank()) put("notes", userNotes.trim().take(300))
            put("measured", JSONObject().apply {
                put("note", "post master inserts + mastering chain (i.e. WITH the current settings)")
                put("seconds", r1(measurement.seconds))
                putOpt("integratedLufsApprox", measurement.integratedLufs?.let { r1(it) })
                putOpt("lastExportIntegratedLufs", measurement.lastExportLufs?.let { r1(it) })
                putOpt("shortTermLufs", measurement.shortTermLufs?.let { r1(it) })
                putOpt("maxMomentaryLufs", measurement.maxMomentaryLufs?.let { r1(it) })
                putOpt("samplePeakDbfs", measurement.peakDbfs?.let { r1(it) })
                putOpt("rmsDbfs", measurement.rmsDbfs?.let { r1(it) })
                if (measurement.peakDbfs != null && measurement.rmsDbfs != null) {
                    put("crestDb", r1(measurement.peakDbfs - measurement.rmsDbfs))
                }
                if (measurement.lowBandDb != null && measurement.midBandDb != null && measurement.highBandDb != null) {
                    put("bandEnergyDbfs", JSONObject().apply {
                        put("below150Hz", r1(measurement.lowBandDb))
                        put("150Hz_5kHz", r1(measurement.midBandDb))
                        put("above5kHz", r1(measurement.highBandDb))
                    })
                }
                put("compressorGrDb", r1(measurement.compGrDb))
                put("limiterGrDb", r1(measurement.limGrDb))
            })
            put("project", JSONObject().apply {
                put("bpm", project.bpm)
                put("trackCount", project.tracks.size)
                if (project.musicalKey.isNotBlank()) put("key", project.musicalKey)
            })
            put("chain", JSONObject().apply {
                put("enabled", c.enabled)
                put("masterVolume", r2(project.masterVolume))
                put("lowShelfDb", r1(c.lowGainDb))
                put("highShelfDb", r1(c.highGainDb))
                put("compThresholdDb", r1(c.compressorThresholdDb))
                put("stereoWidth", r2(c.stereoWidth))
                put("limiterDriveDb", r1(c.limiterGainDb))
                put("limiterCeilingDb", r1(c.limiterCeilingDb))
            })
            val inserts = JSONArray()
            project.masterSlots().forEach { slot ->
                val type = slot.fxType ?: return@forEach
                inserts.put(JSONObject().apply {
                    put("id", slot.id)
                    put("type", type.key)
                    put("bypassed", slot.bypassed)
                    when (type) {
                        FxType.EQ -> { put("lowGainDb", r1(slot.lowGainDb)); put("highGainDb", r1(slot.highGainDb)) }
                        FxType.COMPRESSOR -> put("thresholdDb", r1(slot.thresholdDb))
                        FxType.STEREO_WIDTH -> put("width", r2(slot.width))
                        FxType.LIMITER -> { put("driveDb", r1(slot.driveDb)); put("ceilingDb", r1(slot.ceilingDb)) }
                        else -> {} // newer FX modules: listed (type, bypass) but not adjustable by the assistant
                    }
                })
            }
            put("masterInserts", inserts)
        }
    }

    fun buildPrompt(summary: JSONObject): String = buildString {
        appendLine("You are the mastering assistant inside SANWOLF DAW, a mobile DAW popular for Gqom, Amapiano and Afro House.")
        appendLine("Suggest settings for the EXISTING processors only. Signal flow: tracks -> master inserts (in order) -> mastering chain -> out.")
        appendLine("Mastering chain: master volume x limiter drive -> low shelf 120 Hz -> high shelf 6 kHz -> 2:1 glue compressor (10/150 ms, auto make-up; threshold >= -0.5 dB means off) -> stereo width (M/S) -> look-ahead limiter at the ceiling (sample peak, not true peak).")
        appendLine("Master insert types: eq (lowGainDb, highGainDb: shelves at 120 Hz / 6 kHz), comp (thresholdDb), width (width), limiter (driveDb, ceilingDb). You may change parameters of the listed inserts by id; you cannot add, remove, reorder or bypass them.")
        appendLine("If chain.enabled is false the chain was bypassed while measuring (only master volume and a -0.3 dB safety limiter ran); applying your answer turns the chain on, so its drive then adds to the level.")
        appendLine("The measurements were taken WITH the current settings, so loudness changes are relative: roughly +1 dB of limiter drive raises integrated loudness by up to 1 LU, less when the limiter is already reducing gain.")
        appendLine("Allowed ranges: lowShelfDb/highShelfDb -6..6, compThresholdDb -30..0, stereoWidth 0.5..1.6 (keep low end mono-safe for club music: <= 1.2), limiterDriveDb 0..12, limiterCeilingDb -3..-0.1, masterVolume 0.1..1.5. Prefer small, musical moves.")
        appendLine("Mix summary JSON:")
        appendLine(summary.toString())
        appendLine("Return ONLY this JSON object (omit a field to keep its current value):")
        appendLine("""{"chain":{"masterVolume":0.85,"lowShelfDb":1.0,"highShelfDb":0.5,"compThresholdDb":-16,"stereoWidth":1.1,"limiterDriveDb":3.0,"limiterCeilingDb":-1.0},"inserts":[{"id":"<id from masterInserts>","lowGainDb":0,"highGainDb":0,"thresholdDb":-18,"width":1.0,"driveDb":0,"ceilingDb":-1}],"explanation":"1-3 short sentences for the producer."}""")
    }

    // -----------------------------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------------------------

    /**
     * Parses the model's JSON, keeping the current value for anything missing or not a finite
     * number, and clamping everything into the safe ranges. Inserts are matched by id against the
     * project's current master slots; unknown ids are ignored. Throws on unparseable JSON.
     */
    fun parseGeminiSuggestion(text: String, project: ProjectData, targetLufs: Float): MasteringSuggestion {
        val root = JSONObject(stripCodeFence(text))
        val clamped = mutableListOf<String>()
        val current = ChainValues.from(project)
        val chainJson = root.optJSONObject("chain") ?: JSONObject()

        fun num(obj: JSONObject, key: String, currentValue: Float, range: ClosedFloatingPointRange<Float>, label: String): Float {
            if (!obj.has(key)) return currentValue.coerceIn(range.start, range.endInclusive)
            val raw = obj.optDouble(key, Double.NaN)
            if (raw.isNaN() || raw.isInfinite()) return currentValue.coerceIn(range.start, range.endInclusive)
            val v = raw.toFloat()
            val c = v.coerceIn(range.start, range.endInclusive)
            if (abs(c - v) > 1e-3f) clamped += label
            return c
        }

        val chain = ChainValues(
            lowGainDb = num(chainJson, "lowShelfDb", current.lowGainDb, LOW_SHELF_RANGE, "Low shelf"),
            highGainDb = num(chainJson, "highShelfDb", current.highGainDb, HIGH_SHELF_RANGE, "High shelf"),
            compressorThresholdDb = num(chainJson, "compThresholdDb", current.compressorThresholdDb, COMP_THRESHOLD_RANGE, "Comp threshold"),
            stereoWidth = num(chainJson, "stereoWidth", current.stereoWidth, WIDTH_RANGE, "Width"),
            limiterGainDb = num(chainJson, "limiterDriveDb", current.limiterGainDb, DRIVE_RANGE, "Limiter drive"),
            limiterCeilingDb = num(chainJson, "limiterCeilingDb", current.limiterCeilingDb, CEILING_RANGE, "Ceiling"),
            masterVolume = num(chainJson, "masterVolume", current.masterVolume, MASTER_VOLUME_RANGE, "Master volume")
        )

        val slotsById = project.masterSlots().associateBy { it.id }
        val inserts = mutableListOf<InsertValues>()
        val insertsJson = root.optJSONArray("inserts")
        if (insertsJson != null) {
            for (i in 0 until insertsJson.length()) {
                val o = insertsJson.optJSONObject(i) ?: continue
                val slot = slotsById[o.optString("id")] ?: continue
                val type = slot.fxType ?: continue
                if (inserts.any { it.slotId == slot.id }) continue
                val name = "Insert ${project.masterSlots().indexOf(slot) + 1} ${type.shortLabel}"
                inserts += InsertValues(
                    slotId = slot.id,
                    type = type,
                    lowGainDb = if (type == FxType.EQ) num(o, "lowGainDb", slot.lowGainDb, SLOT_EQ_RANGE, "$name low") else slot.lowGainDb,
                    highGainDb = if (type == FxType.EQ) num(o, "highGainDb", slot.highGainDb, SLOT_EQ_RANGE, "$name high") else slot.highGainDb,
                    thresholdDb = if (type == FxType.COMPRESSOR) num(o, "thresholdDb", slot.thresholdDb, SLOT_THRESHOLD_RANGE, "$name threshold") else slot.thresholdDb,
                    width = if (type == FxType.STEREO_WIDTH) num(o, "width", slot.width, SLOT_WIDTH_RANGE, "$name width") else slot.width,
                    driveDb = if (type == FxType.LIMITER) num(o, "driveDb", slot.driveDb, SLOT_DRIVE_RANGE, "$name drive") else slot.driveDb,
                    ceilingDb = if (type == FxType.LIMITER) num(o, "ceilingDb", slot.ceilingDb, SLOT_CEILING_RANGE, "$name ceiling") else slot.ceilingDb
                )
            }
        }

        val explanation = root.optString("explanation", "").trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_EXPLANATION_CHARS)
            .ifBlank { "Gemini suggested these settings for your target." }

        return MasteringSuggestion(
            source = SuggestionSource.GEMINI,
            targetLufs = targetLufs,
            chain = chain,
            inserts = inserts,
            explanation = explanation,
            clampedFields = clamped
        )
    }

    private fun stripCodeFence(text: String): String {
        val t = text.trim()
        if (!t.startsWith("```")) return t
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        return if (start >= 0 && end > start) t.substring(start, end + 1) else t
    }

    // -----------------------------------------------------------------------------------------
    // Local rule-based fallback
    // -----------------------------------------------------------------------------------------

    /**
     * Rule-based suggestion used when Gemini is unavailable (no key, offline, bad answer):
     *  - limiter ceiling to the target's ceiling (-1 dB streaming/podcast, -0.5 dB club);
     *  - limiter drive (then master volume, when the mix must get quieter) moved by the distance
     *    to the target loudness, at most 6 dB per pass, so it converges over a re-measure or two;
     *  - glue compressor from the crest factor; width capped for club / podcast;
     *  - small genre tilts on the shelves, nudged back when the band balance is extreme.
     */
    fun localSuggestion(
        project: ProjectData,
        measurement: MixMeasurement,
        target: MasteringTarget,
        targetLufs: Float,
        genre: MasteringGenre
    ): MasteringSuggestion {
        val cur = ChainValues.from(project)
        val notes = mutableListOf<String>()

        // Loudness. A bypassed chain was measured without its drive, and enabling it re-adds the
        // drive, so count from 0 dB in that case.
        var drive = if (project.masteringConfig.enabled) cur.limiterGainDb else 0f
        var volume = cur.masterVolume
        val measured = measurement.bestIntegratedLufs
        if (measured != null) {
            val delta = (targetLufs - measured).coerceIn(-6f, 6f)
            if (abs(delta) >= 0.5f) {
                if (delta > 0f) {
                    // Louder: drive the limiter (the chain's only make-up stage before the ceiling)
                    val newDrive = (drive + delta).coerceIn(DRIVE_RANGE.start, DRIVE_RANGE.endInclusive)
                    val rest = delta - (newDrive - drive)
                    drive = newDrive
                    if (rest > 0.25f) volume = scaleVolume(volume, rest)
                } else {
                    // Quieter: take drive back first, then master volume
                    val newDrive = (drive + delta).coerceIn(DRIVE_RANGE.start, DRIVE_RANGE.endInclusive)
                    val rest = delta - (newDrive - drive) // still negative when drive hit 0
                    drive = newDrive
                    if (rest < -0.25f) volume = scaleVolume(volume, rest)
                }
                notes += "Moved the level ${fmtSigned(delta)} dB towards ${fmt1(targetLufs)} LUFS (measured ${fmt1(measured)})"
            } else {
                notes += "Loudness is already within 0.5 LU of ${fmt1(targetLufs)} LUFS"
            }
        } else {
            notes += "No loudness reading yet, so the level was left alone"
        }
        val ceiling = target.ceilingDb.coerceIn(CEILING_RANGE.start, CEILING_RANGE.endInclusive)

        // Dynamics from the crest factor (peak - RMS)
        val crest = if (measurement.peakDbfs != null && measurement.rmsDbfs != null) measurement.peakDbfs - measurement.rmsDbfs else null
        val threshold = when {
            crest == null -> cur.compressorThresholdDb
            crest > 16f -> -20f
            crest > 12f -> -16f
            crest > 9f -> -12f
            else -> 0f // already dense: glue off, let the limiter do the rest
        }.coerceIn(COMP_THRESHOLD_RANGE.start, COMP_THRESHOLD_RANGE.endInclusive)

        // Tone: genre tilt, then a nudge from the measured band balance
        var low = when (genre) {
            MasteringGenre.GQOM -> 1.5f
            MasteringGenre.AMAPIANO -> 2.0f   // log-drum and sub weight
            MasteringGenre.AFRO_HOUSE -> 1.5f
            MasteringGenre.HIP_HOP -> 1.5f
            MasteringGenre.POP -> 0.5f
            MasteringGenre.OTHER -> 0.5f
        }
        var high = when (genre) {
            MasteringGenre.GQOM -> 0.5f
            MasteringGenre.AMAPIANO -> 1.0f   // shakers and hats
            MasteringGenre.AFRO_HOUSE -> 1.0f
            MasteringGenre.HIP_HOP -> 0.5f
            MasteringGenre.POP -> 1.5f
            MasteringGenre.OTHER -> 0.5f
        }
        if (target == MasteringTarget.PODCAST) { low = -1f; high = 1f }
        val lb = measurement.lowBandDb
        val mb = measurement.midBandDb
        val hb = measurement.highBandDb
        if (lb != null && mb != null && hb != null && mb > -80f) {
            // Readings include the current shelves; compare against loose genre-neutral norms
            if (lb - mb > 6f) { low -= 1.5f; notes += "Low end is heavy, so the low shelf was eased" }
            if (lb - mb < -6f) { low += 1f; notes += "Low end is light, so the low shelf was lifted" }
            if (hb - mb < -18f) { high += 1f; notes += "Top end is dull, so the high shelf was lifted" }
            if (hb - mb > -6f) { high -= 1.5f; notes += "Top end is bright, so the high shelf was eased" }
        }
        low = low.coerceIn(LOW_SHELF_RANGE.start, LOW_SHELF_RANGE.endInclusive)
        high = high.coerceIn(HIGH_SHELF_RANGE.start, HIGH_SHELF_RANGE.endInclusive)

        val width = when {
            target == MasteringTarget.PODCAST -> 1.0f
            target == MasteringTarget.CLUB -> minOf(cur.stereoWidth, 1.15f)
            genre == MasteringGenre.GQOM || genre == MasteringGenre.AMAPIANO || genre == MasteringGenre.AFRO_HOUSE -> minOf(cur.stereoWidth, 1.2f)
            else -> cur.stereoWidth
        }.coerceIn(WIDTH_RANGE.start, WIDTH_RANGE.endInclusive)

        val explanation = buildString {
            append("Offline suggestion: ")
            append(notes.take(2).joinToString("; "))
            append(". Ceiling ${fmt1(ceiling)} dB")
            if (crest != null) append(", glue ${if (threshold >= -0.5f) "off" else "${fmt1(threshold)} dB"} for a ${fmt1(crest)} dB crest")
            append(". Play the song again and re-run to fine-tune.")
        }.take(MAX_EXPLANATION_CHARS)

        return MasteringSuggestion(
            source = SuggestionSource.LOCAL,
            targetLufs = targetLufs,
            chain = ChainValues(
                lowGainDb = r1(low),
                highGainDb = r1(high),
                compressorThresholdDb = r1(threshold),
                stereoWidth = r2(width),
                limiterGainDb = r1(drive),
                limiterCeilingDb = r1(ceiling),
                masterVolume = r2(volume)
            ),
            inserts = emptyList(),
            explanation = explanation
        )
    }

    private fun scaleVolume(volume: Float, deltaDb: Float): Float {
        val v = volume * 10.0.pow(deltaDb / 20.0).toFloat()
        return v.coerceIn(MASTER_VOLUME_RANGE.start, MASTER_VOLUME_RANGE.endInclusive)
    }

    // -----------------------------------------------------------------------------------------
    // Apply / snapshot / diff (UI thread)
    // -----------------------------------------------------------------------------------------

    /** Captures the values the assistant can change, so A/B and Discard can restore them. */
    fun capture(project: ProjectData): MasteringSuggestion {
        val inserts = project.masterSlots().mapNotNull { slot -> slot.fxType?.let { InsertValues.from(slot, it) } }
        return MasteringSuggestion(
            source = SuggestionSource.LOCAL,
            targetLufs = project.masteringConfig.targetLufs,
            chain = ChainValues.from(project),
            inserts = inserts,
            explanation = ""
        )
    }

    /**
     * Writes [s] into the live project (the audio thread reads these fields every block, so the
     * change is audible at once). Also enables the chain and sets its reference target.
     * Callers bump projectVersion afterwards when the change should become an undo entry.
     */
    fun write(project: ProjectData, s: MasteringSuggestion, enableChain: Boolean = true) {
        val c: MasteringConfig = project.masteringConfig
        if (enableChain) c.enabled = true
        c.targetLufs = s.targetLufs
        c.lowGainDb = s.chain.lowGainDb
        c.highGainDb = s.chain.highGainDb
        c.compressorThresholdDb = s.chain.compressorThresholdDb
        c.stereoWidth = s.chain.stereoWidth
        c.limiterGainDb = s.chain.limiterGainDb
        c.limiterCeilingDb = s.chain.limiterCeilingDb
        project.masterVolume = s.chain.masterVolume
        val slots = project.masterSlots()
        s.inserts.forEach { v ->
            val slot = slots.firstOrNull { it.id == v.slotId } ?: return@forEach
            slot.lowGainDb = v.lowGainDb
            slot.highGainDb = v.highGainDb
            slot.thresholdDb = v.thresholdDb
            slot.width = v.width
            slot.driveDb = v.driveDb
            slot.ceilingDb = v.ceilingDb
        }
    }

    /** Restores a [capture]d state, including whether the chain was enabled. */
    fun restore(project: ProjectData, snapshot: MasteringSuggestion, wasEnabled: Boolean) {
        write(project, snapshot, enableChain = false)
        project.masteringConfig.enabled = wasEnabled
    }

    fun diff(before: MasteringSuggestion, after: MasteringSuggestion, beforeEnabled: Boolean, project: ProjectData): List<SettingDiff> {
        val rows = mutableListOf<SettingDiff>()
        fun db(label: String, a: Float, b: Float) {
            if (abs(a - b) >= 0.05f) rows.add(SettingDiff(label, dbStr(a), dbStr(b)))
        }
        if (!beforeEnabled) rows += SettingDiff("Mastering chain", "Bypass", "On")
        if (abs(before.targetLufs - after.targetLufs) >= 0.05f) rows += SettingDiff("Target", "${fmt1(before.targetLufs)} LUFS", "${fmt1(after.targetLufs)} LUFS")
        val a = before.chain
        val b = after.chain
        if (abs(a.masterVolume - b.masterVolume) >= 0.005f) {
            rows += SettingDiff("Master volume", "${(a.masterVolume * 100).roundToInt()}%", "${(b.masterVolume * 100).roundToInt()}%")
        }
        db("Low shelf", a.lowGainDb, b.lowGainDb)
        db("High shelf", a.highGainDb, b.highGainDb)
        if (abs(a.compressorThresholdDb - b.compressorThresholdDb) >= 0.05f) {
            rows += SettingDiff("Glue threshold", threshStr(a.compressorThresholdDb), threshStr(b.compressorThresholdDb))
        }
        if (abs(a.stereoWidth - b.stereoWidth) >= 0.005f) {
            rows += SettingDiff("Width", "${(a.stereoWidth * 100).roundToInt()}%", "${(b.stereoWidth * 100).roundToInt()}%")
        }
        db("Limiter drive", a.limiterGainDb, b.limiterGainDb)
        db("Ceiling", a.limiterCeilingDb, b.limiterCeilingDb)
        val slots = project.masterSlots()
        after.inserts.forEach { v ->
            val old = before.inserts.firstOrNull { it.slotId == v.slotId } ?: return@forEach
            val name = "Insert ${slots.indexOfFirst { it.id == v.slotId } + 1} ${v.type.shortLabel}"
            when (v.type) {
                FxType.EQ -> {
                    db("$name low", old.lowGainDb, v.lowGainDb)
                    db("$name high", old.highGainDb, v.highGainDb)
                }
                FxType.COMPRESSOR -> {
                    if (abs(old.thresholdDb - v.thresholdDb) >= 0.05f) {
                        rows.add(SettingDiff("$name threshold", threshStr(old.thresholdDb), threshStr(v.thresholdDb)))
                    }
                }
                FxType.STEREO_WIDTH -> {
                    if (abs(old.width - v.width) >= 0.005f) {
                        rows.add(SettingDiff("$name width", "${(old.width * 100).roundToInt()}%", "${(v.width * 100).roundToInt()}%"))
                    }
                }
                FxType.LIMITER -> {
                    db("$name drive", old.driveDb, v.driveDb)
                    db("$name ceiling", old.ceilingDb, v.ceilingDb)
                }
                else -> {}
            }
            Unit
        }
        return rows
    }

    // -----------------------------------------------------------------------------------------
    // Formatting helpers
    // -----------------------------------------------------------------------------------------

    fun linearToDb(linear: Float): Float = if (linear > 1e-6f) 20f * log10(linear) else -120f

    private fun r1(v: Float): Float = (v * 10f).roundToInt() / 10f
    private fun r2(v: Float): Float = (v * 100f).roundToInt() / 100f
    private fun fmt1(v: Float): String = "%.1f".format(v)
    private fun fmtSigned(v: Float): String = (if (v > 0f) "+" else "") + "%.1f".format(v)
    private fun dbStr(v: Float): String = fmtSigned(v) + " dB"
    private fun threshStr(v: Float): String = if (v >= -0.5f) "Off" else "%.1f dB".format(v)
}
