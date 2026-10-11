package com.sanwolf.daw.audio

import com.sanwolf.daw.model.FxSlot
import com.sanwolf.daw.model.FxType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Rack modules. Each one is the same algorithm as the matching stage of [MasterBusProcessor]
 * (shelving EQ, 2:1 glue compressor, M/S width, drive + look-ahead limiter), split out so a
 * module can be instantiated per track and on the master insert rack.
 *
 * Real-time rules, as for the master bus: [configure] once per block (dB->linear only when a
 * value changes), [process] per frame without allocating.
 */
abstract class FxProcessor(val type: FxType) {
    /** Reads the slot's parameters. Audio thread, once per block. */
    abstract fun configure(slot: FxSlot)

    /** One stereo frame in, written to [out] (size >= 2). */
    abstract fun process(inL: Float, inR: Float, out: FloatArray)

    /** Clears filter / envelope / delay state. */
    abstract fun reset()

    /** Song tempo for tempo-synced modules; set by [configureFxChain] / the exporter before [configure]. */
    @JvmField var bpm: Double = 120.0

    /**
     * Display meter for the rack UI, published once per block from [configure]: gain reduction in
     * dB (<= 0) for dynamics modules, 0 otherwise. Read by the UI through [FxMeters].
     */
    @Volatile var meter: Float = 0f

    /**
     * True while the module still holds audible signal with no input (reverb tank, delay line,
     * feedback ring). The engine keeps processing (and does not reset) until every tail is done.
     */
    open fun hasTail(): Boolean = false

    /** Bypass state seen at the previous block; un-bypassing clears stale delay/reverb state. */
    @JvmField internal var wasBypassed: Boolean = false

    companion object {
        fun create(type: FxType, sampleRate: Int = SanwolfAudioEngine.SAMPLE_RATE): FxProcessor = when (type) {
            FxType.EQ -> ShelfEqProcessor(sampleRate)
            FxType.COMPRESSOR -> GlueCompressorProcessor(sampleRate)
            FxType.STEREO_WIDTH -> StereoWidthProcessor()
            FxType.LIMITER -> LimiterProcessor(sampleRate)
            FxType.PARAM_EQ -> ParamEqProcessor(sampleRate)
            FxType.AUTO_FILTER -> AutoFilterProcessor(sampleRate)
            FxType.GATE -> GateProcessor(sampleRate)
            FxType.CHORUS -> ChorusProcessor(sampleRate)
            FxType.FLANGER -> FlangerProcessor(sampleRate)
            FxType.PHASER -> PhaserProcessor(sampleRate)
            FxType.TREMOLO -> TremoloProcessor(sampleRate)
            FxType.REVERB -> ReverbProcessor(sampleRate)
            FxType.DELAY -> DelayProcessor(sampleRate)
            FxType.DISTORTION -> DistortionProcessor(sampleRate)
            FxType.BITCRUSHER -> BitcrusherProcessor(sampleRate)
        }

        /** Null for a slot whose type this build doesn't know (it is then skipped). */
        fun createFor(slot: FxSlot, sampleRate: Int = SanwolfAudioEngine.SAMPLE_RATE): FxProcessor? =
            slot.fxType?.let { create(it, sampleRate) }
    }
}

private fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

/** Low shelf at 120 Hz and high shelf at 6 kHz (one-pole splits), +/-12 dB. */
class ShelfEqProcessor(sampleRate: Int) : FxProcessor(FxType.EQ) {
    private val lowAlpha = (1.0 - exp(-2.0 * PI * 120.0 / sampleRate)).toFloat()
    private val highAlpha = (1.0 - exp(-2.0 * PI * 6000.0 / sampleRate)).toFloat()
    private var lowDelta = 0f
    private var highDelta = 0f
    private var cachedLow = Float.NaN
    private var cachedHigh = Float.NaN
    private var lowLpL = 0f
    private var lowLpR = 0f
    private var highLpL = 0f
    private var highLpR = 0f

    override fun configure(slot: FxSlot) {
        val low = slot.lowGainDb.coerceIn(-12f, 12f)
        if (low != cachedLow) { cachedLow = low; lowDelta = dbToLinear(low) - 1f }
        val high = slot.highGainDb.coerceIn(-12f, 12f)
        if (high != cachedHigh) { cachedHigh = high; highDelta = dbToLinear(high) - 1f }
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        lowLpL += lowAlpha * (inL - lowLpL)
        lowLpR += lowAlpha * (inR - lowLpR)
        highLpL += highAlpha * (inL - highLpL)
        highLpR += highAlpha * (inR - highLpR)
        out[0] = inL + lowDelta * lowLpL + highDelta * (inL - highLpL)
        out[1] = inR + lowDelta * lowLpR + highDelta * (inR - highLpR)
    }

    override fun reset() {
        lowLpL = 0f; lowLpR = 0f; highLpL = 0f; highLpR = 0f
    }
}

/** 2:1 stereo-linked peak compressor, 10 ms attack, 150 ms release, automatic make-up. */
class GlueCompressorProcessor(sampleRate: Int) : FxProcessor(FxType.COMPRESSOR) {
    private val attack = exp(-1.0 / (0.010 * sampleRate)).toFloat()
    private val release = exp(-1.0 / (0.150 * sampleRate)).toFloat()
    private var on = false
    private var threshold = 1f
    private var makeup = 1f
    private var cachedThresh = Float.NaN
    private var env = 0f
    private var minGain = 1f

