package com.sanwolf.daw.audio

import com.sanwolf.daw.model.FxIdx
import com.sanwolf.daw.model.FxModuleSpec
import com.sanwolf.daw.model.FxModules
import com.sanwolf.daw.model.FxSlot
import com.sanwolf.daw.model.FxType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * FX modules (branch feature/fx-modules): Parametric EQ, Auto filter, Gate, Chorus, Flanger,
 * Phaser, Tremolo / Auto-pan, Reverb, Delay, Distortion, Bitcrusher.
 *
 * Real-time rules (same as the original four modules):
 *  - every buffer is allocated in the constructor; [process] never allocates or locks;
 *  - parameters are read once per block in [configure] and ramped per sample by [Smoother]s
 *    (snapped on the very first configure, so an offline export starts at the set values);
 *  - recursive state (filters, feedback lines, reverb tank) is flushed to 0 below 1e-15 so it
 *    never decays into denormals;
 *  - tail modules report [hasTail] so the engine keeps rendering echoes / reverb after the
 *    voices stop, instead of treating the block as silent and resetting them.
 */

private const val TWO_PI_F = (2.0 * PI).toFloat()

/** Flush values that would decay into the denormal range. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun flushDenorm(x: Float): Float = if (x < 1e-15f && x > -1e-15f) 0f else x

internal fun dbToLin(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

/** One-pole parameter smoother (about [timeMs] to settle). Allocation-free. */
class Smoother(sampleRate: Int, timeMs: Float = 20f) {
    private val coef = (1.0 - exp(-1.0 / (timeMs * 0.001 * sampleRate / 3.0))).toFloat()
    var value = 0f
        private set
    var target = 0f
        private set

    fun set(t: Float, snap: Boolean) {
        target = t
        if (snap) value = t
    }

    fun next(): Float {
        val d = target - value
        if (d > 1e-6f || d < -1e-6f) value += d * coef else value = target
        return value
    }

    val settled: Boolean get() = value == target
}

/** Equal-loudness-ish dry/wet law for send-type effects: both full at 50%. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun dryGainFor(mix: Float): Float = if (mix <= 0.5f) 1f else 2f * (1f - mix)
@Suppress("NOTHING_TO_INLINE")
internal inline fun wetGainFor(mix: Float): Float = if (mix >= 0.5f) 1f else 2f * mix

/**
 * Base for the spec-driven modules: parameters come from [FxSlot.params] by index (defaults and
 * ranges from [FxModules]), and optional tail tracking.
 */
abstract class ModuleProcessor(type: FxType, protected val sampleRate: Int) : FxProcessor(type) {
    protected val spec: FxModuleSpec = FxModules.spec(type)
    private var configured = false

    // Tail tracking: samples since the input was last audible, and the wet peak per block
    private var quietSamples = Int.MAX_VALUE / 2
    private var wetPeak = 0f
    private var lastWetPeak = 0f
    /** How long after the last input the module may still sound (samples); 0 = no tail. */
    protected var tailHoldSamples = 0

    /** Clamped parameter value, default when the slot predates the parameter. No allocation. */
    protected fun prm(slot: FxSlot, index: Int): Float {
        val p = spec.params[index]
        val arr = slot.params
        return if (arr != null && index < arr.size) p.clamp(arr[index]) else p.default
    }

    protected fun prmInt(slot: FxSlot, index: Int): Int = (prm(slot, index) + 0.5f).toInt()

    final override fun configure(slot: FxSlot) {
        val snap = !configured
        configured = true
        lastWetPeak = wetPeak
        wetPeak = 0f
        onConfigure(slot, snap)
    }

    /** Read parameters (once per block). [snap] = first configure: jump, don't ramp. */
    protected abstract fun onConfigure(slot: FxSlot, snap: Boolean)

    /** Call per sample from tail modules with the input and the wet signal. */
    protected fun trackTail(inL: Float, inR: Float, wetL: Float, wetR: Float) {
        val a = abs(inL) + abs(inR)
        if (a > 1e-5f) quietSamples = 0 else if (quietSamples < Int.MAX_VALUE / 2) quietSamples++
        val w = abs(wetL) + abs(wetR)
        if (w > wetPeak) wetPeak = w
    }

    override fun hasTail(): Boolean =
        tailHoldSamples > 0 && (quietSamples < tailHoldSamples || wetPeak > 1e-5f || lastWetPeak > 1e-5f)

    protected fun resetTail() {
        quietSamples = Int.MAX_VALUE / 2
        wetPeak = 0f
        lastWetPeak = 0f
    }

    /** LFO frequency for a sync table: synced (beats per cycle) or the free rate in Hz. */
    protected fun lfoHz(syncBeats: Float, freeHz: Float): Float =
        if (syncBeats > 0f) (bpm.toFloat() / 60f) / syncBeats else freeHz
}

// =============================================================================================
// Biquad (RBJ cookbook), transposed direct form II, stereo
// =============================================================================================

