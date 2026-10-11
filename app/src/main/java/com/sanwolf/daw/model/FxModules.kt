package com.sanwolf.daw.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * FX module registry: category, parameters (range, default, display) and factory presets for
 * every [FxType]. Pure Kotlin, shared by the DSP (audio/FxModulesDsp.kt), the rack UI and tests.
 *
 * Storage: the original four types (EQ, Compressor, Stereo width, Limiter) keep their named
 * [FxSlot] fields (the mastering assistant and Firestore read them); every newer type stores its
 * values in [FxSlot.params] by the index of its parameter here (see [FxIdx]). Append new
 * parameters at the END of a module's list only, so saved projects keep their meaning.
 */
enum class FxCategory(val label: String) {
    DYNAMICS("Dynamics"),
    EQ_FILTER("EQ & Filter"),
    MODULATION("Modulation"),
    SPACE("Space"),
    DISTORTION("Distortion"),
    UTILITY("Utility")
}

/** How a parameter is displayed. */
enum class FxParamKind { DB, HZ, RATE_HZ, MS, PERCENT, Q, BITS, FACTOR, CHOICE }

class FxParam(
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val kind: FxParamKind,
    /** Knob travel is logarithmic (frequencies, times). Requires min > 0. */
    val log: Boolean = false,
    /** Discrete choices (kind = CHOICE); the stored value is the index. */
    val choices: List<String>? = null,
    /** Legacy types: read / write a named [FxSlot] field instead of [FxSlot.params]. */
    val getter: ((FxSlot) -> Float)? = null,
    val setter: ((FxSlot, Float) -> Unit)? = null
) {
    val isChoice: Boolean get() = choices != null
    /** Arc drawn from the centre (e.g. EQ gain). */
    val bipolar: Boolean get() = !isChoice && min < 0f && max > 0f

    fun clamp(v: Float): Float {
        if (v.isNaN()) return default
        val c = v.coerceIn(min, max)
        return if (isChoice) c.roundToInt().toFloat() else c
    }

    /** Value -> knob position 0..1. */
    fun toNorm(v: Float): Float {
        val c = clamp(v)
        if (max <= min) return 0f
        return if (log && min > 0f) (ln(c / min) / ln(max / min)).coerceIn(0f, 1f)
        else ((c - min) / (max - min)).coerceIn(0f, 1f)
    }

    /** Knob position 0..1 -> value. */
    fun fromNorm(n: Float): Float {
        val t = n.coerceIn(0f, 1f)
        val v = if (log && min > 0f) min * exp(t * ln(max / min)) else min + t * (max - min)
        return clamp(v)
    }

    fun format(v: Float): String {
        val c = clamp(v)
        return when (kind) {
            FxParamKind.CHOICE -> choices?.getOrNull(c.roundToInt()) ?: c.roundToInt().toString()
            FxParamKind.DB -> {
                if (c <= -79.5f && min <= -80f) "-inf dB"
                else (if (c > 0.05f && min < 0f) "+" else "") + fmt("%.1f dB", c)
            }
            FxParamKind.HZ -> if (c >= 1000f) fmt("%.1f kHz", c / 1000f) else "${c.roundToInt()} Hz"
            FxParamKind.RATE_HZ -> if (c < 1f) fmt("%.2f Hz", c) else fmt("%.1f Hz", c)
            FxParamKind.MS -> if (c < 10f) fmt("%.1f ms", c) else if (c >= 1000f) fmt("%.2f s", c / 1000f) else "${c.roundToInt()} ms"
            FxParamKind.PERCENT -> "${(c * 100f).roundToInt()}%"
            FxParamKind.Q -> fmt("Q %.2f", c)
            FxParamKind.BITS -> fmt("%.1f bit", c)
            FxParamKind.FACTOR -> "÷${c.roundToInt()}"
        }
    }

    private fun fmt(pattern: String, v: Float): String = String.format(Locale.US, pattern, v)
}

class FxPreset(val name: String, val values: FloatArray)

