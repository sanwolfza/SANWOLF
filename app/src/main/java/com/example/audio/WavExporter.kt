package com.example.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.ProjectData
import com.example.model.SynthWaveform
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object WavExporter {

    fun exportProjectToWav(context: Context, project: ProjectData, numBars: Int = 4): File {
        val sampleRate = 44100
        val channels = 2
        val bpm = project.bpm
        val beatsPerBar = project.timeSignatureNumerator
        val totalBeats = numBars * beatsPerBar
        val totalDurationSec = (totalBeats * 60.0) / bpm
        val totalSamples = (totalDurationSec * sampleRate).toInt()

        val pcmData = ShortArray(totalSamples * channels)
        val samplesPerBeat = (sampleRate * 60.0) / bpm
        val samplesPerStep = samplesPerBeat / 4.0

        // Render project tracks into buffer matching live playback 1:1 via InstrumentLibrary definitions
        for (track in project.tracks) {
            if (track.muted) continue

            val stepCount = track.stepCount.coerceAtLeast(1)
            for (stepIdx in 0 until (numBars * 16)) {
                val effectiveStep = stepIdx % stepCount
                if (track.steps.getOrElse(effectiveStep) { false }) {
                    val stepStartSample = (stepIdx * samplesPerStep).toInt()
                    val vel = track.stepVelocities.getOrElse(effectiveStep) { 0.8f } * track.volume
                    renderDrumIntoBuffer(pcmData, stepStartSample, totalSamples, track.name, vel, track.pan, sampleRate)
                }
            }

            for (note in track.notes) {
                val startSample = (note.startBeat * samplesPerBeat).toInt()
                val durationSamples = (note.lengthBeats * samplesPerBeat).toInt()
                renderSynthNoteIntoBuffer(
                    pcmData,
                    startSample,
                    durationSamples,
                    totalSamples,
                    note,
                    track,
                    sampleRate
                )
            }
        }

        applyMasteringToBuffer(pcmData, project)

        val fileName = "${project.title.replace(" ", "_")}_${System.currentTimeMillis()}.wav"
        val outFile = File(context.cacheDir, fileName)

        FileOutputStream(outFile).use { fos ->
            writeWavHeader(fos, totalSamples, channels, sampleRate)
            val byteBuffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) {
                byteBuffer.putShort(s)
            }
            fos.write(byteBuffer.array())
        }

        return outFile
    }

    private fun renderDrumIntoBuffer(
        buffer: ShortArray,
        startSample: Int,
        maxSamples: Int,
        drumName: String,
        velocity: Float,
        pan: Float,
        sampleRate: Int
    ) {
        val definition = InstrumentLibrary.getById(drumName)
        val voice = InstrumentVoices.createDrumVoice(definition.drumType.ifEmpty { definition.id }, velocity, pan)
        var sIdx = startSample
        val panClamped = pan.coerceIn(-1f, 1f)
        val panL = (1f - panClamped) * 0.5f + 0.5f * (if (panClamped <= 0f) 1f else 1f - panClamped)
        val panR = (1f + panClamped) * 0.5f + 0.5f * (if (panClamped >= 0f) 1f else 1f + panClamped)

        while (!voice.isFinished && sIdx < maxSamples) {
            val sample = voice.nextSample()
            val shortVal = (sample * 32767f).toInt().coerceIn(-32768, 32767)
            val curL = buffer[sIdx * 2]
            val curR = buffer[sIdx * 2 + 1]
            buffer[sIdx * 2] = (curL + (shortVal * panL).toInt()).coerceIn(-32768, 32767).toShort()
            buffer[sIdx * 2 + 1] = (curR + (shortVal * panR).toInt()).coerceIn(-32768, 32767).toShort()
            sIdx++
        }
    }

    private fun renderSynthNoteIntoBuffer(
        buffer: ShortArray,
        startSample: Int,
        durationSamples: Int,
        maxSamples: Int,
        note: com.example.model.MidiNote,
        track: com.example.model.TrackData,
        sampleRate: Int
    ) {
        val definition = InstrumentLibrary.getById(track.synthPresetName.ifEmpty { track.name })
        val durationSec = (durationSamples.toFloat() / sampleRate).coerceAtLeast(0.1f)
        val effVol = track.volume * note.velocity
        val voice = InstrumentVoices.createVoiceForDefinition(definition, note.pitch, durationSec, effVol, track.pan)

        var sIdx = startSample
        val panClamped = track.pan.coerceIn(-1f, 1f)
        val panL = (1f - panClamped) * 0.5f + 0.5f * (if (panClamped <= 0f) 1f else 1f - panClamped)
        val panR = (1f + panClamped) * 0.5f + 0.5f * (if (panClamped >= 0f) 1f else 1f + panClamped)

        while (!voice.isFinished && sIdx < maxSamples) {
            val sample = voice.nextSample()
            val shortVal = (sample * 32767f).toInt().coerceIn(-32768, 32767)
            val curL = buffer[sIdx * 2]
            val curR = buffer[sIdx * 2 + 1]
            buffer[sIdx * 2] = (curL + (shortVal * panL).toInt()).coerceIn(-32768, 32767).toShort()
            buffer[sIdx * 2 + 1] = (curR + (shortVal * panR).toInt()).coerceIn(-32768, 32767).toShort()
            sIdx++
        }
    }

    private fun applyMasteringToBuffer(buffer: ShortArray, project: ProjectData) {
        val m = project.masteringConfig
        if (!m.enabled) return

        val limiter = LookAheadLimiter(
            sampleRate = 44100,
            lookAheadMs = 5.0f,
            releaseMs = 80.0f,
            ceilingDb = m.limiterCeilingDb
        )

        val gain = Math.pow(10.0, (m.lowGainDb + m.highGainDb) / 40.0).toFloat() * 1.1f
        val limitedSamples = FloatArray(2)
        val numFrames = buffer.size / 2

        for (i in 0 until numFrames) {
            var sampleL = buffer[i * 2] / 32768f
            var sampleR = buffer[i * 2 + 1] / 32768f

            sampleL *= gain
            sampleR *= gain

            val mid = (sampleL + sampleR) * 0.5f
            val side = (sampleL - sampleR) * 0.5f
            val wideSide = side * m.stereoWidth
            sampleL = mid + wideSide
            sampleR = mid - wideSide

            limiter.process(sampleL, sampleR, limitedSamples)

            buffer[i * 2] = (limitedSamples[0] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            buffer[i * 2 + 1] = (limitedSamples[1] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun writeWavHeader(out: FileOutputStream, totalSamples: Int, channels: Int, sampleRate: Int) {
        val byteRate = sampleRate * channels * 2
        val dataSize = totalSamples * channels * 2
        val totalSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataSize)

        out.write(header.array())
    }

    fun createShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