class Biquad {
    var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f
    private var z1L = 0f; private var z2L = 0f; private var z1R = 0f; private var z2R = 0f

    fun processL(x: Float): Float {
        val y = b0 * x + z1L
        z1L = flushDenorm(b1 * x - a1 * y + z2L)
        z2L = flushDenorm(b2 * x - a2 * y)
        return y
    }

    fun processR(x: Float): Float {
        val y = b0 * x + z1R
        z1R = flushDenorm(b1 * x - a1 * y + z2R)
        z2R = flushDenorm(b2 * x - a2 * y)
        return y
    }

    fun reset() { z1L = 0f; z2L = 0f; z1R = 0f; z2R = 0f }

    fun setLowShelf(sr: Int, f: Float, gainDb: Float) = BiquadMath.lowShelf(sr, f, gainDb, this)
    fun setHighShelf(sr: Int, f: Float, gainDb: Float) = BiquadMath.highShelf(sr, f, gainDb, this)
    fun setPeak(sr: Int, f: Float, gainDb: Float, q: Float) = BiquadMath.peak(sr, f, gainDb, q, this)

    internal fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / na0).toFloat(); b1 = (nb1 / na0).toFloat(); b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat(); a2 = (na2 / na0).toFloat()
    }
}

/** Coefficient maths shared by the DSP and the EQ curve drawn in the rack UI. */
object BiquadMath {
    fun lowShelf(sr: Int, f: Float, gainDb: Float, out: Biquad) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f.coerceIn(10f, sr * 0.45f) / sr
        val cw = cos(w0); val sw = sin(w0)
        val alpha = sw / 2.0 * sqrt(2.0) // shelf slope S = 1
        val sa = 2.0 * sqrt(a) * alpha
        out.set(
            a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
            (a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa
        )
    }

    fun highShelf(sr: Int, f: Float, gainDb: Float, out: Biquad) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f.coerceIn(10f, sr * 0.45f) / sr
        val cw = cos(w0); val sw = sin(w0)
        val alpha = sw / 2.0 * sqrt(2.0)
        val sa = 2.0 * sqrt(a) * alpha
        out.set(
            a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sa),
            (a + 1) - (a - 1) * cw + sa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa
        )
    }

    fun peak(sr: Int, f: Float, gainDb: Float, q: Float, out: Biquad) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f.coerceIn(10f, sr * 0.45f) / sr
        val cw = cos(w0)
        val alpha = sin(w0) / (2.0 * q.coerceIn(0.1f, 20f))
        out.set(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
    }

    /** Magnitude response of [bq] at [f] Hz, in dB. UI only. */
    fun magnitudeDb(bq: Biquad, f: Float, sr: Int): Float {
        val w = 2.0 * PI * f / sr
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = bq.b0 + bq.b1 * c1 + bq.b2 * c2
        val ni = -(bq.b1 * s1 + bq.b2 * s2)
        val dr = 1.0 + bq.a1 * c1 + bq.a2 * c2
        val di = -(bq.a1 * s1 + bq.a2 * s2)
        val mag = sqrt((nr * nr + ni * ni) / (dr * dr + di * di).coerceAtLeast(1e-12))
        return (20.0 * log10(mag.coerceAtLeast(1e-6))).toFloat()
    }
}

// =============================================================================================
// EQ & Filter
// =============================================================================================

/** Low shelf + peaking mid + high shelf + output trim. Coefficients refresh every 32 samples while ramping. */
class ParamEqProcessor(sr: Int) : ModuleProcessor(FxType.PARAM_EQ, sr) {
    private val low = Biquad(); private val mid = Biquad(); private val high = Biquad()
    private val sLowF = Smoother(sr, 40f); private val sLowG = Smoother(sr, 40f)
    private val sMidF = Smoother(sr, 40f); private val sMidG = Smoother(sr, 40f); private val sMidQ = Smoother(sr, 40f)
    private val sHighF = Smoother(sr, 40f); private val sHighG = Smoother(sr, 40f)
    private val sOut = Smoother(sr, 20f)
    private var counter = 0
    private var dirty = true

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        sLowF.set(prm(slot, FxIdx.ParamEq.LOW_FREQ), snap)
        sLowG.set(prm(slot, FxIdx.ParamEq.LOW_GAIN), snap)
        sMidF.set(prm(slot, FxIdx.ParamEq.MID_FREQ), snap)
        sMidG.set(prm(slot, FxIdx.ParamEq.MID_GAIN), snap)
        sMidQ.set(prm(slot, FxIdx.ParamEq.MID_Q), snap)
        sHighF.set(prm(slot, FxIdx.ParamEq.HIGH_FREQ), snap)
        sHighG.set(prm(slot, FxIdx.ParamEq.HIGH_GAIN), snap)
        sOut.set(dbToLin(prm(slot, FxIdx.ParamEq.OUTPUT)), snap)
        if (snap) updateCoefficients()
    }

    private fun updateCoefficients() {
        low.setLowShelf(sampleRate, sLowF.value, sLowG.value)
        mid.setPeak(sampleRate, sMidF.value, sMidG.value, sMidQ.value)
        high.setHighShelf(sampleRate, sHighF.value, sHighG.value)
        dirty = !(sLowF.settled && sLowG.settled && sMidF.settled && sMidG.settled && sMidQ.settled && sHighF.settled && sHighG.settled)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        if (--counter <= 0) {
            counter = 32
            // Advance the coefficient smoothers at control rate (32 x per-sample steps)
            if (dirty || !(sLowF.settled && sLowG.settled && sMidF.settled && sMidG.settled && sMidQ.settled && sHighF.settled && sHighG.settled)) {
                for (k in 0 until 32) { sLowF.next(); sLowG.next(); sMidF.next(); sMidG.next(); sMidQ.next(); sHighF.next(); sHighG.next() }
                updateCoefficients()
            }
        }
        val g = sOut.next()
        out[0] = high.processL(mid.processL(low.processL(inL))) * g
        out[1] = high.processR(mid.processR(low.processR(inR))) * g
    }

    override fun reset() { low.reset(); mid.reset(); high.reset(); counter = 0 }
}

