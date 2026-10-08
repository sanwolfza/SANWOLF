package com.example.audio

import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Decoded one-shot samples for drum (step-sequencer) tracks, keyed by the track's file URI.
 * Decoding happens off the audio thread ([load]); the audio thread only does [get] lookups.
 */
object PcmSampleCache {
    private const val TAG = "PcmSampleCache"
    /** Longest one-shot we decode for a drum track (seconds at 44.1 kHz). */
    private const val MAX_SECONDS = 12

    private val cache = ConcurrentHashMap<String, FloatArray>()

    fun get(uri: String?): FloatArray? = if (uri.isNullOrBlank()) null else cache[uri]

    /** Decodes [uri] (a file:// or plain path to a PCM WAV) into mono floats at 44.1 kHz. Blocking. */
    fun load(uri: String): FloatArray? {
        cache[uri]?.let { return it }
        val path = if (uri.startsWith("file:")) Uri.parse(uri).path else uri
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) return null
        val pcm = runCatching { decodeWav(file.readBytes()) }
            .onFailure { Log.e(TAG, "Couldn't decode $uri", it) }
            .getOrNull() ?: return null
        cache[uri] = pcm
        return pcm
    }

    /** Minimal RIFF/WAVE reader: 16/24-bit PCM or 32-bit float, any channel count, resampled to 44.1 kHz. */
    fun decodeWav(bytes: ByteArray): FloatArray? {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 12 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        var pos = 12
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var dataOff = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = bb.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt ") {
                format = bb.getShort(body).toInt() and 0xFFFF
                channels = bb.getShort(body + 2).toInt()
                rate = bb.getInt(body + 4)
                bits = bb.getShort(body + 14).toInt()
                if (format == 0xFFFE && len >= 26) format = bb.getShort(body + 24).toInt() and 0xFFFF
            } else if (id == "data") {
                dataOff = body
                dataLen = minOf(len.coerceAtLeast(0), bytes.size - body)
                break
            }
            if (len < 0) return null
            pos = body + len + (len and 1)
        }
        if (dataOff < 0 || channels <= 0 || rate <= 0) return null
        val bytesPerSample = bits / 8
        if (bytesPerSample <= 0) return null
        val frameBytes = bytesPerSample * channels
        var frames = dataLen / frameBytes
        frames = minOf(frames, rate * MAX_SECONDS)
        val mono = FloatArray(frames)
        for (f in 0 until frames) {
            var acc = 0f
            for (c in 0 until channels) {
                val o = dataOff + f * frameBytes + c * bytesPerSample
                acc += when {
                    format == 1 && bits == 16 -> bb.getShort(o) / 32768f
                    format == 1 && bits == 24 -> {
                        val v = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8) or (bytes[o + 2].toInt() shl 16)
                        v / 8388608f
                    }
                    format == 3 && bits == 32 -> bb.getFloat(o)
                    format == 1 && bits == 8 -> ((bytes[o].toInt() and 0xFF) - 128) / 128f
                    else -> return null
                }
            }
            mono[f] = acc / channels
        }
        if (rate == SanwolfAudioEngine.SAMPLE_RATE) return mono
        // Linear resample to the engine rate.
        val ratio = rate.toDouble() / SanwolfAudioEngine.SAMPLE_RATE
        val outLen = (frames / ratio).toInt()
        return FloatArray(outLen) { i ->
            val src = i * ratio
            val i0 = src.toInt().coerceAtMost(frames - 1)
            val i1 = (i0 + 1).coerceAtMost(frames - 1)
            val frac = (src - i0).toFloat()
            mono[i0] * (1f - frac) + mono[i1] * frac
        }
    }
}

/** Plays a decoded mono sample once (drum-track one-shots). Allocation-free while rendering. */
class SampleVoice(
    private val data: FloatArray,
    private val velocity: Float,
    override val pan: Float
) : ActiveVoice {
    private var index = 0
    override val isFinished: Boolean get() = index >= data.size
    override fun nextSample(): Float {
        if (index >= data.size) return 0f
        return data[index++] * velocity
    }
}
