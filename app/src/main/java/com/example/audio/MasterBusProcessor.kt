package com.example.audio

import com.example.model.MasteringConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The master bus shared by live playback ([SanwolfAudioEngine]) and WAV export ([WavExporter]),
 * so what you hear is what gets exported.
 *
 * Chain (when mastering is enabled):
 *   master volume x drive -> low shelf (120 Hz) + high shelf (6 kHz) -> 2:1 glue compressor
 *   (stereo-linked, 10 ms attack / 150 ms release, auto make-up) -> stereo width (M/S)
 *   -> look-ahead brick-wall limiter at the ceiling.
 * When mastering is bypassed only master volume and a -0.3 dBFS safety limiter run.
 *
 * Real-time safe: no allocation in [process]; dB->linear conversions happen in [configure]
 * only when a setting changes.
 */
class MasterBusProcessor(private val sampleRate: Int = SanwolfAudioEngine.SAMPLE_RATE) {

    private val limiter = LookAheadLimiter(sampleRate, 5.0f, 80.0f, -0.3f)

    // ---- Block-rate parameters ----
    private var enabled = false
    private var preGain = 1f
    private var lowShelfDelta = 0f   // linear shelf gain - 1
    private var highShelfDelta = 0f
    private var width = 1f
    private var compOn = false
    private var compThreshold = 1f
    private var compMakeup = 1f

    // dB caches so pow() only runs when a value changes
    private var cachedLowDb = Float.NaN
    private var cachedHighDb = Float.NaN
    private var cachedDriveDb = Float.NaN
    private var cachedThreshDb = Float.NaN
    private var driveLinear = 1f

    // ---- Filter / dynamics state ----
    private val lowAlpha = (1.0 - exp(-2.0 * PI * 120.0 / sampleRate)).toFloat()
    private val highAlpha = (1.0 - exp(-2.0 * PI * 6000.0 / sampleRate)).toFloat()
    private var lowLpL = 0f
    private var lowLpR = 0f
    private var highLpL = 0f
    private var highLpR = 0f
    private val compAttack = exp(-1.0 / (0.010 * sampleRate)).toFloat()
    private val compRelease = exp(-1.0 / (0.150 * sampleRate)).toFloat()
    private var compEnv = 0f

    // ---- Per-block metering (read by the UI through the engine) ----
    private var minCompGain = 1f
    private var minLimiterGain = 1f

    /** Reads the current settings. Call once per block, before [process]. */
    fun configure(config: MasteringConfig?, masterVolume: Float) {
        val vol = masterVolume.coerceIn(0f, 1.5f)
        enabled = config != null && config.enabled
        if (!enabled || config == null) {
            preGain = vol
            limiter.ceilingDb = -0.3f
            return
        }
        val lowDb = config.lowGainDb.coerceIn(-12f, 12f)
        if (lowDb != cachedLowDb) {
            cachedLowDb = lowDb
            lowShelfDelta = 10.0.pow(lowDb / 20.0).toFloat() - 1f
        }
        val highDb = config.highGainDb.coerceIn(-12f, 12f)
        if (highDb != cachedHighDb) {
            cachedHighDb = highDb
            highShelfDelta = 10.0.pow(highDb / 20.0).toFloat() - 1f
        }
        val driveDb = config.limiterGainDb.coerceIn(0f, 12f)
        if (driveDb != cachedDriveDb) {
            cachedDriveDb = driveDb
            driveLinear = 10.0.pow(driveDb / 20.0).toFloat()
        }
        val threshDb = config.compressorThresholdDb.coerceIn(-40f, 0f)
        if (threshDb != cachedThreshDb) {
            cachedThreshDb = threshDb
            compThreshold = 10.0.pow(threshDb / 20.0).toFloat()
            // Auto make-up: give back half of the reduction a full-scale peak would get at 2:1
            compMakeup = 10.0.pow((-threshDb * 0.25) / 20.0).toFloat()
        }
        compOn = threshDb < -0.5f
        preGain = vol * driveLinear
        width = config.stereoWidth.coerceIn(0f, 2f)
        limiter.ceilingDb = config.limiterCeilingDb.coerceIn(-6f, 0f)
    }

    /** Processes one stereo frame into [out] (size >= 2). */
    fun process(inL: Float, inR: Float, out: FloatArray) {
        var l = inL * preGain
        var r = inR * preGain

        if (enabled) {
            // Shelving EQ: one-pole splits, boost/cut the band and add it back
            lowLpL += lowAlpha * (l - lowLpL)
            lowLpR += lowAlpha * (r - lowLpR)
            highLpL += highAlpha * (l - highLpL)
            highLpR += highAlpha * (r - highLpR)
            l += lowShelfDelta * lowLpL + highShelfDelta * (l - highLpL)
            r += lowShelfDelta * lowLpR + highShelfDelta * (r - highLpR)

            // Glue compressor, 2:1, stereo-linked peak envelope
            if (compOn) {
                val aL = abs(l)
                val aR = abs(r)
                val peak = if (aL > aR) aL else aR
                val coeff = if (peak > compEnv) compAttack else compRelease
                compEnv = peak + coeff * (compEnv - peak)
                val g = if (compEnv > compThreshold) sqrt(compThreshold / compEnv) else 1f
                if (g < minCompGain) minCompGain = g
                val total = g * compMakeup
                l *= total
                r *= total
            }

            // Stereo width (mid/side)
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f * width
            l = mid + side
            r = mid - side
        }

        limiter.process(l, r, out)
        val lg = limiter.gain
        if (lg < minLimiterGain) minLimiterGain = lg
    }

    /** Gain reduction since the last call, in positive dB: [compressor, limiter]. Resets the window. */
    fun takeGainReductionDb(out: FloatArray) {
        out[0] = if (minCompGain < 1f) (-20f * log10(minCompGain.coerceAtLeast(1e-6f))) else 0f
        out[1] = if (minLimiterGain < 1f) (-20f * log10(minLimiterGain.coerceAtLeast(1e-6f))) else 0f
        minCompGain = 1f
        minLimiterGain = 1f
    }

    /** Clears filter, compressor and limiter state (equivalent to a long silence). */
    fun reset() {
        lowLpL = 0f; lowLpR = 0f; highLpL = 0f; highLpR = 0f
        compEnv = 0f
        limiter.reset()
    }
}