/** TPT state-variable filter (stable under fast modulation) with an LFO on the cutoff (+/-3 octaves). */
class AutoFilterProcessor(sr: Int) : ModuleProcessor(FxType.AUTO_FILTER, sr) {
    private var mode = 0
    private val sCut = Smoother(sr, 30f)
    private val sRes = Smoother(sr, 30f)
    private val sDepth = Smoother(sr, 30f)
    private val sMix = Smoother(sr, 20f)
    private var lfoInc = 0f
    private var phase = 0f
    private var counter = 0
    private var a1 = 0f; private var a2 = 0f; private var a3 = 0f; private var k = 2f
    private var ic1L = 0f; private var ic2L = 0f; private var ic1R = 0f; private var ic2R = 0f
    private val nyquistGuard = sr * 0.45f

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        mode = prmInt(slot, FxIdx.Filter.MODE)
        sCut.set(prm(slot, FxIdx.Filter.CUTOFF), snap)
        sRes.set(prm(slot, FxIdx.Filter.RESONANCE), snap)
        sDepth.set(prm(slot, FxIdx.Filter.DEPTH), snap)
        sMix.set(prm(slot, FxIdx.Filter.MIX), snap)
        val sync = prmInt(slot, FxIdx.Filter.SYNC).coerceIn(0, FxModules.FILTER_SYNC_BEATS.size - 1)
        lfoInc = lfoHz(FxModules.FILTER_SYNC_BEATS[sync], prm(slot, FxIdx.Filter.RATE)) / sampleRate
        if (snap) { counter = 0 }
    }

    private fun updateCoefficients(lfo: Float) {
        val oct = lfo * sDepth.value * 3f
        var fc = sCut.value * 2f.pow(oct)
        if (fc < 20f) fc = 20f
        if (fc > nyquistGuard) fc = nyquistGuard
        val g = tan(PI * fc / sampleRate).toFloat()
        k = 2f - 1.94f * sRes.value
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        phase += lfoInc
        if (phase >= 1f) phase -= 1f
        sCut.next(); sRes.next(); sDepth.next()
        if (--counter <= 0) {
            counter = 16
            updateCoefficients(sin(TWO_PI_F * phase))
        }
        val mix = sMix.next()
        // Left
        var v3 = inL - ic2L
        var v1 = a1 * ic1L + a2 * v3
        var v2 = ic2L + a2 * ic1L + a3 * v3
        ic1L = flushDenorm(2f * v1 - ic1L)
        ic2L = flushDenorm(2f * v2 - ic2L)
        val yL = when (mode) { 1 -> inL - k * v1 - v2; 2 -> k * v1; else -> v2 }
        // Right
        v3 = inR - ic2R
        v1 = a1 * ic1R + a2 * v3
        v2 = ic2R + a2 * ic1R + a3 * v3
        ic1R = flushDenorm(2f * v1 - ic1R)
        ic2R = flushDenorm(2f * v2 - ic2R)
        val yR = when (mode) { 1 -> inR - k * v1 - v2; 2 -> k * v1; else -> v2 }
        out[0] = inL + (yL - inL) * mix
        out[1] = inR + (yR - inR) * mix
    }

    override fun reset() { ic1L = 0f; ic2L = 0f; ic1R = 0f; ic2R = 0f; counter = 0 }

    /** Current LFO phase 0..1 (UI may show it; not synchronised). */
    val lfoPhase: Float get() = phase
}

// =============================================================================================
// Dynamics
// =============================================================================================

