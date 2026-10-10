package com.sanwolf.daw.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.IOException
import java.nio.ByteOrder

/** Decoded mono 16-bit PCM at [sampleRate] Hz. */
class DecodedPcm(val data: ShortArray, val sampleRate: Int)

/**
 * Decodes a compressed audio asset (Ogg Vorbis, MP3, AAC… anything the device's MediaCodec
 * supports) into mono 16-bit PCM. Same MediaExtractor + MediaCodec loop as [StemDecoder], but
 * mono, kept at the file's own sample rate (the playing voice folds the rate into its pitch
 * ratio) and stored as shorts to halve memory. Blocking: call off the main and audio threads.
 *
 * Works whether or not the asset is compressed inside the APK: it tries
 * [android.content.res.AssetManager.openFd] first (stored assets) and falls back to reading the
 * bytes into memory and feeding them through a [MediaDataSource] (deflated assets).
 */
object AssetPcmDecoder {
    private const val TAG = "AssetPcmDecoder"
    private const val TIMEOUT_US = 10_000L
    /** Gives up after this many consecutive dequeue rounds that produced nothing (stuck codec). */
    private const val MAX_IDLE_ROUNDS = 2000

    fun decodeMono(context: Context, assetPath: String, maxSeconds: Float): DecodedPcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (!setAssetSource(context, extractor, assetPath)) return null
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

            var out = ShortArray(1 shl 16)
            var frames = 0
            val maxFrames = (maxSeconds * srcRate).toInt().coerceAtLeast(1)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleRounds = 0

            while (!outputDone) {
                var progressed = false
                if (!inputDone) {
                    val inIndex = dec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        progressed = true
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
                    progressed = true
                    val of = dec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) srcChannels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (outIndex >= 0) {
                    progressed = true
                    val outBuf = dec.getOutputBuffer(outIndex)
                    if (outBuf != null && info.size > 0 && frames < maxFrames) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.order(ByteOrder.nativeOrder())
                        val bytesPerSample = if (isFloat) 4 else 2
                        val chunkFrames = minOf(info.size / (bytesPerSample * srcChannels), maxFrames - frames)
                        if (frames + chunkFrames > out.size) {
                            var newSize = out.size * 2
                            while (newSize < frames + chunkFrames) newSize *= 2
                            out = out.copyOf(newSize)
                        }
                        val floats = if (isFloat) outBuf.asFloatBuffer() else null
                        val shorts = if (isFloat) null else outBuf.asShortBuffer()
                        for (f in 0 until chunkFrames) {
                            var acc = 0f
                            for (c in 0 until srcChannels) {
                                acc += if (floats != null) floats.get() else shorts!!.get() / 32768f
                            }
                            val v = (acc / srcChannels * 32767f).toInt().coerceIn(-32768, 32767)
                            out[frames + f] = v.toShort()
                        }
                        frames += chunkFrames
                    }
                    dec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || frames >= maxFrames) {
                        outputDone = true
                    }
                }
                if (progressed) {
                    idleRounds = 0
                } else if (++idleRounds > MAX_IDLE_ROUNDS) {
                    Log.w(TAG, "Decoder stalled on $assetPath after $frames frames; keeping what was decoded")
                    break
                }
            }
            if (frames <= 0) return null
            return DecodedPcm(out.copyOf(frames), if (srcRate > 0) srcRate else SanwolfAudioEngine.SAMPLE_RATE)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't decode asset $assetPath", e)
            return null
        } finally {
            codec?.let { c ->
                runCatching { c.stop() }.onFailure { Log.w(TAG, "codec.stop failed", it) }
                runCatching { c.release() }.onFailure { Log.w(TAG, "codec.release failed", it) }
            }
            runCatching { extractor.release() }.onFailure { Log.w(TAG, "extractor.release failed", it) }
        }
    }

    /** Points [extractor] at the asset: by file descriptor when stored, from memory when deflated. */
    private fun setAssetSource(context: Context, extractor: MediaExtractor, assetPath: String): Boolean {
        try {
            context.assets.openFd(assetPath).use { afd ->
                // MediaExtractor dups the descriptor, so closing the AssetFileDescriptor afterwards is safe.
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            return true
        } catch (e: IOException) {
            // Compressed in the APK ("probably compressed"): read it whole instead.
        }
        val bytes = try {
            context.assets.open(assetPath).use { it.readBytes() }
        } catch (e: IOException) {
            Log.e(TAG, "Asset not found: $assetPath", e)
            return false
        }
        extractor.setDataSource(ByteArrayMediaDataSource(bytes))
        return true
    }

    private class ByteArrayMediaDataSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val n = minOf(size.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, n)
            return n
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() {}
    }
}