class FxModuleSpec(
    val type: FxType,
    val category: FxCategory,
    val description: String,
    val params: List<FxParam>,
    val presets: List<FxPreset>
) {
    /** Uses the named [FxSlot] fields (EQ, Compressor, Width, Limiter). */
    val legacy: Boolean get() = params.isNotEmpty() && params[0].getter != null
    val defaults: FloatArray = FloatArray(params.size) { params[it].default }
}

/** Parameter indices into [FxSlot.params] for the DSP. Must match the lists in [FxModules]. */
object FxIdx {
    object ParamEq { const val LOW_FREQ = 0; const val LOW_GAIN = 1; const val MID_FREQ = 2; const val MID_GAIN = 3; const val MID_Q = 4; const val HIGH_FREQ = 5; const val HIGH_GAIN = 6; const val OUTPUT = 7 }
    object Filter { const val MODE = 0; const val CUTOFF = 1; const val RESONANCE = 2; const val SYNC = 3; const val RATE = 4; const val DEPTH = 5; const val MIX = 6 }
    object Gate { const val THRESHOLD = 0; const val ATTACK = 1; const val HOLD = 2; const val RELEASE = 3; const val RANGE = 4 }
    object Chorus { const val RATE = 0; const val DEPTH = 1; const val DELAY = 2; const val SPREAD = 3; const val MIX = 4 }
    object Flanger { const val RATE = 0; const val DEPTH = 1; const val FEEDBACK = 2; const val DELAY = 3; const val MIX = 4 }
    object Phaser { const val RATE = 0; const val DEPTH = 1; const val FEEDBACK = 2; const val STAGES = 3; const val MIX = 4 }
    object Tremolo { const val MODE = 0; const val SHAPE = 1; const val SYNC = 2; const val RATE = 3; const val DEPTH = 4 }
    object Reverb { const val SIZE = 0; const val DAMPING = 1; const val WIDTH = 2; const val PRE_DELAY = 3; const val LOW_CUT = 4; const val MIX = 5 }
    object Delay { const val SYNC = 0; const val TIME = 1; const val FEEDBACK = 2; const val PING_PONG = 3; const val HIGH_CUT = 4; const val MIX = 5 }
    object Distortion { const val TYPE = 0; const val DRIVE = 1; const val TONE = 2; const val OUTPUT = 3; const val MIX = 4 }
    object Crusher { const val BITS = 0; const val DOWNSAMPLE = 1; const val MIX = 2; const val OUTPUT = 3 }
}

object FxModules {
    // ---- Tempo-sync tables (value stored = index; beats per cycle / per repeat) ----
    val DELAY_SYNC_LABELS = listOf("Free", "1/4", "1/8", "1/16", "1/4 D", "1/8 D", "1/4 T", "1/8 T")
    val DELAY_SYNC_BEATS = floatArrayOf(0f, 1f, 0.5f, 0.25f, 1.5f, 0.75f, 2f / 3f, 1f / 3f)
    val FILTER_SYNC_LABELS = listOf("Free", "2 bars", "1 bar", "1/2", "1/4", "1/8", "1/16")
    val FILTER_SYNC_BEATS = floatArrayOf(0f, 8f, 4f, 2f, 1f, 0.5f, 0.25f)
    val TREMOLO_SYNC_LABELS = listOf("Free", "1/2", "1/4", "1/8", "1/16", "1/4 T", "1/8 T", "1/16 T")
    val TREMOLO_SYNC_BEATS = floatArrayOf(0f, 2f, 1f, 0.5f, 0.25f, 2f / 3f, 1f / 3f, 1f / 6f)
    val PHASER_STAGES = intArrayOf(4, 6, 8, 12)

    private fun p(label: String, min: Float, max: Float, def: Float, kind: FxParamKind, log: Boolean = false) =
        FxParam(label, min, max, def, kind, log)

    private fun choice(label: String, choices: List<String>, def: Int) =
        FxParam(label, 0f, (choices.size - 1).toFloat(), def.toFloat(), FxParamKind.CHOICE, choices = choices)

    private fun preset(name: String, vararg v: Float) = FxPreset(name, v)