/** Stereo-linked gate: peak detector (5 ms decay), hysteresis, hold, attack/release, range floor. */
class GateProcessor(sr: Int) : ModuleProcessor(FxType.GATE, sr) {
    private val detDecay = exp(-1.0 / (0.005 * sr)).toFloat()
    private var openThresh = 0.01f
    private var closeThresh = 0.006f
    private var attackCoef = 0.5f
    private var releaseCoef = 0.001f
    private var holdSamples = 0
    private var floorGain = 0f
    private var env = 0f
    private var gain = 0f
    private var holdLeft = 0
    private var open = false
    private var minGain = 1f

    private fun coefFor(ms: Float): Float = (1.0 - exp(-1.0 / (ms.coerceAtLeast(0.05f) * 0.001 * sampleRate))).toFloat()

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        meter = if (minGain < 0.9999f) 20f * log10(minGain.coerceAtLeast(1e-4f)) else 0f
        minGain = 1f
        val t = prm(slot, FxIdx.Gate.THRESHOLD)
        openThresh = dbToLin(t)
        closeThresh = dbToLin(t - 4f)
        attackCoef = coefFor(prm(slot, FxIdx.Gate.ATTACK))
        releaseCoef = coefFor(prm(slot, FxIdx.Gate.RELEASE))
        holdSamples = (prm(slot, FxIdx.Gate.HOLD) * 0.001f * sampleRate).toInt()
        val r = prm(slot, FxIdx.Gate.RANGE)
        floorGain = if (r <= -79.5f) 0f else dbToLin(r)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        val aL = abs(inL); val aR = abs(inR)
        val peak = if (aL > aR) aL else aR
        env = if (peak > env) peak else env * detDecay
        if (env > openThresh) { open = true; holdLeft = holdSamples }
        else if (env < closeThresh) {
            if (holdLeft > 0) holdLeft-- else open = false
        }
        val target = if (open) 1f else 0f
        gain += (target - gain) * (if (open) attackCoef else releaseCoef)
        gain = flushDenorm(gain)
        val g = floorGain + (1f - floorGain) * gain
        if (g < minGain) minGain = g
        out[0] = inL * g
        out[1] = inR * g
    }

    override fun reset() { env = 0f; gain = 0f; holdLeft = 0; open = false; minGain = 1f }
}

// =============================================================================================
// Modulation
// =============================================================================================

/** Fractional read from a power-of-two ring buffer: [delay] samples behind [write]. */
@Suppress("NOTHING_TO_INLINE")
private inline fun readFrac(buf: FloatArray, mask: Int, write: Int, delay: Float): Float {
    val pos = write.toFloat() - delay
    val fl = floor(pos)
    val i0 = fl.toInt()
    val frac = pos - fl
    val a = buf[i0 and mask]
    val b = buf[(i0 + 1) and mask]
    return a + (b - a) * frac
}

private fun pow2AtLeast(n: Int): Int { var s = 1; while (s < n) s = s shl 1; return s }

class ChorusProcessor(sr: Int) : ModuleProcessor(FxType.CHORUS, sr) {
    private val size = pow2AtLeast((0.06 * sr).toInt())
    private val mask = size - 1
    private val bufL = FloatArray(size)
    private val bufR = FloatArray(size)
    private var w = 0
    private var phase = 0f
    private var inc = 0f
    private val sDelay = Smoother(sr, 50f)
    private val sDepth = Smoother(sr, 30f)
    private val sSpread = Smoother(sr, 50f)
    private val sMix = Smoother(sr, 20f)
    private val msToSamples = sr / 1000f

    init { tailHoldSamples = size }

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        inc = prm(slot, FxIdx.Chorus.RATE) / sampleRate
        sDelay.set(prm(slot, FxIdx.Chorus.DELAY), snap)
        sDepth.set(prm(slot, FxIdx.Chorus.DEPTH), snap)
        sSpread.set(prm(slot, FxIdx.Chorus.SPREAD), snap)
        sMix.set(prm(slot, FxIdx.Chorus.MIX), snap)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        phase += inc
        if (phase >= 1f) phase -= 1f
        val base = sDelay.next()
        val depthMs = sDepth.next() * 6f
        val spread = sSpread.next()
        val mix = sMix.next()
        bufL[w] = inL
        bufR[w] = inR
        val lfoL = sin(TWO_PI_F * phase)
        val lfoR = sin(TWO_PI_F * phase + PI.toFloat() * spread)
        val dL = (base + depthMs * 0.5f * (1f + lfoL)) * msToSamples
        val dR = (base + depthMs * 0.5f * (1f + lfoR)) * msToSamples
        val wetL = readFrac(bufL, mask, w, dL)
        val wetR = readFrac(bufR, mask, w, dR)
        w = (w + 1) and mask
        out[0] = inL + (wetL - inL) * mix
        out[1] = inR + (wetR - inR) * mix
        trackTail(inL, inR, wetL, wetR)
    }

    override fun reset() { bufL.fill(0f); bufR.fill(0f); w = 0; resetTail() }
}

