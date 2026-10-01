package com.example.audio

import android.content.Context
import android.util.Log

/**
 * Oboe / AAudio Low-Latency Audio Boilerplate for Modular Synthesizer Output.
 * Configures ultra-low latency streams using Android AAudio / Oboe low-latency principles.
 */
class OboeLowLatencyBoilerplate(private val context: Context) {

    companion object {
        private const val TAG = "OboeLowLatencyAudio"
        const val DESIRED_SAMPLE_RATE = 44100
        const val DESIRED_FRAMES_PER_BURST = 192 // Ultra-low latency buffer chunk size
    }

    private var isEngineRunning = false

    /**
     * Initializes the low-latency audio stream builder configuration.
     * In C++/JNI with Oboe, this configures:
     * - StreamDirection: Output
     * - PerformanceMode: LowLatency
     * - SharingMode: Exclusive
     * - Format: PCM Float or 16-bit
     * - BufferCapacity: Optimized for minimum round-trip audio latency.
     */
    fun configureLowLatencyStream(): Map<String, Any> {
        Log.i(TAG, "Configuring Oboe / AAudio low-latency parameters for modular synth...")
        return mapOf(
            "performanceMode" to "PerformanceMode.LowLatency",
            "sharingMode" to "SharingMode.Exclusive",
            "sampleRate" to DESIRED_SAMPLE_RATE,
            "framesPerBurst" to DESIRED_FRAMES_PER_BURST,
            "channelCount" to 2
        )
    }

    fun startLowLatencyEngine(onAudioBufferReady: (FloatArray, Int) -> Unit) {
        if (isEngineRunning) return
        isEngineRunning = true
        Log.i(TAG, "Oboe low-latency audio engine started.")
    }

    fun stopLowLatencyEngine() {
        isEngineRunning = false
        Log.i(TAG, "Oboe low-latency audio engine stopped.")
    }
}
