package com.sanwolf.daw.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.sanwolf.daw.model.ProjectData
import com.sanwolf.daw.model.SynthWaveform
import com.sanwolf.daw.model.TrackType
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object WavExporter {
    private const val TAG = "WavExporter"

    /** Integrated loudness (ITU-R BS.1770, gated) of the most recent export, in LUFS. */
    @Volatile
    var lastExportIntegratedLufs: Float = LoudnessMeter.SILENCE_LUFS
        private set

    fun exportProjectToWav(context: Context, project: ProjectData, numBars: Int = 4): File {
        MultisampleLibrary.init(context)
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
        val anySolo = project.tracks.any { it.solo }
        for (track in project.tracks) {
            if (track.muted || (anySolo && !track.solo)) continue

            // Audio clips (imported stems, mic takes, factory loops) start at their arranger position
            if (track.type == TrackType.AUDIO_IMPORT) {
                renderAudioClipIntoBuffer(context, pcmData, totalSamples, track, samplesPerBeat)
                continue
            }

            val stepCount = track.stepCount.coerceAtLeast(1)
            // Patterns start at their arranger position and loop from there
            val startStep = (track.patternStartBeat * 4.0 + 0.5).toInt().coerceAtLeast(0)
            for (stepIdx in 0 until (numBars * 16)) {
                val rel = stepIdx - startStep
                if (rel < 0) continue
                val effectiveStep = rel % stepCount
                if (track.steps.getOrElse(effectiveStep) { false }) {
                    val stepStartSample = (stepIdx * samplesPerStep).toInt()
                    val vel = track.stepVelocities.getOrElse(effectiveStep) { 0.8f } * track.volume
                    val samplePcm = if (track.type == TrackType.DRUM_MACHINE) {
                        track.audioUri?.let { PcmSampleCache.get(it) ?: PcmSampleCache.load(it) }
                    } else null
                    renderDrumIntoBuffer(
                        pcmData, stepStartSample, totalSamples, track.synthPresetName.ifEmpty { track.name },
                        vel, track.pan, sampleRate, samplePcm
                    )
                }
            }

            for (note in track.notes) {
                if (!track.isNoteInClip(note)) continue // cropped by the clip's right edge
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
        lastExportIntegratedLufs = measureIntegratedLufs(pcmData)
        Log.i(TAG, "Export integrated loudness: %.1f LUFS".format(lastExportIntegratedLufs))

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

    /** Mixes an audio track's file into [buffer] starting at its clip position (beats). */
    private fun renderAudioClipIntoBuffer(
        context: Context,
        buffer: ShortArray,
        maxSamples: Int,
        track: com.sanwolf.daw.model.TrackData,
        samplesPerBeat: Double
    ) {
        val uriStr = track.audioUri
        if (uriStr.isNullOrBlank()) return
        val startFrame = (track.audioStartBeat.coerceAtLeast(0.0) * samplesPerBeat).toInt()
        val framesAvailable = maxSamples - startFrame
        if (framesAvailable <= 0) return
        val stereo = StemDecoder.decodeStereo(context, Uri.parse(uriStr), framesAvailable)
        if (stereo == null) {
            Log.w(TAG, "Skipping audio clip '${track.name}': couldn't decode $uriStr")
            return
        }
        val gain = track.volume * track.inputGain
        val panClamped = track.pan.coerceIn(-1f, 1f)
        val gainL = gain * (if (panClamped > 0f) 1f - panClamped else 1f)
        val gainR = gain * (if (panClamped < 0f) 1f + panClamped else 1f)
        val frames = stereo.size / 2
        for (f in 0 until frames) {
            val idx = startFrame + f
            if (idx >= maxSamples) break
            val l = buffer[idx * 2] + (stereo[f * 2] * gainL * 32767f).toInt()
            val r = buffer[idx * 2 + 1] + (stereo[f * 2 + 1] * gainR * 32767f).toInt()
            buffer[idx * 2] = l.coerceIn(-32768, 32767).toShort()
            buffer[idx * 2 + 1] = r.coerceIn(-32768, 32767).toShort()
        }
    }

    private fun renderDrumIntoBuffer(
        buffer: ShortArray,
        startSample: Int,
        maxSamples: Int,
        drumName: String,
        velocity: Float,
        pan: Float,
        sampleRate: Int,
        samplePcm: FloatArray? = null
    ) {
        val voice: ActiveVoice = if (samplePcm != null) {
            SampleVoice(samplePcm, velocity, pan)
        } else {
            val definition = InstrumentLibrary.getById(drumName)
            InstrumentVoices.createDrumVoice(definition.drumType.ifEmpty { definition.id }, velocity, pan)
        }
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
        note: com.sanwolf.daw.model.MidiNote,
        track: com.sanwolf.daw.model.TrackData,
        sampleRate: Int
    ) {
        val instrumentName = track.synthPresetName.ifEmpty { track.name }
        val durationSec = (durationSamples.toFloat() / sampleRate).coerceAtLeast(0.1f)
        val effVol = track.volume * note.velocity
        val voice: ActiveVoice = if (MultisampleLibrary.isMultisample(instrumentName)) {
            // Same voice as live playback; offline we can wait for the decode.
            val inst = MultisampleLibrary.loadNow(instrumentName)
            val msVoice = inst?.createVoice(note.pitch, durationSec, effVol, track.pan)
            if (msVoice == null) {
                Log.w(TAG, "Skipping note on '${track.name}': $instrumentName couldn't be decoded")
                return
            }
            msVoice
        } else {
            val definition = InstrumentLibrary.getById(instrumentName)
            InstrumentVoices.createVoiceForDefinition(definition, note.pitch, durationSec, effVol, track.pan)
        }

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

    /** Gated integrated loudness (BS.1770) of an interleaved stereo 16-bit buffer. */
    fun measureIntegratedLufs(buffer: ShortArray): Float {
        val meter = LoudnessMeter(44100, trackIntegrated = true)
        val frames = buffer.size / 2
        for (i in 0 until frames) {
            meter.process(buffer[i * 2] / 32768f, buffer[i * 2 + 1] / 32768f)
        }
        return meter.integratedLufs()
    }

    /** Runs the same master bus as live playback (see [MasterBusProcessor]) over the render. */
    private fun applyMasteringToBuffer(buffer: ShortArray, project: ProjectData) {
        val bus = MasterBusProcessor(44100)
        bus.configure(project.masteringConfig, project.masterVolume)
        val out = FloatArray(2)
        val numFrames = buffer.size / 2
        for (i in 0 until numFrames) {
            bus.process(buffer[i * 2] / 32768f, buffer[i * 2 + 1] / 32768f, out)
            buffer[i * 2] = (out[0] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            buffer[i * 2 + 1] = (out[1] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
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