class FlangerProcessor(sr: Int) : ModuleProcessor(FxType.FLANGER, sr) {
    private val size = pow2AtLeast((0.02 * sr).toInt())
    private val mask = size - 1
    private val bufL = FloatArray(size)
    private val bufR = FloatArray(size)
    private var w = 0
    private var phase = 0f
    private var inc = 0f
    private var fbL = 0f
    private var fbR = 0f
    private val sDelay = Smoother(sr, 50f)
    private val sDepth = Smoother(sr, 30f)
    private val sFb = Smoother(sr, 30f)
    private val sMix = Smoother(sr, 20f)
    private val msToSamples = sr / 1000f

    init { tailHoldSamples = sr / 2 }

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        inc = prm(slot, FxIdx.Flanger.RATE) / sampleRate
        sDelay.set(prm(slot, FxIdx.Flanger.DELAY), snap)
        sDepth.set(prm(slot, FxIdx.Flanger.DEPTH), snap)
        sFb.set(prm(slot, FxIdx.Flanger.FEEDBACK), snap)
        sMix.set(prm(slot, FxIdx.Flanger.MIX), snap)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        phase += inc
        if (phase >= 1f) phase -= 1f
        val base = sDelay.next()
        val depthMs = sDepth.next() * 4f
        val fb = sFb.next()
        val mix = sMix.next()
        val lfoL = 0.5f * (1f + sin(TWO_PI_F * phase))
        val lfoR = 0.5f * (1f + sin(TWO_PI_F * phase + 0.5f * PI.toFloat()))
        bufL[w] = inL + fb * fbL
        bufR[w] = inR + fb * fbR
        val dL = 1f + (base + depthMs * lfoL) * msToSamples
        val dR = 1f + (base + depthMs * lfoR) * msToSamples
        val wetL = readFrac(bufL, mask, w, dL)
        val wetR = readFrac(bufR, mask, w, dR)
        fbL = flushDenorm(wetL)
        fbR = flushDenorm(wetR)
        w = (w + 1) and mask
        out[0] = inL + (wetL - inL) * mix
        out[1] = inR + (wetR - inR) * mix
        trackTail(inL, inR, wetL, wetR)
    }

    override fun reset() { bufL.fill(0f); bufR.fill(0f); w = 0; fbL = 0f; fbR = 0f; resetTail() }
}

/** Up to 12 first-order all-pass stages per side, swept 200 Hz .. ~9 kHz, with feedback. */
class PhaserProcessor(sr: Int) : ModuleProcessor(FxType.PHASER, sr) {
    private val zL = FloatArray(12)
    private val zR = FloatArray(12)
    private var stages = 6
    private var phase = 0f
    private var inc = 0f
    private var lastL = 0f
    private var lastR = 0f
    private var aL = 0f
    private var aR = 0f
    private var counter = 0
    private val sDepth = Smoother(sr, 30f)
    private val sFb = Smoother(sr, 30f)
    private val sMix = Smoother(sr, 20f)

    init { tailHoldSamples = sr / 4 }

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        inc = prm(slot, FxIdx.Phaser.RATE) / sampleRate
        sDepth.set(prm(slot, FxIdx.Phaser.DEPTH), snap)
        sFb.set(prm(slot, FxIdx.Phaser.FEEDBACK), snap)
        sMix.set(prm(slot, FxIdx.Phaser.MIX), snap)
        val idx = prmInt(slot, FxIdx.Phaser.STAGES).coerceIn(0, FxModules.PHASER_STAGES.size - 1)
        stages = FxModules.PHASER_STAGES[idx]
    }

    private fun coefFor(lfo01: Float, depth: Float): Float {
        val f = (200f * 2f.pow(lfo01 * depth * 5.5f)).coerceAtMost(sampleRate * 0.45f)
        val t = tan(PI * f / sampleRate).toFloat()
        return (1f - t) / (1f + t)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        phase += inc
        if (phase >= 1f) phase -= 1f
        val depth = sDepth.next()
        val fb = sFb.next()
        val mix = sMix.next()
        if (--counter <= 0) {
            counter = 8
            aL = coefFor(0.5f * (1f + sin(TWO_PI_F * phase)), depth)
            aR = coefFor(0.5f * (1f + sin(TWO_PI_F * phase + 0.5f * PI.toFloat())), depth)
        }
        var xL = inL + fb * lastL
        var xR = inR + fb * lastR
        for (s in 0 until stages) {
            val yL = -aL * xL + zL[s]
            zL[s] = flushDenorm(xL + aL * yL)
            xL = yL
            val yR = -aR * xR + zR[s]
            zR[s] = flushDenorm(xR + aR * yR)
            xR = yR
        }
        lastL = flushDenorm(xL)
        lastR = flushDenorm(xR)
        out[0] = inL + (xL - inL) * mix
        out[1] = inR + (xR - inR) * mix
        trackTail(inL, inR, xL, xR)
    }

    override fun reset() { zL.fill(0f); zR.fill(0f); lastL = 0f; lastR = 0f; counter = 0; resetTail() }
}

