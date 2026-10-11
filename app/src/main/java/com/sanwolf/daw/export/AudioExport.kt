package com.sanwolf.daw.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/** Audio formats the Export dialog offers. All render at 44.1 kHz stereo. */
enum class ExportAudioFormat(
    val label: String,
    val detail: String,
    val extension: String,
    val mime: String
) {
    WAV_24("WAV 24-bit", "Lossless • 44.1 kHz • distributor master", "wav", "audio/wav"),
    WAV_16("WAV 16-bit", "Lossless • 44.1 kHz • CD quality", "wav", "audio/wav"),
    FLAC("FLAC", "Lossless, smaller files • 16-bit • 44.1 kHz", "flac", "audio/flac"),
    AAC_256("AAC (M4A) 256 kbps", "Built-in encoder • good for phones", "m4a", "audio/mp4"),
    MP3_320("MP3 320 kbps", "Quick sharing • best MP3 quality", "mp3", "audio/mpeg"),
    MP3_256("MP3 256 kbps", "Quick sharing", "mp3", "audio/mpeg"),
    MP3_192("MP3 192 kbps", "Quick sharing • smaller", "mp3", "audio/mpeg");

    val isLossless: Boolean get() = this == WAV_24 || this == WAV_16 || this == FLAC
}

/** Title / artist written into the file's tags (WAV LIST/INFO, FLAC Vorbis comment, MP3 ID3v2). */
data class ExportTags(val title: String, val artist: String = "SANWOLF")

/**
 * Encodes a rendered master (interleaved stereo float, -1..1) into the chosen format, then
 * publishes it to Music/SANWOLF and/or the share sheet.
 *
 * - WAV 16/24: written here (16-bit with TPDF dither).
 * - FLAC: Android's MediaCodec "audio/flac" encoder; the .flac stream (fLaC marker, STREAMINFO,
 *   Vorbis comment, frames) is assembled here because MediaMuxer can't write FLAC. Hidden when
 *   the device has no FLAC encoder.
 * - AAC: MediaCodec AAC-LC + MediaMuxer (MP4/M4A).
 * - MP3: [Mp3Encoders] (jump3r, a Java port of LAME).
 *
 * Everything is blocking: run on a background dispatcher.
 */
object AudioExport {
    private const val TAG = "AudioExport"
    const val SAMPLE_RATE = 44100
    private const val CHANNELS = 2
    private const val TIMEOUT_US = 10_000L

    /** Formats this device can produce, in display order. */
    fun availableFormats(): List<ExportAudioFormat> = ExportAudioFormat.values().filter { isAvailable(it) }

    fun isAvailable(format: ExportAudioFormat): Boolean = when (format) {
        ExportAudioFormat.WAV_24, ExportAudioFormat.WAV_16 -> true
        ExportAudioFormat.FLAC -> hasEncoder(flacFormat())
        ExportAudioFormat.AAC_256 -> hasEncoder(aacFormat())
        ExportAudioFormat.MP3_320, ExportAudioFormat.MP3_256, ExportAudioFormat.MP3_192 -> Mp3Encoders.isAvailable()
    }

    /**
     * Encodes [pcm] into cacheDir/exports/<baseName>.<ext>. [onProgress] gets 0..1.
     */
    fun encode(
        context: Context,
        pcm: FloatArray,
        format: ExportAudioFormat,
        tags: ExportTags,
        baseName: String,
        onProgress: (Float) -> Unit = {}
    ): File {
        val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val out = File(dir, "$baseName.${format.extension}")
        if (out.exists()) out.delete()
        when (format) {
            ExportAudioFormat.WAV_24 -> writeWav(out, pcm, 24, tags, onProgress)
            ExportAudioFormat.WAV_16 -> writeWav(out, pcm, 16, tags, onProgress)
            ExportAudioFormat.FLAC -> encodeFlac(out, pcm, tags, onProgress)
            ExportAudioFormat.AAC_256 -> encodeAac(out, pcm, 256_000, onProgress)
            ExportAudioFormat.MP3_320 -> Mp3Encoders.encode(pcm, SAMPLE_RATE, 320, tags, out, onProgress)
            ExportAudioFormat.MP3_256 -> Mp3Encoders.encode(pcm, SAMPLE_RATE, 256, tags, out, onProgress)
            ExportAudioFormat.MP3_192 -> Mp3Encoders.encode(pcm, SAMPLE_RATE, 192, tags, out, onProgress)
        }
        onProgress(1f)
        return out
    }