    private val specs: List<FxModuleSpec> = listOf(
        // ---------------- Dynamics ----------------
        FxModuleSpec(
            FxType.COMPRESSOR, FxCategory.DYNAMICS, "2:1 glue, 10 ms attack, 150 ms release, auto make-up.",
            listOf(FxParam("Threshold", -40f, 0f, -18f, FxParamKind.DB, getter = { it.thresholdDb }, setter = { s, v -> s.thresholdDb = v })),
            listOf(preset("Gentle glue", -12f), preset("Punch", -20f), preset("Squash", -32f))
        ),
        FxModuleSpec(
            FxType.LIMITER, FxCategory.DYNAMICS, "5 ms look-ahead brick-wall (adds 5 ms latency).",
            listOf(
                FxParam("Drive", 0f, 12f, 0f, FxParamKind.DB, getter = { it.driveDb }, setter = { s, v -> s.driveDb = v }),
                FxParam("Ceiling", -6f, 0f, -1f, FxParamKind.DB, getter = { it.ceilingDb }, setter = { s, v -> s.ceilingDb = v })
            ),
            listOf(preset("Safe", 0f, -1f), preset("Loud", 6f, -0.3f), preset("Club", 9f, -0.5f))
        ),
        FxModuleSpec(
            FxType.GATE, FxCategory.DYNAMICS, "Stereo-linked noise gate with hold and range.",
            listOf(
                p("Threshold", -80f, 0f, -40f, FxParamKind.DB),
                p("Attack", 0.1f, 50f, 1f, FxParamKind.MS, log = true),
                p("Hold", 0f, 500f, 40f, FxParamKind.MS),
                p("Release", 5f, 1000f, 120f, FxParamKind.MS, log = true),
                p("Range", -80f, 0f, -80f, FxParamKind.DB)
            ),
            listOf(
                preset("Tight drums", -30f, 0.5f, 20f, 60f, -80f),
                preset("Noise cleanup", -50f, 2f, 80f, 200f, -30f),
                preset("Hard chop", -20f, 0.1f, 0f, 10f, -80f)
            )
        ),
        // ---------------- EQ & Filter ----------------
        FxModuleSpec(
            FxType.EQ, FxCategory.EQ_FILTER, "Low shelf 120 Hz and high shelf 6 kHz.",
            listOf(
                FxParam("Low 120", -12f, 12f, 0f, FxParamKind.DB, getter = { it.lowGainDb }, setter = { s, v -> s.lowGainDb = v }),
                FxParam("High 6k", -12f, 12f, 0f, FxParamKind.DB, getter = { it.highGainDb }, setter = { s, v -> s.highGainDb = v })
            ),
            listOf(preset("Flat", 0f, 0f), preset("Warmth", 3f, -1f), preset("Air", 0f, 3f), preset("Smile", 3f, 2.5f))
        ),
        FxModuleSpec(
            FxType.PARAM_EQ, FxCategory.EQ_FILTER, "Low shelf, peaking mid and high shelf, with output trim.",
            listOf(
                p("Low freq", 20f, 500f, 100f, FxParamKind.HZ, log = true),
                p("Low gain", -15f, 15f, 0f, FxParamKind.DB),
                p("Mid freq", 100f, 10000f, 1000f, FxParamKind.HZ, log = true),
                p("Mid gain", -15f, 15f, 0f, FxParamKind.DB),
                p("Mid Q", 0.3f, 8f, 1f, FxParamKind.Q, log = true),
                p("High freq", 1500f, 16000f, 8000f, FxParamKind.HZ, log = true),
                p("High gain", -15f, 15f, 0f, FxParamKind.DB),
                p("Output", -12f, 12f, 0f, FxParamKind.DB)
            ),
            listOf(
                preset("Flat", 100f, 0f, 1000f, 0f, 1f, 8000f, 0f, 0f),
                preset("Vocal presence", 120f, -2f, 3000f, 3f, 1.2f, 10000f, 2f, 0f),
                preset("Kick punch", 60f, 4f, 400f, -4f, 1.5f, 5000f, 2f, -1f),
                preset("Mud cut", 100f, 0f, 300f, -4f, 1f, 8000f, 1.5f, 0f)
            )
        ),
        FxModuleSpec(
            FxType.AUTO_FILTER, FxCategory.EQ_FILTER, "Resonant state-variable filter with a tempo-synced LFO.",
            listOf(
                choice("Mode", listOf("LP", "HP", "BP"), 0),
                p("Cutoff", 20f, 20000f, 2000f, FxParamKind.HZ, log = true),
                p("Resonance", 0f, 1f, 0.3f, FxParamKind.PERCENT),
                choice("LFO sync", FILTER_SYNC_LABELS, 2),
                p("LFO rate", 0.05f, 10f, 1f, FxParamKind.RATE_HZ, log = true),
                p("LFO depth", 0f, 1f, 0f, FxParamKind.PERCENT),
                p("Mix", 0f, 1f, 1f, FxParamKind.PERCENT)
            ),
            listOf(
                preset("Low-pass sweep", 0f, 800f, 0.5f, 2f, 1f, 0.6f, 1f),
                preset("Hi-pass thin", 1f, 400f, 0.2f, 2f, 1f, 0f, 1f),
                preset("Wah 1/8", 2f, 1000f, 0.7f, 5f, 1f, 0.7f, 1f),
                preset("Dub low-pass", 0f, 600f, 0.65f, 1f, 1f, 0.4f, 1f)
            )
        ),
        // ---------------- Modulation ----------------
        FxModuleSpec(
            FxType.CHORUS, FxCategory.MODULATION, "Two modulated delay lines, stereo spread.",
            listOf(
                p("Rate", 0.05f, 5f, 0.8f, FxParamKind.RATE_HZ, log = true),
                p("Depth", 0f, 1f, 0.5f, FxParamKind.PERCENT),
                p("Delay", 5f, 25f, 14f, FxParamKind.MS),
                p("Spread", 0f, 1f, 0.7f, FxParamKind.PERCENT),
                p("Mix", 0f, 1f, 0.5f, FxParamKind.PERCENT)
            ),
            listOf(
                preset("Subtle", 0.4f, 0.3f, 12f, 0.8f, 0.3f),
                preset("Wide", 0.8f, 0.6f, 16f, 1f, 0.5f),
                preset("Vibrato", 5f, 0.4f, 8f, 0f, 1f)
            )
        ),
        FxModuleSpec(
            FxType.FLANGER, FxCategory.MODULATION, "Short swept delay with +/- feedback.",
            listOf(
                p("Rate", 0.02f, 5f, 0.25f, FxParamKind.RATE_HZ, log = true),
                p("Depth", 0f, 1f, 0.7f, FxParamKind.PERCENT),
                p("Feedback", -0.95f, 0.95f, 0.6f, FxParamKind.PERCENT),
                p("Delay", 0.1f, 8f, 1f, FxParamKind.MS, log = true),
                p("Mix", 0f, 1f, 0.5f, FxParamKind.PERCENT)
            ),
            listOf(
                preset("Jet", 0.1f, 0.9f, 0.8f, 1f, 0.5f),
                preset("Classic", 0.3f, 0.6f, 0.5f, 1.5f, 0.5f),
                preset("Metallic", 1.5f, 0.3f, -0.85f, 0.5f, 0.5f)
            )
        ),
        FxModuleSpec(
            FxType.PHASER, FxCategory.MODULATION, "Cascaded all-pass stages swept by an LFO.",
            listOf(
                p("Rate", 0.02f, 5f, 0.4f, FxParamKind.RATE_HZ, log = true),
                p("Depth", 0f, 1f, 0.7f, FxParamKind.PERCENT),
                p("Feedback", 0f, 0.9f, 0.5f, FxParamKind.PERCENT),
                choice("Stages", PHASER_STAGES.map { it.toString() }, 1),
                p("Mix", 0f, 1f, 0.5f, FxParamKind.PERCENT)
            ),
            listOf(
                preset("Slow swirl", 0.15f, 0.8f, 0.6f, 3f, 0.5f),
                preset("Funk 4-stage", 0.8f, 0.6f, 0.3f, 0f, 0.5f),
                preset("Deep", 0.3f, 1f, 0.8f, 2f, 0.6f)
            )
        ),
        FxModuleSpec(
            FxType.TREMOLO, FxCategory.MODULATION, "Volume or pan LFO, tempo-synced or free.",
            listOf(
                choice("Mode", listOf("Trem", "Pan"), 0),
                choice("Shape", listOf("Sine", "Tri", "Square"), 0),
                choice("Sync", TREMOLO_SYNC_LABELS, 3),
                p("Rate", 0.1f, 20f, 4f, FxParamKind.RATE_HZ, log = true),
                p("Depth", 0f, 1f, 0.6f, FxParamKind.PERCENT)
            ),
            listOf(
                preset("Gentle 1/8", 0f, 0f, 3f, 4f, 0.4f),
                preset("Auto-pan 1/4", 1f, 0f, 2f, 4f, 0.8f),
                preset("Chop 1/16", 0f, 2f, 4f, 4f, 1f),
                preset("Slow pan", 1f, 1f, 0f, 0.25f, 0.7f)
            )
        ),
        // ---------------- Space ----------------
        FxModuleSpec(
            FxType.REVERB, FxCategory.SPACE, "Freeverb-style: 8 combs + 4 all-passes per side.",
            listOf(
                p("Size", 0f, 1f, 0.6f, FxParamKind.PERCENT),
                p("Damping", 0f, 1f, 0.5f, FxParamKind.PERCENT),
                p("Width", 0f, 1f, 1f, FxParamKind.PERCENT),
                p("Pre-delay", 0f, 200f, 10f, FxParamKind.MS),
                p("Low cut", 20f, 1000f, 100f, FxParamKind.HZ, log = true),
                p("Mix", 0f, 1f, 0.25f, FxParamKind.PERCENT)
            ),
            listOf(
                preset("Room", 0.45f, 0.6f, 0.8f, 5f, 150f, 0.18f),
                preset("Hall", 0.85f, 0.35f, 1f, 25f, 120f, 0.3f),
                preset("Plate", 0.7f, 0.1f, 1f, 0f, 200f, 0.25f),
                preset("Vocal", 0.65f, 0.45f, 0.9f, 40f, 250f, 0.22f)
            )
        ),
        FxModuleSpec(
            FxType.DELAY, FxCategory.SPACE, "Tempo-synced or free delay, ping-pong, filtered repeats.",
            listOf(
                choice("Sync", DELAY_SYNC_LABELS, 2),
                p("Time", 1f, 2000f, 375f, FxParamKind.MS, log = true),
                p("Feedback", 0f, 0.95f, 0.35f, FxParamKind.PERCENT),
                choice("Ping-pong", listOf("Off", "On"), 0),
                p("High cut", 500f, 20000f, 8000f, FxParamKind.HZ, log = true),
                p("Mix", 0f, 1f, 0.25f, FxParamKind.PERCENT)
            ),
            listOf(
                preset("Slapback", 0f, 90f, 0.1f, 0f, 6000f, 0.25f),
                preset("1/8 Ping-pong", 2f, 375f, 0.45f, 1f, 7000f, 0.3f),
                preset("Dotted 1/8", 5f, 375f, 0.4f, 0f, 6000f, 0.25f),
                preset("Dub 1/4", 1f, 500f, 0.65f, 1f, 2500f, 0.3f)
            )
        ),
        // ---------------- Distortion ----------------
        FxModuleSpec(
            FxType.DISTORTION, FxCategory.DISTORTION, "Soft / hard clip or fuzz, tone and parallel mix.",
            listOf(
                choice("Type", listOf("Soft", "Hard", "Fuzz"), 0),
                p("Drive", 0f, 36f, 12f, FxParamKind.DB),
                p("Tone", 0f, 1f, 0.5f, FxParamKind.PERCENT),
                p("Output", -24f, 6f, -6f, FxParamKind.DB),
                p("Mix", 0f, 1f, 1f, FxParamKind.PERCENT)
            ),
            listOf(
                preset("Warm overdrive", 0f, 9f, 0.45f, -4f, 1f),
                preset("Crunch", 1f, 18f, 0.55f, -10f, 1f),
                preset("Fuzz", 2f, 28f, 0.4f, -14f, 1f),
                preset("Parallel grit", 0f, 24f, 0.6f, -12f, 0.35f)
            )
        ),
        FxModuleSpec(
            FxType.BITCRUSHER, FxCategory.DISTORTION, "Bit-depth and sample-rate reduction.",
            listOf(
                p("Bits", 1f, 16f, 8f, FxParamKind.BITS),
                p("Downsample", 1f, 32f, 4f, FxParamKind.FACTOR, log = true),
                p("Mix", 0f, 1f, 1f, FxParamKind.PERCENT),
                p("Output", -24f, 6f, 0f, FxParamKind.DB)
            ),
            listOf(
                preset("Lo-fi 12-bit", 12f, 2f, 1f, 0f),
                preset("8-bit game", 8f, 4f, 1f, -2f),
                preset("Destroyed", 4f, 16f, 0.7f, -6f)
            )
        ),
        // ---------------- Utility ----------------
        FxModuleSpec(
            FxType.STEREO_WIDTH, FxCategory.UTILITY, "Mid/side: 0% mono, 100% unchanged, 200% extra wide.",
            listOf(FxParam("Width", 0f, 2f, 1f, FxParamKind.PERCENT, getter = { it.width }, setter = { s, v -> s.width = v })),
            listOf(preset("Mono", 0f), preset("Wide", 1.4f), preset("Extra wide", 1.8f))
        )
    )