/** Tremolo (amplitude) or auto-pan (constant power), sine / triangle / square (de-clicked). */
class TremoloProcessor(sr: Int) : ModuleProcessor(FxType.TREMOLO, sr) {
    private var mode = 0
    private var shape = 0
    private var phase = 0f
    private var inc = 0f
    private val sDepth = Smoother(sr, 30f)
    private var shaped = 0f
    private val squareSmooth = (1.0 - exp(-1.0 / (0.002 * sr))).toFloat()
    private val quarterPi = (PI / 4.0).toFloat()
    private val sqrt2 = sqrt(2f)

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        mode = prmInt(slot, FxIdx.Tremolo.MODE)
        shape = prmInt(slot, FxIdx.Tremolo.SHAPE)
        val sync = prmInt(slot, FxIdx.Tremolo.SYNC).coerceIn(0, FxModules.TREMOLO_SYNC_BEATS.size - 1)
        inc = lfoHz(FxModules.TREMOLO_SYNC_BEATS[sync], prm(slot, FxIdx.Tremolo.RATE)) / sampleRate
        sDepth.set(prm(slot, FxIdx.Tremolo.DEPTH), snap)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        phase += inc
        if (phase >= 1f) phase -= 1f
        val raw = when (shape) {
            1 -> if (phase < 0.5f) 4f * phase - 1f else 3f - 4f * phase
            2 -> if (phase < 0.5f) 1f else -1f
            else -> sin(TWO_PI_F * phase)
        }
        shaped = if (shape == 2) shaped + (raw - shaped) * squareSmooth else raw
        val depth = sDepth.next()
        if (mode == 1) {
            val pan = shaped * depth
            val ang = (pan + 1f) * quarterPi
            out[0] = inL * cos(ang) * sqrt2
            out[1] = inR * sin(ang) * sqrt2
        } else {
            val g = 1f - depth * 0.5f * (1f - shaped)
            out[0] = inL * g
            out[1] = inR * g
        }
    }

    override fun reset() { shaped = 0f }
}

// =============================================================================================
// Space
// =============================================================================================

/**
 * Freeverb (Jezar's public-domain Schroeder/Moorer design): 8 damped combs + 4 all-passes per
 * side, right side offset by 23 samples, plus pre-delay and a low cut on the tank input.
 */
class ReverbProcessor(sr: Int) : ModuleProcessor(FxType.REVERB, sr) {
    private val scale = sr / 44100f
    private val combLen = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val apLen = intArrayOf(556, 441, 341, 225)
    private val spread = 23
    private val combL = Array(8) { FloatArray(((combLen[it]) * scale).toInt().coerceAtLeast(1)) }
    private val combR = Array(8) { FloatArray(((combLen[it] + spread) * scale).toInt().coerceAtLeast(1)) }
    private val combIdxL = IntArray(8); private val combIdxR = IntArray(8)
    private val storeL = FloatArray(8); private val storeR = FloatArray(8)
    private val apL = Array(4) { FloatArray(((apLen[it]) * scale).toInt().coerceAtLeast(1)) }
    private val apR = Array(4) { FloatArray(((apLen[it] + spread) * scale).toInt().coerceAtLeast(1)) }
    private val apIdxL = IntArray(4); private val apIdxR = IntArray(4)

    private val preSize = pow2AtLeast((0.21 * sr).toInt())
    private val preMask = preSize - 1
    private val preBuf = FloatArray(preSize)
    private var preW = 0

    private var feedback = 0.84f
    private var damp1 = 0.2f
    private var damp2 = 0.8f
    private var lowCutAlpha = 0.01f
    private var lowLp = 0f
    private val sPre = Smoother(sr, 60f)
    private val sMix = Smoother(sr, 20f)
    private val sWidth = Smoother(sr, 30f)

    init { tailHoldSamples = preSize + (2 * sr) }

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        feedback = prm(slot, FxIdx.Reverb.SIZE) * 0.28f + 0.7f
        val d = prm(slot, FxIdx.Reverb.DAMPING) * 0.4f
        damp1 = d
        damp2 = 1f - d
        sWidth.set(prm(slot, FxIdx.Reverb.WIDTH), snap)
        sPre.set(prm(slot, FxIdx.Reverb.PRE_DELAY) * 0.001f * sampleRate, snap)
        lowCutAlpha = (1.0 - exp(-2.0 * PI * prm(slot, FxIdx.Reverb.LOW_CUT) / sampleRate)).toFloat()
        sMix.set(prm(slot, FxIdx.Reverb.MIX), snap)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        // Pre-delay (mono), then low cut, into the tank
        preBuf[preW] = (inL + inR) * 0.5f
        val pre = readFrac(preBuf, preMask, preW, sPre.next().coerceAtLeast(0f))
        preW = (preW + 1) and preMask
        lowLp = flushDenorm(lowLp + lowCutAlpha * (pre - lowLp))
        val input = (pre - lowLp) * 0.03f // freeverb fixed gain 0.015 x 2 (mono sum of two channels)

