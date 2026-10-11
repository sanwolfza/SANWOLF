package com.sanwolf.daw.audio

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Interface representing any active audio voice (melodic synth voice or drum voice).
 */
interface ActiveVoice {
    val isFinished: Boolean
    val pan: Float
    fun nextSample(): Float
}

/**
 * VoiceManager responsible for polyphony management, voice allocation,
 * and per-sample mixing of individual InstrumentVoice and DrumVoice instances
 * into master stereo buffers.
 *
 * Threading model:
 *  - [addVoice] and [clear] may be called from any thread (UI, sequencer). They only post to a
 *    lock-free queue / flag, so they never block the audio thread and never copy arrays.
 *  - [renderBlock] must only be called from the single audio thread, which owns the voice array.
 *    Rendering does not allocate.
 */
class VoiceManager {
    private val maxPolyphony = 64

    // Owned by the audio thread only
    private val voices = arrayOfNulls<ActiveVoice>(maxPolyphony)
    private val panL = FloatArray(maxPolyphony)
    private val panR = FloatArray(maxPolyphony)
    /** Track id whose insert rack this voice goes through (null = straight to the main mix). */
    private val busOf = arrayOfNulls<String>(maxPolyphony)
    private var count = 0

    private class PendingVoice(val voice: ActiveVoice, val bus: String?)

    // Cross-thread hand-off
    private val pending = ConcurrentLinkedQueue<PendingVoice>()
    @Volatile
    private var clearRequested = false
    @Volatile
    private var publishedCount = 0

    /** Queues a voice. [bus] is the owning track's id, so a track insert rack can process it. */
    fun addVoice(voice: ActiveVoice, bus: String? = null) {
        pending.add(PendingVoice(voice, bus))
    }

    fun clear() {
        clearRequested = true
    }

    /** Number of voices currently sounding (as of the last rendered block) plus queued ones. */
    val voiceCount: Int
        get() = publishedCount + pending.size

    /** True when nothing is sounding and nothing is queued. Cheap; safe from any thread. */
    fun isIdle(): Boolean = publishedCount == 0 && pending.isEmpty()

    private fun removeAt(index: Int) {
        // Keep start order (oldest first) so polyphony stealing keeps dropping the earliest voice
        for (j in index until count - 1) {
            voices[j] = voices[j + 1]
            panL[j] = panL[j + 1]
            panR[j] = panR[j + 1]
            busOf[j] = busOf[j + 1]
        }
        count--
        voices[count] = null
        busOf[count] = null
    }

    private fun drainPending() {
        if (clearRequested) {
            clearRequested = false
            for (j in 0 until count) { voices[j] = null; busOf[j] = null }
            count = 0
            // Anything queued before the clear request is discarded too
            pending.clear()
            return
        }
        while (true) {
            val item = pending.poll() ?: break
            val voice = item.voice
            if (count >= maxPolyphony) {
                // Drop oldest finished or earliest voice to prevent audio queue starvation
                var victim = 0
                for (j in 0 until count) {
                    if (voices[j]?.isFinished == true) { victim = j; break }
                }
                removeAt(victim)
            }
            val pan = voice.pan.coerceIn(-1f, 1f)
            panL[count] = (1f - pan) * 0.5f + 0.5f * (if (pan <= 0f) 1f else 1f - pan)
            panR[count] = (1f + pan) * 0.5f + 0.5f * (if (pan >= 0f) 1f else 1f + pan)
            voices[count] = voice
            busOf[count] = item.bus
            count++
        }
    }

    /**
     * Renders an audio block into leftBuffer and rightBuffer, starting at [offset].
     * Audio thread only. Returns true if any voice produced output in this block.
     */
    fun renderBlock(
        bufferSize: Int,
        leftBuffer: FloatArray,
        rightBuffer: FloatArray,
        offset: Int = 0,
        buses: FxBusSet? = null,
        softClipMain: Boolean = true
    ): Boolean {
        val end = offset + bufferSize
        // 1. Clear output buffers (main mix and any per-track rack buses)
        leftBuffer.fill(0f, offset, end)
        rightBuffer.fill(0f, offset, end)
        if (buses != null) {
            for (b in 0 until buses.count) {
                buses.left[b].fill(0f, offset, end)
                buses.right[b].fill(0f, offset, end)
            }
        }

        drainPending()
        if (count == 0) {
            publishedCount = 0
            return false
        }

        // 2. Render all active voices sample by sample across the block
        var v = 0
        while (v < count) {
            val voice = voices[v]!!
            val gl = panL[v]
            val gr = panR[v]
            // Voices of a track with an insert rack render into that track's bus
            val busIndex = if (buses != null) buses.indexOf(busOf[v]) else -1
            val outL = if (busIndex >= 0) buses!!.left[busIndex] else leftBuffer
            val outR = if (busIndex >= 0) buses!!.right[busIndex] else rightBuffer
            var i = offset
            while (i < end) {
                if (voice.isFinished) break
                val sample = voice.nextSample()
                outL[i] += sample * gl
                outR[i] += sample * gr
                i++
            }
            if (voice.isFinished) {
                // 3. Remove finished voices outside the sample loop
                removeAt(v)
            } else {
                v++
            }
        }
        publishedCount = count

        // 4. Apply soft saturation to prevent harsh digital clipping when multiple voices overlap.
        // With rack buses the engine clips after summing the processed buses instead.
        if (softClipMain) for (i in offset until end) {
            leftBuffer[i] = softClip(leftBuffer[i])
            rightBuffer[i] = softClip(rightBuffer[i])
        }
        return true
    }

    /**
     * Smooth soft-knee saturation prevents hard digital clipping during polyphonic summing,
     * guaranteeing output stays strictly within [-1.0f, 1.0f].
     */
    fun softClip(x: Float): Float {
        val y = when {
            x > 1.0f -> 1.0f - 0.25f / (1.0f + (x - 1.0f) * 0.5f)
            x < -1.0f -> -1.0f + 0.25f / (1.0f + (-x - 1.0f) * 0.5f)
            x > 0.6f -> x - (x - 0.6f) * (x - 0.6f) * 0.35f
            x < -0.6f -> x + (-x - 0.6f) * (-x - 0.6f) * 0.35f
            else -> return x
        }
        return if (y > 1.0f) 1.0f else if (y < -1.0f) -1.0f else y
    }
}
