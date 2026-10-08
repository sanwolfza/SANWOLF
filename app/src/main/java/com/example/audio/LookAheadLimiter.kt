package com.example.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Brick-wall look-ahead peak limiter.
 *
 * Real-time safe: the look-ahead peak is tracked with a monotonic sliding-window maximum
 * (amortised O(1) per sample) instead of rescanning the whole look-ahead window for every
 * sample, and the linear ceiling is cached instead of calling pow() per sample.
 * Output is identical to the previous full-scan implementation.
 */
class LookAheadLimiter(
    val sampleRate: Int = 44100,
    val lookAheadMs: Float = 5.0f, // 5ms look-ahead
    val releaseMs: Float = 80.0f,
    ceilingDb: Float = -0.3f       // Output ceiling in dB
) {
    private val lookAheadSamples = max(1, (lookAheadMs * sampleRate / 1000f).toInt())

    // Ring buffers for look-ahead delay (stereo)
    private val delayBufferL = FloatArray(lookAheadSamples)
    private val delayBufferR = FloatArray(lookAheadSamples)
    private var writeIndex = 0

    // Monotonic deque (ring) holding candidate peaks and the sample counter they arrived at
    private val dequeCapacity = lookAheadSamples + 1
    private val dequeValues = FloatArray(dequeCapacity)
    private val dequeStamps = LongArray(dequeCapacity)
    private var dequeHead = 0
    private var dequeSize = 0
    private var sampleCounter = 0L

    // Envelope / gain reduction state
    private var currentGain = 1.0f

    private var ceilingLinear: Float = 10.0f.pow(ceilingDb / 20.0f)

    /** Output ceiling in dB. Setting the same value again is free. */
    var ceilingDb: Float = ceilingDb
        set(value) {
            if (value != field) {
                field = value
                ceilingLinear = 10.0f.pow(value / 20.0f)
            }
        }

    // Release coefficient: gain rises back to 1.0
    private val releaseCoeff = exp(-1.0 / (releaseMs * sampleRate / 1000.0)).toFloat()
    private val attackCoeffComplement = 1.0f - releaseCoeff

    /** Clears the delay line and gain state (equivalent to a long stretch of silence). */
    fun reset() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        writeIndex = 0
        dequeHead = 0
        dequeSize = 0
        currentGain = 1.0f
    }

    /**
     * Processes one stereo sample frame, returning the limited output in the provided [outSamples] array.
     */
    fun process(inputL: Float, inputR: Float, outSamples: FloatArray) {
        // 1. Get the delayed samples from the buffers
        val delayedL = delayBufferL[writeIndex]
        val delayedR = delayBufferR[writeIndex]

        // 2. Put the new input samples into the buffers
        delayBufferL[writeIndex] = inputL
        delayBufferR[writeIndex] = inputR
        writeIndex++
        if (writeIndex >= lookAheadSamples) writeIndex = 0

        // 3. Peak over the look-ahead window (the last lookAheadSamples inputs)
        val absL = abs(inputL)
        val absR = abs(inputR)
        val peakNow = if (absL > absR) absL else absR
        val now = sampleCounter++

        // Drop smaller-or-equal candidates from the back
        while (dequeSize > 0) {
            var backIdx = dequeHead + dequeSize - 1
            if (backIdx >= dequeCapacity) backIdx -= dequeCapacity
            if (dequeValues[backIdx] <= peakNow) dequeSize-- else break
        }
        var tail = dequeHead + dequeSize
        if (tail >= dequeCapacity) tail -= dequeCapacity
        dequeValues[tail] = peakNow
        dequeStamps[tail] = now
        dequeSize++
        // Expire candidates that left the window
        val oldestAllowed = now - lookAheadSamples + 1
        while (dequeStamps[dequeHead] < oldestAllowed) {
            dequeHead++
            if (dequeHead >= dequeCapacity) dequeHead = 0
            dequeSize--
        }
        val futurePeak = dequeValues[dequeHead]

        // 4. Calculate required gain to stay below ceiling
        val ceiling = ceilingLinear
        val targetGain = if (futurePeak > ceiling) ceiling / futurePeak else 1.0f

        // 5. Instant attack / smooth release
        if (targetGain < currentGain) {
            // Instant attack: decrease gain immediately (which acts ahead of time for the delayed signal)
            currentGain = targetGain
        } else {
            // Exponential release back towards 1.0
            currentGain = currentGain * releaseCoeff + targetGain * attackCoeffComplement
        }

        // 6. Apply gain to the delayed (older) samples
        outSamples[0] = delayedL * currentGain
        outSamples[1] = delayedR * currentGain
    }
}