        var accL = 0f
        var accR = 0f
        for (c in 0 until 8) {
            val bl = combL[c]
            var i = combIdxL[c]
            val oL = bl[i]
            storeL[c] = flushDenorm(oL * damp2 + storeL[c] * damp1)
            bl[i] = input + storeL[c] * feedback
            if (++i >= bl.size) i = 0
            combIdxL[c] = i
            accL += oL

            val br = combR[c]
            var j = combIdxR[c]
            val oR = br[j]
            storeR[c] = flushDenorm(oR * damp2 + storeR[c] * damp1)
            br[j] = input + storeR[c] * feedback
            if (++j >= br.size) j = 0
            combIdxR[c] = j
            accR += oR
        }
        for (a in 0 until 4) {
            val bl = apL[a]
            var i = apIdxL[a]
            val bo = bl[i]
            val yl = -accL + bo
            bl[i] = flushDenorm(accL + bo * 0.5f)
            if (++i >= bl.size) i = 0
            apIdxL[a] = i
            accL = yl

            val br = apR[a]
            var j = apIdxR[a]
            val bo2 = br[j]
            val yr = -accR + bo2
            br[j] = flushDenorm(accR + bo2 * 0.5f)
            if (++j >= br.size) j = 0
            apIdxR[a] = j
            accR = yr
        }
        val width = sWidth.next()
        val wet1 = 3f * (width * 0.5f + 0.5f)
        val wet2 = 3f * ((1f - width) * 0.5f)
        val wetL = accL * wet1 + accR * wet2
        val wetR = accR * wet1 + accL * wet2
        val mix = sMix.next()
        val dry = dryGainFor(mix)
        val wet = wetGainFor(mix)
        out[0] = inL * dry + wetL * wet
        out[1] = inR * dry + wetR * wet
        trackTail(inL, inR, wetL, wetR)
    }

    override fun reset() {
        for (b in combL) b.fill(0f)
        for (b in combR) b.fill(0f)
        for (b in apL) b.fill(0f)
        for (b in apR) b.fill(0f)
        storeL.fill(0f); storeR.fill(0f)
        combIdxL.fill(0); combIdxR.fill(0); apIdxL.fill(0); apIdxR.fill(0)
        preBuf.fill(0f); preW = 0; lowLp = 0f
        resetTail()
    }
}

/**
 * Stereo / ping-pong delay up to 2.5 s. Time glides (tape-style) instead of clicking when it
 * changes; a one-pole high cut sits in the feedback path so repeats get darker.
 */
class DelayProcessor(sr: Int) : ModuleProcessor(FxType.DELAY, sr) {
    private val size = pow2AtLeast((2.6 * sr).toInt())
    private val mask = size - 1
    private val maxDelay = (2.5f * sr)
    private val bufL = FloatArray(size)
    private val bufR = FloatArray(size)
    private var w = 0
    private var pingPong = false
    private val sTime = Smoother(sr, 120f)
    private val sFb = Smoother(sr, 30f)
    private val sMix = Smoother(sr, 20f)
    private var hcAlpha = 0.5f
    private var lpL = 0f
    private var lpR = 0f

    /** Current delay time in ms (UI tap display). */
    @Volatile var delayMs: Float = 375f
        private set

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        val sync = prmInt(slot, FxIdx.Delay.SYNC).coerceIn(0, FxModules.DELAY_SYNC_BEATS.size - 1)
        val beats = FxModules.DELAY_SYNC_BEATS[sync]
        val samples = if (beats > 0f) beats * 60f / bpm.toFloat() * sampleRate
        else prm(slot, FxIdx.Delay.TIME) * 0.001f * sampleRate
        val clamped = samples.coerceIn(1f, maxDelay)
        sTime.set(clamped, snap)
        delayMs = clamped * 1000f / sampleRate
        sFb.set(prm(slot, FxIdx.Delay.FEEDBACK), snap)
        pingPong = prmInt(slot, FxIdx.Delay.PING_PONG) == 1
        hcAlpha = (1.0 - exp(-2.0 * PI * prm(slot, FxIdx.Delay.HIGH_CUT) / sampleRate)).toFloat()
        sMix.set(prm(slot, FxIdx.Delay.MIX), snap)
        tailHoldSamples = (clamped * 2f).toInt() + 2048
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        val d = sTime.next()
        val fb = sFb.next()
        val mix = sMix.next()
        val dlL = readFrac(bufL, mask, w, d)
        val dlR = readFrac(bufR, mask, w, d)
        lpL = flushDenorm(lpL + hcAlpha * (dlL - lpL))
        lpR = flushDenorm(lpR + hcAlpha * (dlR - lpR))
        if (pingPong) {
            bufL[w] = (inL + inR) * 0.5f + fb * lpR
            bufR[w] = fb * lpL
        } else {
            bufL[w] = inL + fb * lpL
            bufR[w] = inR + fb * lpR
        }
        w = (w + 1) and mask
        val dry = dryGainFor(mix)
        val wet = wetGainFor(mix)
        out[0] = inL * dry + lpL * wet
        out[1] = inR * dry + lpR * wet
        trackTail(inL, inR, lpL, lpR)
    }

    override fun reset() { bufL.fill(0f); bufR.fill(0f); w = 0; lpL = 0f; lpR = 0f; resetTail() }
}

