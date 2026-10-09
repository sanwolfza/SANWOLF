package com.example.studio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records the microphone as 16-bit PCM, 44.1 kHz, mono and writes a standard WAV file.
 *
 * Capture runs on a dedicated background thread. The WAV header is written up-front with
 * a zero length and patched with the real sizes when recording stops.
 *
 * The caller is responsible for holding the RECORD_AUDIO permission before calling [start].
 */
class RecordingEngine(private val context: Context) {

    companion object {
        private const val TAG = "RecordingEngine"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_COUNT = 1
        const val BITS_PER_SAMPLE = 16
        private const val WAV_HEADER_SIZE = 44
        private const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8
    }

    private val running = AtomicBoolean(false)
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var captureThread: Thread? = null
    @Volatile private var currentFile: File? = null
    @Volatile private var bytesWritten: Long = 0L
    @Volatile private var captureError: Throwable? = null

    val isRecording: Boolean
        get() = running.get()

    /** Creates a new, unique output file at filesDir/recordings/rec_<timestamp>.wav. */
    fun createOutputFile(): File {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "rec_${System.currentTimeMillis()}.wav")
    }

    /** Starts recording into [outputFile]. Returns the file on success. */
    @SuppressLint("MissingPermission")
    fun start(outputFile: File = createOutputFile()): Result<File> = runCatching {
        check(!running.get()) { "Already recording" }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "This device's microphone does not support 44.1 kHz recording" }
        val bufferSize = maxOf(minBuffer, BYTES_PER_SECOND / 10) // at least ~100 ms

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("Could not open the microphone")
        }

        val raf = try {
            RandomAccessFile(outputFile, "rw").apply {
                setLength(0)
                write(buildWavHeader(0L))
            }
        } catch (t: Throwable) {
            recorder.release()
            throw t
        }

        try {
            recorder.startRecording()
        } catch (t: Throwable) {
            recorder.release()
            runCatching { raf.close() }
            outputFile.delete()
            throw t
        }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.release()
            runCatching { raf.close() }
            outputFile.delete()
            error("The microphone is busy or unavailable")
        }

        audioRecord = recorder
        currentFile = outputFile
        bytesWritten = 0L
        captureError = null
        running.set(true)

        captureThread = Thread({
            val buffer = ByteArray(bufferSize)
            try {
                while (running.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        raf.write(buffer, 0, read)
                        bytesWritten += read
                    } else if (read < 0) {
                        captureError = IllegalStateException("Microphone read failed (code $read)")
                        break
                    }
                }
            } catch (t: Throwable) {
                captureError = t
                Log.e(TAG, "Capture failed", t)
            } finally {
                runCatching {
                    raf.seek(0)
                    raf.write(buildWavHeader(bytesWritten))
                }.onFailure { Log.e(TAG, "Failed to finalise WAV header", it) }
                runCatching { raf.close() }
            }
        }, "SanwolfMicRecorder").apply { start() }

        outputFile
    }

    /** Stops recording and returns the finished WAV file, or a failure if nothing usable was captured. */
    fun stop(): Result<File> = runCatching {
        check(running.getAndSet(false)) { "Not recording" }
        val recorder: AudioRecord = audioRecord ?: error("Not recording")
        val file: File = currentFile ?: error("Not recording")

        runCatching { recorder.stop() }
        runCatching { captureThread?.join(3000) }
        runCatching { recorder.release() }
        audioRecord = null
        captureThread = null
        currentFile = null

        captureError?.let { throw it }
        if (bytesWritten <= 0L) {
            file.delete()
            error("No audio was captured")
        }
        file
    }

    /** Duration in milliseconds of a WAV file written by this engine. */
    fun durationMs(file: File): Long {
        val dataBytes = (file.length() - WAV_HEADER_SIZE).coerceAtLeast(0L)
        return dataBytes * 1000L / BYTES_PER_SECOND
    }

    /** Stops any in-progress recording and frees the microphone. Safe to call repeatedly. */
    fun release() {
        if (running.get()) {
            stop()
        } else {
            runCatching { audioRecord?.release() }
            audioRecord = null
        }
    }

    private fun buildWavHeader(dataSize: Long): ByteArray {
        val byteRate = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNEL_COUNT * BITS_PER_SAMPLE / 8
        val clampedData = dataSize.coerceIn(0L, 0xFFFFFFFFL - 36L)
        return ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36L + clampedData).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM fmt chunk size
            putShort(1.toShort()) // audio format = PCM
            putShort(CHANNEL_COUNT.toShort())
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(clampedData.toInt())
        }.array()
    }
}