    private val byOrdinal: Array<FxModuleSpec?> = arrayOfNulls<FxModuleSpec>(FxType.values().size).also { arr ->
        for (s in specs) arr[s.type.ordinal] = s
    }

    /** Every module type has a spec (checked by FxModulesTest). */
    fun spec(type: FxType): FxModuleSpec = byOrdinal[type.ordinal]
        ?: error("No FX module spec for $type")

    val all: List<FxModuleSpec> get() = specs

    /** Modules grouped for the "Add module" menu, in [FxCategory] order. */
    fun byCategory(): List<Pair<FxCategory, List<FxModuleSpec>>> =
        FxCategory.values().map { c -> c to specs.filter { it.category == c } }.filter { it.second.isNotEmpty() }

    /** A new slot of [type] at its default values (the first factory preset for the old four). */
    fun newSlot(type: FxType): FxSlot {
        val s = spec(type)
        val slot = FxSlot(type = type.key)
        if (!s.legacy) slot.params = s.defaults.copyOf()
        else for (i in s.params.indices) s.params[i].setter?.invoke(slot, s.params[i].default)
        return slot
    }

    /** Current value of parameter [index] (clamped, default when missing). */
    fun get(slot: FxSlot, spec: FxModuleSpec, index: Int): Float {
        val prm = spec.params[index]
        val g = prm.getter
        if (g != null) return prm.clamp(g(slot))
        val arr = slot.params
        return if (arr != null && index < arr.size) prm.clamp(arr[index]) else prm.default
    }