// =============================================================================================
// Distortion
// =============================================================================================

/** Rational tanh approximation, exact +/-1 beyond |x| = 3. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun softClip3(x: Float): Float = when {
    x > 3f -> 1f
    x < -3f -> -1f
    else -> x * (27f + x * x) / (27f + 9f * x * x)
}

class DistortionProcessor(sr: Int) : ModuleProcessor(FxType.DISTORTION, sr) {
    private var curve = 0
    private val sDrive = Smoother(sr, 30f)
    private val sOut = Smoother(sr, 30f)
    private val sMix = Smoother(sr, 20f)
    private var toneAlpha = 0.5f
    private var lpL = 0f; private var lpR = 0f
    // DC blocker (fuzz bias)
    private val dcR = 1f - (2f * PI.toFloat() * 10f / sr)
    private var dcXL = 0f; private var dcYL = 0f; private var dcXR = 0f; private var dcYR = 0f
    private val fuzzZero = fuzz(0f)

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        curve = prmInt(slot, FxIdx.Distortion.TYPE)
        sDrive.set(dbToLin(prm(slot, FxIdx.Distortion.DRIVE)), snap)
        sOut.set(dbToLin(prm(slot, FxIdx.Distortion.OUTPUT)), snap)
        sMix.set(prm(slot, FxIdx.Distortion.MIX), snap)
        val tone = prm(slot, FxIdx.Distortion.TONE)
        val fc = (800.0 * 25.0.pow(tone.toDouble())).coerceAtMost(sampleRate * 0.45)
        toneAlpha = (1.0 - exp(-2.0 * PI * fc / sampleRate)).toFloat()
    }

    private fun fuzz(x: Float): Float {
        val b = x + 0.25f
        return if (b > 0f) 1f - exp(-2f * b) else -0.6f * (1f - exp(3f * b))
    }

    private fun shape(x: Float): Float = when (curve) {
        1 -> if (x > 1f) 1f else if (x < -1f) -1f else x
        2 -> fuzz(x) - fuzzZero
        else -> softClip3(x)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        val drive = sDrive.next()
        val outG = sOut.next()
        val mix = sMix.next()
        var yL = shape(inL * drive)
        var yR = shape(inR * drive)
        if (curve == 2) {
            val nL = yL - dcXL + dcR * dcYL; dcXL = yL; dcYL = flushDenorm(nL); yL = nL
            val nR = yR - dcXR + dcR * dcYR; dcXR = yR; dcYR = flushDenorm(nR); yR = nR
        }
        lpL = flushDenorm(lpL + toneAlpha * (yL - lpL))
        lpR = flushDenorm(lpR + toneAlpha * (yR - lpR))
        out[0] = inL + (lpL * outG - inL) * mix
        out[1] = inR + (lpR * outG - inR) * mix
    }

    override fun reset() { lpL = 0f; lpR = 0f; dcXL = 0f; dcYL = 0f; dcXR = 0f; dcYR = 0f }
}

/** Sample-and-hold downsampling (fractional factor) and bit-depth quantisation. */
class BitcrusherProcessor(sr: Int) : ModuleProcessor(FxType.BITCRUSHER, sr) {
    private var levels = 128f
    private var step = 0.25f
    private var acc = 1f
    private var holdL = 0f
    private var holdR = 0f
    private val sMix = Smoother(sr, 20f)
    private val sOut = Smoother(sr, 30f)

    override fun onConfigure(slot: FxSlot, snap: Boolean) {
        levels = 2f.pow(prm(slot, FxIdx.Crusher.BITS) - 1f)
        step = 1f / prm(slot, FxIdx.Crusher.DOWNSAMPLE)
        sMix.set(prm(slot, FxIdx.Crusher.MIX), snap)
        sOut.set(dbToLin(prm(slot, FxIdx.Crusher.OUTPUT)), snap)
    }

    override fun process(inL: Float, inR: Float, out: FloatArray) {
        acc += step
        if (acc >= 1f) {
            acc -= 1f
            holdL = floor(inL * levels + 0.5f) / levels
            holdR = floor(inR * levels + 0.5f) / levels
        }
        val mix = sMix.next()
        val g = sOut.next()
        out[0] = inL + (holdL * g - inL) * mix
        out[1] = inR + (holdR * g - inR) * mix
    }

    override fun reset() { acc = 1f; holdL = 0f; holdR = 0f }
}