    override fun configure(slot: FxSlot) {
        meter = if (minGain < 0.9999f) (20f * log10(minGain.coerceAtLeast(1e-4f))) else 0f
        minGain = 1f
        val t = slot.thresholdDb.coerceIn(-40f, 0f)
        if (t != cachedThresh) {
            cachedThresh = t
            threshold = dbToLinear(t)
            makeup = 10.0.pow((-t * 0.25) / 20.0).toFloat()
        }
        on = t < -0.5f
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        if (!on) { out[0] = inL; out[1] = inR; return }
        val aL = abs(inL)
        val aR = abs(inR)
        val peak = if (aL > aR) aL else aR
        val coeff = if (peak > env) attack else release
        env = peak + coeff * (env - peak)
        val g = if (env > threshold) sqrt(threshold / env) else 1f
        if (g < minGain) minGain = g
        val total = g * makeup
        out[0] = inL * total
        out[1] = inR * total
    }

    override fun reset() { env = 0f; minGain = 1f }
}

/** Mid/side width: 0 = mono, 1 = unchanged, 2 = double side. */
class StereoWidthProcessor : FxProcessor(FxType.STEREO_WIDTH) {
    private var width = 1f

    override fun configure(slot: FxSlot) { width = slot.width.coerceIn(0f, 2f) }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        val mid = (inL + inR) * 0.5f
        val side = (inL - inR) * 0.5f * width
        out[0] = mid + side
        out[1] = mid - side
    }

    override fun reset() {}
}

/** Drive (0..12 dB) into the 5 ms look-ahead brick-wall limiter at the ceiling (-6..0 dB). */
class LimiterProcessor(sampleRate: Int) : FxProcessor(FxType.LIMITER) {
    private val limiter = LookAheadLimiter(sampleRate, 5.0f, 80.0f, -1f)
    private var drive = 1f
    private var cachedDrive = Float.NaN

    override fun configure(slot: FxSlot) {
        val d = slot.driveDb.coerceIn(0f, 12f)
        if (d != cachedDrive) { cachedDrive = d; drive = dbToLinear(d) }
        limiter.ceilingDb = slot.ceilingDb.coerceIn(-6f, 0f)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        limiter.process(inL * drive, inR * drive, out)
    }

    override fun reset() { limiter.reset() }
}

/** A slot paired with its processor instance. */
class FxUnit(val slot: FxSlot, val processor: FxProcessor)

/**
 * Immutable routing snapshot published to the audio thread: which tracks have a rack (by id,
 * at most [FxRackPlan.MAX_TRACK_BUSES]) and the master insert chain.
 */
class FxRackPlan(
    val trackKeys: Array<String>,
    val trackChains: Array<Array<FxUnit>>,
    val master: Array<FxUnit>
) {
    companion object {
        /** Tracks that can have their own rack processed at once (one bus buffer each). */
        const val MAX_TRACK_BUSES = 16
        val EMPTY = FxRackPlan(emptyArray(), emptyArray(), emptyArray())
    }
}

/** Runs a chain over one frame, in place in [io] (size >= 2), using [tmp] (size >= 2). */
fun runFxChain(chain: Array<FxUnit>, io: FloatArray, tmp: FloatArray) {
    for (u in chain) {
        if (u.slot.bypassed) continue
        u.processor.process(io[0], io[1], tmp)
        io[0] = tmp[0]
        io[1] = tmp[1]
    }
}

/**
 * Configures every unit of a chain (once per block) with the song tempo. A module that was
 * bypassed and is switched back on starts from clean state (no stale echo / reverb burst).
 */
fun configureFxChain(chain: Array<FxUnit>, bpm: Double = 120.0) {
    for (u in chain) {
        val p = u.processor
        if (u.slot.bypassed) {
            p.wasBypassed = true
            continue
        }
        if (p.wasBypassed) {
            p.wasBypassed = false
            p.reset()
        }
        p.bpm = bpm
        p.configure(u.slot)
    }
}

/** True while any active module of the plan still has a reverb / delay / feedback tail. */
fun fxPlanHasTail(plan: FxRackPlan): Boolean {
    for (chain in plan.trackChains) for (u in chain) if (!u.slot.bypassed && u.processor.hasTail()) return true
    for (u in plan.master) if (!u.slot.bypassed && u.processor.hasTail()) return true
    return false
}

/**
 * Live processors by slot id, so the rack UI can read meters (gain reduction) without a
 * reference to the engine. Replaced whole by the engine on every rack rebuild (UI thread).
 */
object FxMeters {
    @Volatile private var map: Map<String, FxProcessor> = emptyMap()

    /** Song tempo at the last rebuild (UI: delay tap spacing). */
    @Volatile var songBpm: Int = 120
        private set

    fun publish(processors: Map<String, FxProcessor>, bpm: Int = songBpm) {
        map = HashMap(processors)
        songBpm = bpm
    }

    /** Latest meter value for a slot (0 when the slot is not live). */
    fun read(slotId: String): Float = map[slotId]?.meter ?: 0f
}

/**
 * Per-track bus buffers for one audio block. Owned by the audio thread. [keys] are the track ids
 * whose voices are rendered into [left]/[right] instead of the main mix.
 */
class FxBusSet(capacity: Int, frames: Int) {
    val keys = arrayOfNulls<String>(capacity)
    val left = Array(capacity) { FloatArray(frames) }
    val right = Array(capacity) { FloatArray(frames) }
    var count = 0

    fun indexOf(key: String?): Int {
        if (key == null) return -1
        for (b in 0 until count) if (keys[b] == key) return b
        return -1
    }
}
