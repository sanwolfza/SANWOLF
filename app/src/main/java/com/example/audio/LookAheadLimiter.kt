package com.example.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.exp

class LookAheadLimiter(
    val sampleRate: Int = 44100,
    val lookAheadMs: Float = 5.0f, // 5ms look-ahead
    val releaseMs: Float = 80.0f,
    var ceilingDb: Float = -0.3f   // Output ceiling in dB
) {
    private val lookAheadSamples = max(1, (lookAheadMs * sampleRate / 1000f).toInt())
    
    // Ring buffers for look-ahead delay (stereo)
    private val delayBufferL = FloatArray(lookAheadSamples)
    private val delayBufferR = FloatArray(lookAheadSamples)
    private var writeIndex = 0
    
    // Envelope / gain reduction state
    private var currentGain = 1.0f
    
    private val ceilingLinear: Float
        get() = 10.0f.pow(ceilingDb / 20.0f)

    // Release coefficient: gain rises back to 1.0
    private val releaseCoeff = exp(-1.0 / (releaseMs * sampleRate / 1000.0)).toFloat()

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
        writeIndex = (writeIndex + 1) % lookAheadSamples
        
        // 3. Peak detection over the look-ahead window
        var futurePeak = 0.0f
        for (i in 0 until lookAheadSamples) {
            val absL = abs(delayBufferL[i])
            val absR = abs(delayBufferR[i])
            if (absL > futurePeak) futurePeak = absL
            if (absR > futurePeak) futurePeak = absR
        }
        
        // 4. Calculate required gain to stay below ceiling
        val targetGain = if (futurePeak > ceilingLinear) {
            ceilingLinear / futurePeak
        } else {
            1.0f
        }
        
        // 5. Instant attack / smooth release
        if (targetGain < currentGain) {
            // Instant attack: decrease gain immediately (which acts ahead of time for the delayed signal)
            currentGain = targetGain
        } else {
            // Exponential release back towards 1.0
            currentGain = currentGain * releaseCoeff + targetGain * (1.0f - releaseCoeff)
        }
        
        // 6. Apply gain to the delayed (older) samples
        outSamples[0] = delayedL * currentGain
        outSamples[1] = delayedR * currentGain
    }
}
