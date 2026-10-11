package com.sanwolf.daw.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteOrder

/**
 * Decodes an audio file (WAV, MP3, AAC/M4A, OGG… whatever the device's MediaCodec supports)
 * into interleaved stereo floats at [SanwolfAudioEngine.SAMPLE_RATE]. Used by the offline WAV
 * export to mix audio clips (imported stems, mic takes, factory loops). Blocking: call off the
 * main thread.
 */
object StemDecoder {
    private const val TAG = "StemDecoder"
    private const val TIMEOUT_US = 10_000L

    /**
     * Returns interleaved L/R floats at 44.1 kHz, at most [maxFrames] frames long, or null when
     * the file can't be read.
     */
    fun decodeStereo(context: Context, uri: Uri, maxFrames: Int): FloatArray? {
        if (maxFrames <= 0) return FloatArray(0)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var isFloat = false

            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, null, null, 0)
            dec.start()

            // Source-rate stereo frames (L, R interleaved), grown as needed
            var src = FloatArray(1 shl 16)
            var srcFrames = 0
            val maxSrcFrames = (maxFrames.toLong() * srcRate / SanwolfAudioEngine.SAMPLE_RATE + 2).toInt()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = dec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = dec.getInputBuffer(inIndex)
                        val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (size < 0) {
                            dec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = dec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = dec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) srcChannels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (outIndex >= 0) {
                    val outBuf = dec.getOutputBuffer(outIndex)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.order(ByteOrder.nativeOrder())
                        val bytesPerSample = if (isFloat) 4 else 2
                        val frames = info.size / (bytesPerSample * srcChannels)
                        if ((srcFrames + frames) * 2 > src.size) {
                            var newSize = src.size * 2
                            while (newSize < (srcFrames + frames) * 2) newSize *= 2
                            src = src.copyOf(newSize)
                        }
                        val floats = if (isFloat) outBuf.asFloatBuffer() else null
                        val shorts = if (isFloat) null else outBuf.asShortBuffer()
                        for (f in 0 until frames) {
                            var l = 0f
                            var r = 0f
                            for (c in 0 until srcChannels) {
                                val v = if (floats != null) floats.get() else shorts!!.get() / 32768f
                                if (c == 0) l = v else if (c == 1) r = v
                            }
                            if (srcChannels == 1) r = l
                            src[(srcFrames + f) * 2] = l
                            src[(srcFrames + f) * 2 + 1] = r
                        }
                        srcFrames += frames
                    }
                    dec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || srcFrames >= maxSrcFrames) {
                        outputDone = true
                    }
                }
            }

            return resampleStereo(src, srcFrames, srcRate, maxFrames)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't decode $uri", e)
            return null
        } finally {
            codec?.let { c ->
                runCatching { c.stop() }.onFailure { Log.w(TAG, "codec.stop failed", it) }
                runCatching { c.release() }.onFailure { Log.w(TAG, "codec.release failed", it) }
            }
            runCatching { extractor.release() }.onFailure { Log.w(TAG, "extractor.release failed", it) }
        }
    }

    /** Linear resample of interleaved stereo [src] (first [frames] frames) to the engine rate. */
    private fun resampleStereo(src: FloatArray, frames: Int, srcRate: Int, maxFrames: Int): FloatArray {
        if (frames <= 0) return FloatArray(0)
        val target = SanwolfAudioEngine.SAMPLE_RATE
        if (srcRate == target || srcRate <= 0) {
            val n = minOf(frames, maxFrames)
            return src.copyOf(n * 2)
        }
        val ratio = srcRate.toDouble() / target
        val outFrames = minOf((frames / ratio).toInt(), maxFrames)
        val out = FloatArray(outFrames * 2)
        for (i in 0 until outFrames) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceAtMost(frames - 1)
            val i1 = (i0 + 1).coerceAtMost(frames - 1)
            val frac = (pos - i0).toFloat()
            out[i * 2] = src[i0 * 2] * (1f - frac) + src[i1 * 2] * frac
            out[i * 2 + 1] = src[i0 * 2 + 1] * (1f - frac) + src[i1 * 2 + 1] * frac
        }
        return out
    }
}
