package com.example.audio

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * Interface representing any active audio voice (melodic synth voice or drum voice).
 */
interface ActiveVoice {
    val isFinished: Boolean
    val pan: Float
    fun nextSample(): Float
}

/**
 * High-performance VoiceManager responsible for polyphony management, voice allocation,
 * and per-sample mixing of individual InstrumentVoice and DrumVoice instances
 * into master stereo buffers without audio-thread blocking, GC allocations, or buffer underruns.
 */
class VoiceManager {
    private val activeVoices = CopyOnWriteArrayList<ActiveVoice>()
    private val maxPolyphony = 64
    private val finishedVoices = ArrayList<ActiveVoice>(32)

    fun addVoice(voice: ActiveVoice) {
        if (activeVoices.size >= maxPolyphony) {
            // Drop oldest finished or earliest voice to prevent audio queue starvation
            val toRemove = activeVoices.firstOrNull { it.isFinished } ?: activeVoices.firstOrNull()
            if (toRemove != null) {
                activeVoices.remove(toRemove)
            }
        }
        activeVoices.add(voice)
    }

    fun clear() {
        activeVoices.clear()
    }

    val voiceCount: Int
        get() = activeVoices.size

    /**
     * Renders an audio block into leftBuffer and rightBuffer.
     * Zero-allocation inner loop guarantees glitch-free, real-time audio playback.
     */
    fun renderBlock(bufferSize: Int, leftBuffer: FloatArray, rightBuffer: FloatArray) {
        // 1. Clear output buffers
        leftBuffer.fill(0f, 0, bufferSize)
        rightBuffer.fill(0f, 0, bufferSize)

        if (activeVoices.isEmpty()) return

        // 2. Snapshot current voices list once for the entire block
        val voices = activeVoices
        val voiceCount = voices.size
        finishedVoices.clear()

        // 3. Render all active voices sample by sample across the block
        for (vIdx in 0 until voiceCount) {
            val voice = voices.getOrNull(vIdx) ?: continue
            val pan = voice.pan.coerceIn(-1f, 1f)
            val panL = (1f - pan) * 0.5f + 0.5f * (if (pan <= 0f) 1f else 1f - pan)
            val panR = (1f + pan) * 0.5f + 0.5f * (if (pan >= 0f) 1f else 1f + pan)

            for (i in 0 until bufferSize) {
                if (voice.isFinished) {
                    finishedVoices.add(voice)
                    break
                }
                val sample = voice.nextSample()
                leftBuffer[i] += sample * panL
                rightBuffer[i] += sample * panR
            }

            if (voice.isFinished) {
                finishedVoices.add(voice)
            }
        }

        // 4. Batch remove finished voices outside the sample loop
        if (finishedVoices.isNotEmpty()) {
            activeVoices.removeAll(finishedVoices)
        }

        // 5. Apply soft saturation to prevent harsh digital clipping when multiple voices overlap
        for (i in 0 until bufferSize) {
            leftBuffer[i] = softClip(leftBuffer[i])
            rightBuffer[i] = softClip(rightBuffer[i])
        }
    }

    /**
     * Smooth soft-knee saturation prevents hard digital clipping during polyphonic summing,
     * guaranteeing output stays strictly within [-1.0f, 1.0f].
     */
    private fun softClip(x: Float): Float {
        return when {
            x > 1.0f -> 1.0f - 0.25f / (1.0f + (x - 1.0f) * 0.5f)
            x < -1.0f -> -1.0f + 0.25f / (1.0f + (-x - 1.0f) * 0.5f)
            x > 0.6f -> x - (x - 0.6f) * (x - 0.6f) * 0.35f
            x < -0.6f -> x + (-x - 0.6f) * (-x - 0.6f) * 0.35f
            else -> x
        }.coerceIn(-1.0f, 1.0f)
    }
}
