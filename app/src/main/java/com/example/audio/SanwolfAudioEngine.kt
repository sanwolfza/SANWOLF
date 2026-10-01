package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import com.example.model.ProjectData
import com.example.model.SynthWaveform
import com.example.model.TrackData
import com.example.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Enterprise Audio Engine utilizing dedicated VoiceManager, individual InstrumentVoice classes
 * (Piano, Bass, Lead, Pad, Organ, Pluck), and independent DrumVoice engines.
 */
class SanwolfAudioEngine(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val BUFFER_SIZE = 1024
        const val NUM_CHANNELS = 2 // Stereo
    }

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val lastTriggerTimeMap = ConcurrentHashMap<Int, Long>()

    @Volatile
    var isPlaying = false
        private set

    @Volatile
    var currentBeat = 0.0

    @Volatile
    var currentStep = 0

    // Master volume and mixing bus
    var masterVolume = 0.85f
    @Volatile
    var currentPeakLeft = 0f
    @Volatile
    var currentPeakRight = 0f
    val frequencyBands = FloatArray(8)
    @Volatile
    var phaseCorrelation = 1.0f
    val goniometerPoints = FloatArray(128)

    // Master limiter and VoiceManager
    private val lookAheadLimiter = LookAheadLimiter(SAMPLE_RATE, 5.0f, 80.0f, -0.3f)
    val voiceManager = VoiceManager()

    // Media players for imported audio stem playback
    private val stemPlayers = ConcurrentHashMap<String, MediaPlayer>()

    // Current project reference
    var currentProject: ProjectData? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize, BUFFER_SIZE * NUM_CHANNELS * 2 * 4)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            startAudioProcessingLoop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAudioProcessingLoop() {
        synthJob?.cancel()
        synthJob = scope.launch {
            val audioBuffer = ShortArray(BUFFER_SIZE * NUM_CHANNELS)
            val leftBlock = FloatArray(BUFFER_SIZE)
            val rightBlock = FloatArray(BUFFER_SIZE)

            while (isActive) {
                val proj = currentProject
                val bpm = proj?.bpm ?: 120
                val samplesPerBeat = (SAMPLE_RATE * 60.0) / bpm

                // Sequencer advance logic
                if (isPlaying && proj != null) {
                    val prevStep = currentStep
                    val maxSteps = proj.tracks.maxOfOrNull { it.stepCount }?.coerceIn(16, 64) ?: 32
                    val loopBeats = (maxSteps / 4.0).coerceAtLeast(4.0)
                    val nextBeat = currentBeat + (BUFFER_SIZE.toDouble() / samplesPerBeat)
                    currentBeat = nextBeat % loopBeats
                    currentStep = ((currentBeat * 4).toInt()) % maxSteps

                    if (currentStep != prevStep) {
                        triggerSequencerEvents(proj, currentStep, currentBeat)
                    }
                }

                // Render block through VoiceManager
                voiceManager.renderBlock(BUFFER_SIZE, leftBlock, rightBlock)

                var maxLeft = 0f
                var maxRight = 0f
                var sumLR = 0f
                var sumLL = 0f
                var sumRR = 0f
                var goniometerSampleIdx = 0

                for (i in 0 until BUFFER_SIZE) {
                    var sampleL = leftBlock[i]
                    var sampleR = rightBlock[i]

                    // Apply master volume & Pro Mastering chain / look-ahead limiter
                    val vol = (proj?.masterVolume ?: masterVolume).coerceIn(0f, 1.5f)
                    var masterL = sampleL * vol
                    var masterR = sampleR * vol

                    val mConfig = proj?.masteringConfig
                    if (mConfig != null && mConfig.enabled) {
                        val lowBoost = Math.pow(10.0, mConfig.lowGainDb / 40.0).toFloat()
                        val highBoost = Math.pow(10.0, mConfig.highGainDb / 40.0).toFloat()
                        masterL = (masterL * lowBoost + masterL * highBoost) * 0.5f
                        masterR = (masterR * lowBoost + masterR * highBoost) * 0.5f

                        val mid = (masterL + masterR) * 0.5f
                        val side = (masterL - masterR) * 0.5f
                        val wideSide = side * mConfig.stereoWidth
                        masterL = mid + wideSide
                        masterR = mid - wideSide

                        lookAheadLimiter.ceilingDb = mConfig.limiterCeilingDb
                    } else {
                        lookAheadLimiter.ceilingDb = -0.3f
                    }

                    val limited = FloatArray(2)
                    lookAheadLimiter.process(masterL, masterR, limited)
                    sampleL = limited[0]
                    sampleR = limited[1]

                    sumLR += sampleL * sampleR
                    sumLL += sampleL * sampleL
                    sumRR += sampleR * sampleR

                    if (i % (BUFFER_SIZE / 64) == 0 && goniometerSampleIdx < 64) {
                        goniometerPoints[goniometerSampleIdx * 2] = (sampleL - sampleR) * 0.7071f
                        goniometerPoints[goniometerSampleIdx * 2 + 1] = (sampleL + sampleR) * 0.7071f
                        goniometerSampleIdx++
                    }

                    maxLeft = maxOf(maxLeft, kotlin.math.abs(sampleL))
                    maxRight = maxOf(maxRight, kotlin.math.abs(sampleR))

                    val pcmL = (sampleL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                    val pcmR = (sampleR * 32767f).toInt().coerceIn(-32768, 32767).toShort()

                    audioBuffer[i * 2] = pcmL
                    audioBuffer[i * 2 + 1] = pcmR
                }

                val denom = sqrt(sumLL * sumRR)
                val rawCorr = if (denom > 1e-6f) sumLR / denom else 1.0f
                phaseCorrelation = phaseCorrelation * 0.7f + rawCorr * 0.3f

                for (b in 0 until 8) {
                    var bandSum = 0f
                    val startIdx = (b * BUFFER_SIZE / 8) * 2
                    val endIdx = ((b + 1) * BUFFER_SIZE / 8) * 2
                    for (idx in startIdx until endIdx step 2) {
                        bandSum += kotlin.math.abs(audioBuffer[idx].toFloat() / 32768f)
                    }
                    frequencyBands[b] = (frequencyBands[b] * 0.5f + (bandSum / (BUFFER_SIZE / 8) * 10f) * 0.5f).coerceIn(0f, 1f)
                }

                currentPeakLeft = currentPeakLeft * 0.7f + maxLeft * 0.3f
                currentPeakRight = currentPeakRight * 0.7f + maxRight * 0.3f

                audioTrack?.write(audioBuffer, 0, audioBuffer.size)
            }
        }
    }

    private fun triggerSequencerEvents(proj: ProjectData, step: Int, beat: Double) {
        val anySolo = proj.tracks.any { it.solo }

        for (track in proj.tracks) {
            if (track.muted || (anySolo && !track.solo)) continue

            val effectiveStep = step % track.stepCount.coerceAtLeast(1)
            if (track.steps.getOrElse(effectiveStep) { false }) {
                val vel = track.stepVelocities.getOrElse(effectiveStep) { 0.8f } * track.volume
                if (track.type == TrackType.DRUM_MACHINE) {
                    val drumKey = "${track.synthPresetName} ${track.name}"
                    triggerDrumSound(drumKey, vel, track.pan)
                } else {
                    triggerTrackNote(track, 60, 0.25f, vel)
                }
            }

            for (note in track.notes) {
                if (kotlin.math.abs(note.startBeat - (beat % 16.0)) < 0.125) {
                    var effectiveVolume = track.volume
                    var effectivePan = track.pan
                    var effectiveCutoff = track.filterCutoffHz
                    var effectiveResonance = track.filterResonance
                    var effectiveAttack = track.attackMs
                    var effectiveRelease = track.releaseMs

                    for (lane in track.automationLanes) {
                        if (lane.points.isEmpty()) continue
                        val normVal = lane.getValueAtBeat(note.startBeat)
                        when {
                            lane.targetParam.contains("Cutoff", ignoreCase = true) -> effectiveCutoff = 100f + normVal * 11900f
                            lane.targetParam.contains("Resonance", ignoreCase = true) -> effectiveResonance = 0.5f + normVal * 3.5f
                            lane.targetParam.contains("Volume", ignoreCase = true) -> effectiveVolume = track.volume * normVal
                            lane.targetParam.contains("Pan", ignoreCase = true) -> effectivePan = (normVal - 0.5f) * 2f
                            lane.targetParam.contains("Attack", ignoreCase = true) -> effectiveAttack = 2f + normVal * 498f
                            lane.targetParam.contains("Release", ignoreCase = true) -> effectiveRelease = 20f + normVal * 1480f
                        }
                    }

                    triggerTrackNote(
                        track = track,
                        pitch = note.pitch,
                        durationSec = (note.lengthBeats * 60.0 / proj.bpm).toFloat(),
                        velocity = note.velocity * effectiveVolume,
                        pan = effectivePan,
                        cutoff = effectiveCutoff,
                        resonance = effectiveResonance,
                        attackMs = effectiveAttack,
                        releaseMs = effectiveRelease
                    )
                }
            }
        }
    }

    fun triggerTrackNote(
        track: TrackData?,
        pitch: Int,
        durationSec: Float = 0.4f,
        velocity: Float = 0.85f,
        pan: Float = 0f,
        cutoff: Float = 2500f,
        resonance: Float = 1.2f,
        attackMs: Float = 15f,
        releaseMs: Float = 180f
    ) {
        val finalVol = (track?.volume ?: 1.0f) * velocity
        val finalPan = if (pan != 0f) pan else (track?.pan ?: 0f)

        // Route drum machine tracks directly to percussion engine so they never play as synth notes
        if (track?.type == TrackType.DRUM_MACHINE) {
            val drumKey = track.synthPresetName.ifEmpty { track.name }
            triggerDrumSound(drumKey, finalVol, finalPan)
            return
        }

        // Authoritative resolution via InstrumentLibrary
        val instrumentQuery = when {
            !track?.synthPresetName.isNullOrEmpty() -> track!!.synthPresetName
            !track?.name.isNullOrEmpty() -> track!!.name
            else -> "keys.grand.piano"
        }
        val def = InstrumentLibrary.getById(instrumentQuery)

        val voice: ActiveVoice = InstrumentVoices.createVoiceForDefinition(
            definition = def,
            pitch = pitch,
            durationSec = durationSec,
            velocity = finalVol,
            pan = finalPan
        )

        voiceManager.addVoice(voice)
    }

    fun triggerMidiNote(pitch: Int, track: TrackData? = null, velocity: Float = 0.85f) {
        val now = SystemClock.uptimeMillis()
        val lastTrigger = lastTriggerTimeMap[pitch] ?: 0L
        if (now - lastTrigger < 100L) {
            return
        }
        lastTriggerTimeMap[pitch] = now
        triggerTrackNote(track, pitch, 0.5f, velocity)
    }

    fun triggerSynthNote(
        frequency: Float,
        durationSec: Float = 0.4f,
        waveform: SynthWaveform = SynthWaveform.SAWTOOTH,
        velocity: Float = 0.8f,
        pan: Float = 0f,
        cutoff: Float = 2500f,
        resonance: Float = 1.2f,
        attackMs: Float = 15f,
        decayMs: Float = 120f,
        sustainLevel: Float = 0.65f,
        releaseMs: Float = 180f,
        presetName: String = "",
        category: String = ""
    ) {
        val dummyTrack = TrackData(
            name = presetName,
            type = TrackType.SYNTH,
            colorHex = 0xFF00E5FF,
            synthPresetName = presetName,
            synthPresetCategory = category,
            synthWaveform = waveform,
            decayMs = decayMs,
            sustainLevel = sustainLevel
        )
        triggerTrackNote(dummyTrack, freqToMidi(frequency), durationSec, velocity, pan, cutoff, resonance, attackMs, releaseMs)
    }

    fun triggerDrumSound(
        drumName: String,
        velocity: Float = 0.8f,
        pan: Float = 0f,
        definition: InstrumentDefinition? = null
    ) {
        val def = definition ?: InstrumentLibrary.getById(drumName)
        val drumVoice: ActiveVoice = InstrumentVoices.createDrumVoice(def.drumType.ifEmpty { def.id }, velocity, pan)
        voiceManager.addVoice(drumVoice)
    }

    fun play() {
        isPlaying = true
        stemPlayers.values.forEach { if (!it.isPlaying) runCatching { it.start() } }
    }

    fun pause() {
        isPlaying = false
        stemPlayers.values.forEach { if (it.isPlaying) runCatching { it.pause() } }
    }

    fun stop() {
        isPlaying = false
        currentBeat = 0.0
        currentStep = 0
        voiceManager.clear()
        stemPlayers.values.forEach {
            runCatching {
                it.pause()
                it.seekTo(0)
            }
        }
    }

    fun seekToBeat(beat: Double) {
        currentBeat = beat
        currentStep = (beat * 4).toInt()
        val proj = currentProject ?: return
        val ms = ((beat * 60.0 / proj.bpm) * 1000).toInt()
        stemPlayers.values.forEach { runCatching { it.seekTo(ms) } }
    }

    fun addStemAudio(trackId: String, uri: Uri) {
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                prepare()
            }
            stemPlayers[trackId]?.release()
            stemPlayers[trackId] = mp
        }
    }

    fun updateStemMuteSolo(proj: ProjectData) {
        val anySolo = proj.tracks.any { it.solo }
        for (track in proj.tracks) {
            val player = stemPlayers[track.id] ?: continue
            val isSilenced = track.muted || (anySolo && !track.solo)
            val effectiveVol = if (isSilenced) 0f else (track.volume * proj.masterVolume).coerceIn(0f, 1f)
            runCatching {
                player.setVolume(effectiveVol, effectiveVol)
            }
        }
    }

    fun release() {
        isPlaying = false
        synthJob?.cancel()
        audioTrack?.runCatching {
            stop()
            release()
        }
        audioTrack = null
        stemPlayers.values.forEach { runCatching { it.release() } }
        stemPlayers.clear()
        voiceManager.clear()
    }

    private fun midiToFreq(midiNote: Int): Float {
        return (440.0 * 2.0.pow((midiNote - 69) / 12.0)).toFloat()
    }

    private fun freqToMidi(freq: Float): Int {
        return (69 + 12 * kotlin.math.ln(freq / 440.0) / kotlin.math.ln(2.0)).toInt().coerceIn(0, 127)
    }
}