    /** Writes parameter [index]; grows / creates [FxSlot.params] (UI thread) when needed. */
    fun set(slot: FxSlot, spec: FxModuleSpec, index: Int, value: Float) {
        val prm = spec.params[index]
        val v = prm.clamp(value)
        val st = prm.setter
        if (st != null) { st(slot, v); return }
        val arr = slot.params
        if (arr != null && arr.size == spec.params.size) {
            arr[index] = v
        } else {
            val fresh = spec.defaults.copyOf()
            if (arr != null) for (i in 0 until minOf(arr.size, fresh.size)) fresh[i] = spec.params[i].clamp(arr[i])
            fresh[index] = v
            slot.params = fresh // one reference write: the audio thread sees old or new, never half
        }
    }

    fun applyPreset(slot: FxSlot, spec: FxModuleSpec, preset: FxPreset) {
        if (spec.legacy) {
            for (i in spec.params.indices) set(slot, spec, i, preset.values.getOrElse(i) { spec.params[i].default })
        } else {
            slot.params = FloatArray(spec.params.size) { i -> spec.params[i].clamp(preset.values.getOrElse(i) { spec.params[i].default }) }
        }
    }

    /** Name of the factory preset the slot currently matches, or null ("Custom"). */
    fun matchingPreset(slot: FxSlot, spec: FxModuleSpec): String? = spec.presets.firstOrNull { pr ->
        spec.params.indices.all { i -> abs(get(slot, spec, i) - spec.params[i].clamp(pr.values.getOrElse(i) { spec.params[i].default })) < 1e-3f }
    }?.name
}
