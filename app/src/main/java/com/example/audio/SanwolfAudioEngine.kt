package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.example.model.ProjectData
import com.example.model.SynthWaveform
import com.example.model.TrackData
import com.example.model.TrackType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Enterprise Audio Engine utilizing dedicated VoiceManager, individual InstrumentVoice classes
 * (Piano, Bass, Lead, Pad, Organ, Pluck), and independent DrumVoice engines.
 *
 * Rendering runs on a dedicated real-time thread (THREAD_PRIORITY_URGENT_AUDIO) that pushes
 * 16-bit stereo PCM into a streaming AudioTrack. Everything inside the per-sample loops is
 * allocation-free; per-block parameters (volume, mastering gains, limiter ceiling, tempo)
 * are read once per block.
 */
class SanwolfAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "SanwolfAudioEngine"
        /** Engine/synthesis rate. All voices (and the WAV exporter) are built for this rate. */
        const val SAMPLE_RATE = 44100
        /** Frames rendered and written per AudioTrack.write(). */
        const val BUFFER_SIZE = 1024
        const val NUM_CHANNELS = 2 // Stereo
        /** Sequencer resolution inside a block (frames). Keeps step timing within ~3 ms. */
        private const val SEQ_CHUNK = 128
        private const val BYTES_PER_FRAME = NUM_CHANNELS * 2 // 16-bit PCM
    }

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    @Volatile
    private var running = false
    @Volatile
    private var released = false
    private val lastTriggerTimeMap = ConcurrentHashMap<Int, Long>()

    @Volatile
    var isPlaying = false
        private set

    @Volatile
    var currentBeat = 0.0

    @Volatile
    var currentStep = 0

    // Master volume and mixing bus
    @Volatile
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
    @Volatile
    var currentProject: ProjectData? = null

    // Metronome (quarter-note click while playing; accent on the first beat of each bar)
    @Volatile
    var metronomeEnabled = false
    @Volatile
    var metronomeVolume = 0.5f

    init {
        initAudioTrack()
    }

    private fun createAudioTrack(): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // At least 4 render blocks (~93 ms) and at least 2x the device minimum, so a GC pause or
        // a heavy block does not starve the mixer.
        val bufferSize = maxOf(
            if (minBufferSize > 0) minBufferSize * 2 else 0,
            BUFFER_SIZE * BYTES_PER_FRAME * 4
        )

        val track = AudioTrack.Builder()
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

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("AudioTrack failed to initialise")
        }
        val nativeRate = runCatching { AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) }.getOrDefault(-1)
        Log.i(
            TAG,
            "AudioTrack ready: engine=${SAMPLE_RATE}Hz track=${track.sampleRate}Hz device=${nativeRate}Hz " +
                "minBuf=${minBufferSize}B buf=${bufferSize}B (${bufferSize / BYTES_PER_FRAME} frames)"
        )
        track.play()
        return track
    }

    private fun initAudioTrack() {
        try {
            audioTrack = createAudioTrack()
            startAudioThread()
        } catch (e: Exception) {
            Log.e(TAG, "Audio init failed", e)
        }
    }

    private fun startAudioThread() {
        if (running) return
        running = true
        audioThread = Thread({ audioLoop() }, "SanwolfAudio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun audioLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (e: Exception) {
            Log.w(TAG, "Could not raise audio thread priority", e)
        }

        val audioBuffer = ShortArray(BUFFER_SIZE * NUM_CHANNELS)
        val silence = ShortArray(BUFFER_SIZE * NUM_CHANNELS)
        val leftBlock = FloatArray(BUFFER_SIZE)
        val rightBlock = FloatArray(BUFFER_SIZE)
        val limited = FloatArray(2)
        val gonioStride = BUFFER_SIZE / 64
        val bandSize = BUFFER_SIZE / 8

        // Cached mastering tone gain (pow() only when the dB settings change)
        var cachedLowDb = Float.NaN
        var cachedHighDb = Float.NaN
        var cachedToneGain = 1f

        // Once the mix has been silent long enough to flush the limiter, idle blocks skip DSP
        var outputSilent = false
        var lastMaxSteps = 32

        while (running) {
            val track = audioTrack ?: break
            try {
            val proj = currentProject
            val playing = isPlaying && proj != null

            if (!playing && outputSilent && voiceManager.isIdle()) {
                // Nothing is sounding: hand the device silence and let the meters fall
                decayMeters()
                writeFully(track, silence)
                continue
            }

            // ---- Per-block parameters (hoisted out of the sample loop) ----
            val bpm = (proj?.bpm ?: 120).coerceAtLeast(1)
            val samplesPerBeat = (SAMPLE_RATE * 60.0) / bpm
            val vol = (proj?.masterVolume ?: masterVolume).coerceIn(0f, 1.5f)
            val mConfig = proj?.masteringConfig
            val masteringOn = mConfig != null && mConfig.enabled
            var toneGain = 1f
            var width = 1f
            if (masteringOn && mConfig != null) {
                val lowDb = mConfig.lowGainDb
                val highDb = mConfig.highGainDb
                if (lowDb != cachedLowDb || highDb != cachedHighDb) {
                    cachedLowDb = lowDb
                    cachedHighDb = highDb
                    val lowBoost = 10.0.pow(lowDb / 40.0).toFloat()
                    val highBoost = 10.0.pow(highDb / 40.0).toFloat()
                    cachedToneGain = (lowBoost + highBoost) * 0.5f
                }
                toneGain = cachedToneGain
                width = mConfig.stereoWidth
                lookAheadLimiter.ceilingDb = mConfig.limiterCeilingDb
            } else {
                lookAheadLimiter.ceilingDb = -0.3f
            }
            val preGain = vol * toneGain

            // ---- Sequencer + voice rendering, in small chunks for tight step timing ----
            var anyVoiceOutput = false
            var maxSteps = 32
            var loopBeats = 8.0
            if (playing && proj != null) {
                maxSteps = try {
                    val tracks = proj.tracks
                    if (tracks.isEmpty()) {
                        32
                    } else {
                        var m = Int.MIN_VALUE
                        for (t in tracks) if (t.stepCount > m) m = t.stepCount
                        m.coerceIn(16, 64)
                    }
                } catch (e: Exception) {
                    // Track list being edited concurrently on the UI thread: keep last value
                    lastMaxSteps
                }
                lastMaxSteps = maxSteps
                loopBeats = (maxSteps / 4.0).coerceAtLeast(4.0)
            }
            var offset = 0
            while (offset < BUFFER_SIZE) {
                val n = minOf(SEQ_CHUNK, BUFFER_SIZE - offset)
                if (playing && proj != null && isPlaying) {
                    val prevStep = currentStep
                    val nextBeat = currentBeat + (n / samplesPerBeat)
                    currentBeat = nextBeat % loopBeats
                    currentStep = ((currentBeat * 4).toInt()) % maxSteps
                    if (currentStep != prevStep) {
                        try {
                            triggerSequencerEvents(proj, currentStep, currentBeat)
                        } catch (e: Exception) {
                            // The UI thread may be editing the project; never let that kill audio
                            Log.w(TAG, "Sequencer step skipped", e)
                        }
                    }
                }
                if (voiceManager.renderBlock(n, leftBlock, rightBlock, offset)) anyVoiceOutput = true
                offset += n
            }

            // ---- Master bus: volume, mastering, limiter, meters, PCM ----
            var maxLeft = 0f
            var maxRight = 0f
            var sumLR = 0f
            var sumLL = 0f
            var sumRR = 0f
            var goniometerSampleIdx = 0
            var gonioCountdown = 0

            for (i in 0 until BUFFER_SIZE) {
                var masterL = leftBlock[i] * preGain
                var masterR = rightBlock[i] * preGain

                if (masteringOn) {
                    val mid = (masterL + masterR) * 0.5f
                    val wideSide = (masterL - masterR) * 0.5f * width
                    masterL = mid + wideSide
                    masterR = mid - wideSide
                }

                lookAheadLimiter.process(masterL, masterR, limited)
                val sampleL = limited[0]
                val sampleR = limited[1]

                sumLR += sampleL * sampleR
                sumLL += sampleL * sampleL
                sumRR += sampleR * sampleR

                if (gonioCountdown == 0) {
                    if (goniometerSampleIdx < 64) {
                        goniometerPoints[goniometerSampleIdx * 2] = (sampleL - sampleR) * 0.7071f
                        goniometerPoints[goniometerSampleIdx * 2 + 1] = (sampleL + sampleR) * 0.7071f
                        goniometerSampleIdx++
                    }
                    gonioCountdown = gonioStride
                }
                gonioCountdown--

                val aL = abs(sampleL)
                val aR = abs(sampleR)
                if (aL > maxLeft) maxLeft = aL
                if (aR > maxRight) maxRight = aR

                var pcmL = (sampleL * 32767f).toInt()
                var pcmR = (sampleR * 32767f).toInt()
                if (pcmL > 32767) pcmL = 32767 else if (pcmL < -32768) pcmL = -32768
                if (pcmR > 32767) pcmR = 32767 else if (pcmR < -32768) pcmR = -32768
                audioBuffer[i * 2] = pcmL.toShort()
                audioBuffer[i * 2 + 1] = pcmR.toShort()
            }

            val denom = sqrt(sumLL * sumRR)
            val rawCorr = if (denom > 1e-6f) sumLR / denom else 1.0f
            phaseCorrelation = phaseCorrelation * 0.7f + rawCorr * 0.3f

            for (b in 0 until 8) {
                var bandSum = 0f
                var idx = b * bandSize * 2
                val endIdx = (b + 1) * bandSize * 2
                while (idx < endIdx) {
                    bandSum += abs(audioBuffer[idx].toFloat() / 32768f)
                    idx += 2
                }
                frequencyBands[b] = (frequencyBands[b] * 0.5f + (bandSum / bandSize * 10f) * 0.5f).coerceIn(0f, 1f)
            }

            currentPeakLeft = currentPeakLeft * 0.7f + maxLeft * 0.3f
            currentPeakRight = currentPeakRight * 0.7f + maxRight * 0.3f

            // A full block (longer than the 5 ms look-ahead) of silence in and out means the
            // limiter's delay line is empty; from then on idle blocks can skip all DSP.
            val blockSilent = !anyVoiceOutput && maxLeft == 0f && maxRight == 0f
            if (blockSilent && !outputSilent) {
                lookAheadLimiter.reset()
            }
            outputSilent = blockSilent

            writeFully(track, audioBuffer)
            } catch (e: Exception) {
                // Never let one bad block kill the audio thread
                Log.e(TAG, "Audio block failed", e)
                outputSilent = false
                writeFully(track, silence)
            }
        }
    }

    /** Meter ballistics for idle blocks, matching what a silent rendered block would produce. */
    private fun decayMeters() {
        currentPeakLeft *= 0.7f
        currentPeakRight *= 0.7f
        phaseCorrelation = phaseCorrelation * 0.7f + 0.3f
        for (b in 0 until 8) frequencyBands[b] *= 0.5f
    }

    /**
     * Blocking write of a whole block. Returns false (after recovering) if the device rejected it,
     * e.g. after an audio route change killed the track.
     */
    private fun writeFully(track: AudioTrack, data: ShortArray): Boolean {
        var written = 0
        while (written < data.size && running) {
            val r = track.write(data, written, data.size - written)
            if (r < 0) {
                Log.w(TAG, "AudioTrack.write failed: $r")
                if (r == AudioTrack.ERROR_DEAD_OBJECT && running) {
                    recreateAudioTrack(track)
                } else {
                    SystemClock.sleep(10)
                }
                return false
            }
            if (r == 0) {
                // Track paused/stopped: avoid a busy spin
                SystemClock.sleep(5)
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return false
            }
            written += r
        }
        return true
    }

    private fun recreateAudioTrack(dead: AudioTrack) {
        runCatching { dead.release() }
        audioTrack = try {
            createAudioTrack()
        } catch (e: Exception) {
            Log.e(TAG, "Could not recreate AudioTrack", e)
            SystemClock.sleep(200)
            null
        }
        if (audioTrack == null) running = false
    }

    private fun triggerSequencerEvents(proj: ProjectData, step: Int, beat: Double) {
        if (metronomeEnabled && step % 4 == 0) {
            val beatsPerBar = proj.timeSignatureNumerator.coerceAtLeast(1)
            playMetronomeClick(accent = (step / 4) % beatsPerBar == 0)
        }
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

    /** One metronome / count-in click. Safe to call from the UI thread. */
    fun playMetronomeClick(accent: Boolean) {
        val vel = (metronomeVolume * if (accent) 1.0f else 0.7f).coerceIn(0f, 1f)
        if (vel <= 0f || released) return
        voiceManager.addVoice(InstrumentVoices.createDrumVoice(if (accent) "rim click" else "closed hihat", vel, 0f))
    }

    /** Releases every imported-audio stem player (used when a different project is loaded). */
    fun clearStems() {
        stemPlayers.values.forEach { runCatching { it.release() } }
        stemPlayers.clear()
    }

    fun play() {
        if (!running && !released) initAudioTrack()
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
        released = true
        isPlaying = false
        running = false
        val track = audioTrack
        // Unblock a pending write, then wait for the audio thread to exit before releasing
        track?.runCatching {
            pause()
            flush()
        }
        runCatching { audioThread?.join(500) }
        audioThread = null
        track?.runCatching {
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