    // ------------------------------------------------------------------ publish / share

    /**
     * Copies [file] into the shared Music/SANWOLF folder. API 29+: MediaStore, no permission.
     * Older devices: the app's own Music/SANWOLF folder (no storage permission is declared).
     * Returns a human-readable location.
     */
    fun saveToMusic(context: Context, file: File, mime: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SANWOLF")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw IllegalStateException("Couldn't create the file in Music")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IllegalStateException("Couldn't write to Music")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            return "Music/SANWOLF/${file.name}"
        } else {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music")
            val dir = File(base, "SANWOLF").apply { if (!exists()) mkdirs() }
            val dest = File(dir, file.name)
            file.copyTo(dest, overwrite = true)
            return dest.absolutePath
        }
    }

    fun shareIntent(context: Context, file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ------------------------------------------------------------------ WAV

    private fun writeWav(out: File, pcm: FloatArray, bits: Int, tags: ExportTags, onProgress: (Float) -> Unit) {
        val bytesPerSample = bits / 8
        val frames = pcm.size / CHANNELS
        val dataSize = frames.toLong() * CHANNELS * bytesPerSample
        val info = buildInfoChunk(tags)
        val riffSize = 4 + (8 + 16) + info.size + (8 + dataSize)

        BufferedOutputStream(FileOutputStream(out), 1 shl 16).use { os ->
            val h = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray(Charsets.US_ASCII))
            h.putInt(riffSize.toInt())
            h.put("WAVE".toByteArray(Charsets.US_ASCII))
            h.put("fmt ".toByteArray(Charsets.US_ASCII))
            h.putInt(16)
            h.putShort(1) // PCM
            h.putShort(CHANNELS.toShort())
            h.putInt(SAMPLE_RATE)
            h.putInt(SAMPLE_RATE * CHANNELS * bytesPerSample)
            h.putShort((CHANNELS * bytesPerSample).toShort())
            h.putShort(bits.toShort())
            os.write(h.array())
            os.write(info)
            val dh = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            dh.put("data".toByteArray(Charsets.US_ASCII))
            dh.putInt(dataSize.toInt())
            os.write(dh.array())

            val chunkSamples = 8192 * CHANNELS
            val buf = ByteArray(chunkSamples * bytesPerSample)
            var i = 0
            while (i < pcm.size) {
                val n = minOf(chunkSamples, pcm.size - i)
                var b = 0
                for (k in 0 until n) {
                    val v = pcm[i + k]
                    if (bits == 24) {
                        val s = (v * 8388607f).toInt().coerceIn(-8388608, 8388607)
                        buf[b] = (s and 0xFF).toByte()
                        buf[b + 1] = ((s shr 8) and 0xFF).toByte()
                        buf[b + 2] = ((s shr 16) and 0xFF).toByte()
                        b += 3
                    } else {
                        val s = to16(v)
                        buf[b] = (s and 0xFF).toByte()
                        buf[b + 1] = ((s shr 8) and 0xFF).toByte()
                        b += 2
                    }
                }
                os.write(buf, 0, b)
                i += n
                onProgress(i.toFloat() / pcm.size)
            }
        }
    }

    /** LIST/INFO chunk: INAM (title), IART (artist), ISFT (software). */
    private fun buildInfoChunk(tags: ExportTags): ByteArray {
        fun sub(id: String, text: String): ByteArray {
            val bytes = text.toByteArray(Charsets.UTF_8) + 0.toByte()
            val padded = if (bytes.size % 2 == 1) bytes + 0.toByte() else bytes
            val bb = ByteBuffer.allocate(8 + padded.size).order(ByteOrder.LITTLE_ENDIAN)
            bb.put(id.toByteArray(Charsets.US_ASCII))
            bb.putInt(bytes.size)
            bb.put(padded)
            return bb.array()
        }
        val body = "INFO".toByteArray(Charsets.US_ASCII) +
            sub("INAM", tags.title.ifBlank { "Untitled" }) +
            sub("IART", tags.artist.ifBlank { "SANWOLF" }) +
            sub("ISFT", "SANWOLF DAW")
        val bb = ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("LIST".toByteArray(Charsets.US_ASCII))
        bb.putInt(body.size)
        bb.put(body)
        return bb.array()
    }

    /** Float -> 16-bit with TPDF dither (±1 LSB). */
    private fun to16(v: Float): Int {
        val dither = (Random.nextFloat() - Random.nextFloat()) / 32768f
        return Math.round((v + dither) * 32767f).coerceIn(-32768, 32767)
    }

    // ------------------------------------------------------------------ MediaCodec (FLAC / AAC)

    private fun flacFormat(): MediaFormat =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

    private fun aacFormat(bitRate: Int = 256_000): MediaFormat =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

    private fun hasEncoder(format: MediaFormat): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) != null
    }.getOrDefault(false)

    private fun createEncoder(format: MediaFormat): MediaCodec {
        val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
            ?: throw IllegalStateException("This device has no ${format.getString(MediaFormat.KEY_MIME)} encoder")
        val codec = MediaCodec.createByCodecName(name)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return codec
    }

    /**
     * Feeds [pcm] (as 16-bit PCM) through [codec] and hands every output buffer to [onOutput].
     * [onFormat] is called on INFO_OUTPUT_FORMAT_CHANGED.
     */
    private fun pump(
        codec: MediaCodec,
        pcm: FloatArray,
        onProgress: (Float) -> Unit,
        onFormat: (MediaFormat) -> Unit,
        onOutput: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val info = MediaCodec.BufferInfo()
        var inputPos = 0 // index into pcm (samples, interleaved)
        var inputDone = false
        var outputDone = false
        var idleRounds = 0
        codec.start()
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx) ?: throw IllegalStateException("No input buffer")
                        inBuf.clear()
                        inBuf.order(ByteOrder.LITTLE_ENDIAN)
                        val ptsUs = (inputPos / CHANNELS).toLong() * 1_000_000L / SAMPLE_RATE
                        val maxSamples = (inBuf.capacity() / 2 / CHANNELS) * CHANNELS
                        val n = minOf(maxSamples, pcm.size - inputPos)
                        if (n <= 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            for (k in 0 until n) inBuf.putShort(to16(pcm[inputPos + k]).toShort())
                            codec.queueInputBuffer(inIdx, 0, n * 2, ptsUs, 0)
                            inputPos += n
                            onProgress(inputPos.toFloat() / pcm.size * 0.98f)
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(codec.outputFormat)
                    outIdx >= 0 -> {
                        idleRounds = 0
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            onOutput(outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                    else -> {
                        if (inputDone && ++idleRounds > 2000) throw IllegalStateException("Encoder stalled")
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private fun encodeAac(out: File, pcm: FloatArray, bitRate: Int, onProgress: (Float) -> Unit) {
        val codec = createEncoder(aacFormat(bitRate))
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        try {
            pump(codec, pcm, onProgress,
                onFormat = { fmt ->
                    if (!started) {
                        track = muxer.addTrack(fmt)
                        muxer.start()
                        started = true
                    }
                },
                onOutput = { buf, info ->
                    // Codec config (csd) is already in the track format
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && started) {
                        muxer.writeSampleData(track, buf, info)
                    }
                }
            )
            if (!started) throw IllegalStateException("AAC encoder produced no output")
        } finally {
            runCatching { if (started) muxer.stop() }.onFailure { Log.e(TAG, "Muxer stop failed", it) }
            runCatching { muxer.release() }
        }
    }

    private fun encodeFlac(out: File, pcm: FloatArray, tags: ExportTags, onProgress: (Float) -> Unit) {
        val codec = createEncoder(flacFormat())
        val totalFrames = (pcm.size / CHANNELS).toLong()
        BufferedOutputStream(FileOutputStream(out), 1 shl 16).use { os ->
            var headerWritten = false
            var csdFromFormat: ByteArray? = null
            val chunk = ByteArray(64 * 1024)

            fun writeHeader(csd: ByteArray) {
                os.write(FlacHeader.build(csd, totalFrames, tags))
                headerWritten = true
            }

            pump(codec, pcm, onProgress,
                onFormat = { fmt ->
                    val bb = fmt.getByteBuffer("csd-0")
                    if (bb != null) {
                        val dup = bb.duplicate()
                        dup.rewind()
                        csdFromFormat = ByteArray(dup.remaining()).also { dup.get(it) }
                    }
                },
                onOutput = { buf, info ->
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        if (!headerWritten) {
                            val csd = ByteArray(buf.remaining()).also { buf.get(it) }
                            writeHeader(csd)
                        }
                    } else {
                        if (!headerWritten) {
                            val csd = csdFromFormat ?: throw IllegalStateException("FLAC encoder gave no stream header")
                            writeHeader(csd)
                        }
                        while (buf.hasRemaining()) {
                            val n = minOf(chunk.size, buf.remaining())
                            buf.get(chunk, 0, n)
                            os.write(chunk, 0, n)
                        }
                    }
                }
            )
            if (!headerWritten) throw IllegalStateException("FLAC encoder produced no output")
        }
    }
}

/**
 * Builds the metadata part of a .flac file from the encoder's codec-specific data:
 * "fLaC", STREAMINFO (total sample count filled in), VORBIS_COMMENT (title/artist).
 */
internal object FlacHeader {
    private const val TYPE_STREAMINFO = 0
    private const val TYPE_VORBIS_COMMENT = 4

    fun build(csd: ByteArray, totalFrames: Long, tags: ExportTags): ByteArray {
        val streamInfo = findStreamInfo(csd) ?: throw IllegalStateException("FLAC header without STREAMINFO")
        // Total samples: 36 bits starting at bit 108 of STREAMINFO (low nibble of byte 13 + bytes 14..17)
        val total = totalFrames.coerceIn(0L, (1L shl 36) - 1)
        streamInfo[13] = ((streamInfo[13].toInt() and 0xF0) or ((total shr 32).toInt() and 0x0F)).toByte()
        streamInfo[14] = ((total shr 24) and 0xFF).toByte()
        streamInfo[15] = ((total shr 16) and 0xFF).toByte()
        streamInfo[16] = ((total shr 8) and 0xFF).toByte()
        streamInfo[17] = (total and 0xFF).toByte()

        val comment = vorbisComment(tags)
        val out = java.io.ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        out.write(blockHeader(false, TYPE_STREAMINFO, streamInfo.size))
        out.write(streamInfo)
        out.write(blockHeader(true, TYPE_VORBIS_COMMENT, comment.size))
        out.write(comment)
        return out.toByteArray()
    }

    /** Returns a copy of the 34-byte STREAMINFO body from any of the csd layouts encoders use. */
    private fun findStreamInfo(csd: ByteArray): ByteArray? {
        if (csd.size == 34) return csd.copyOf()
        var pos = if (csd.size >= 4 && csd[0] == 'f'.code.toByte() && csd[1] == 'L'.code.toByte() &&
            csd[2] == 'a'.code.toByte() && csd[3] == 'C'.code.toByte()) 4 else 0
        while (pos + 4 <= csd.size) {
            val type = csd[pos].toInt() and 0x7F
            val last = (csd[pos].toInt() and 0x80) != 0
            val len = ((csd[pos + 1].toInt() and 0xFF) shl 16) or ((csd[pos + 2].toInt() and 0xFF) shl 8) or (csd[pos + 3].toInt() and 0xFF)
            val start = pos + 4
            if (start + len > csd.size) return null
            if (type == TYPE_STREAMINFO && len >= 34) return csd.copyOfRange(start, start + 34)
            if (last) break
            pos = start + len
        }
        return null
    }

    private fun blockHeader(last: Boolean, type: Int, length: Int): ByteArray = byteArrayOf(
        ((if (last) 0x80 else 0) or (type and 0x7F)).toByte(),
        ((length shr 16) and 0xFF).toByte(),
        ((length shr 8) and 0xFF).toByte(),
        (length and 0xFF).toByte()
    )

    private fun vorbisComment(tags: ExportTags): ByteArray {
        val vendor = "SANWOLF DAW".toByteArray(Charsets.UTF_8)
        val fields = listOf(
            "TITLE=" + tags.title.ifBlank { "Untitled" },
            "ARTIST=" + tags.artist.ifBlank { "SANWOLF" },
            "ENCODER=SANWOLF DAW"
        ).map { it.toByteArray(Charsets.UTF_8) }
        val size = 4 + vendor.size + 4 + fields.sumOf { 4 + it.size }
        val bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(vendor.size)
        bb.put(vendor)
        bb.putInt(fields.size)
        fields.forEach { bb.putInt(it.size); bb.put(it) }
        return bb.array()
    }
}
