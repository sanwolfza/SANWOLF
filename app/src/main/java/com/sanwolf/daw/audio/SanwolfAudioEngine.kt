package com.sanwolf.daw.audio

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
import com.sanwolf.daw.model.ProjectData
import com.sanwolf.daw.model.SynthWaveform
import com.sanwolf.daw.model.TrackData
import com.sanwolf.daw.model.TrackType
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
        /** Longest song loop (beats): matches the arranger's 16-bar timeline. */
        const val MAX_LOOP_BEATS = 64.0
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
    // Master bus (EQ, glue compressor, width, drive, limiter); shared with WAV export
    private val masterBus = MasterBusProcessor(SAMPLE_RATE)

    /** Master-bus meters for the mastering panel (updated once per audio block). */
    @Volatile
    var compressorReductionDb = 0f
    @Volatile
    var limiterReductionDb = 0f
    /** Short-term RMS of the master output in dBFS (an RMS reading, not LUFS). */
    @Volatile
    var masterRmsDb = -90f
    /** ITU-R BS.1770 K-weighted loudness of the master output (audio thread only writes it). */
    private val loudnessMeter = LoudnessMeter(SAMPLE_RATE)
    /** Momentary loudness (400 ms window), LUFS. */
    @Volatile
    var masterMomentaryLufs = LoudnessMeter.SILENCE_LUFS
    /** Short-term loudness (3 s window), LUFS. */
    @Volatile
    var masterShortTermLufs = LoudnessMeter.SILENCE_LUFS
    /**
     * Rough spectral balance of the master output for the mastering assistant: mean-square
     * energy (dBFS, smoothed per block) below ~150 Hz, between, and above ~5 kHz. One-pole
     * splits on the mono sum, so only a coarse tilt reading, not an analyser.
     */
    @Volatile
    var masterLowBandDb = -90f
    @Volatile
    var masterMidBandDb = -90f
    @Volatile
    var masterHighBandDb = -90f
    /** Largest absolute output sample of the last block (unsmoothed, linear, post limiter). */
    @Volatile
    var masterBlockPeak = 0f
    val voiceManager = VoiceManager()

    // Media players for imported audio stem playback
    private val stemPlayers = ConcurrentHashMap<String, MediaPlayer>()
    // MediaPlayer calls are IPC: every transport call on a stem runs on this thread, never on
    // the audio thread (which only posts "clip start reached" events) and never on the UI thread.
    private val stemThread = android.os.HandlerThread("SanwolfStems").apply { start() }
    private val stemHandler = android.os.Handler(stemThread.looper)

    // Current project reference. Setting it also pre-warms the project's instruments and
    // decodes its drum samples on a background thread, so the first hit on the audio thread
    // never has to resolve an instrument or fall back to a synth voice ("first-play glitch").
    @Volatile
    var currentProject: ProjectData? = null
        set(value) {
            field = value
            if (value != null) requestPrewarm(value)
            rebuildFxRacks(value)
        }

    // ---- Insert racks (right panel). Built on the caller's thread, published as one snapshot ----
    @Volatile
    private var fxPlan: FxRackPlan = FxRackPlan.EMPTY
    /** Processor per slot id, reused across rebuilds so filter / limiter state survives edits. */
    private val fxProcessors = HashMap<String, FxProcessor>()
    private val fxLock = Any()

    /**
     * Rebuilds the routing snapshot from the project's track racks and master inserts. Call after
     * a slot is added, removed, reordered or a track is deleted (setting [currentProject], which
     * every projectVersion bump does, calls it too). Parameter and bypass edits need no rebuild:
     * the audio thread reads the slot fields every block.
     * Audio-clip tracks are skipped: their stems play through MediaPlayer, outside this mix.
     */
    fun rebuildFxRacks(project: ProjectData?) {
        if (released) return
        try {
            synchronized(fxLock) {
                if (project == null) {
                    fxPlan = FxRackPlan.EMPTY
                    return
                }
                val used = HashSet<String>()
                fun unitsFor(slots: List<com.sanwolf.daw.model.FxSlot>): Array<FxUnit> {
                    val out = ArrayList<FxUnit>(slots.size)
                    for (slot in slots.take(com.sanwolf.daw.model.FX_RACK_SLOTS)) {
                        val type = slot.fxType ?: continue
                        var proc = fxProcessors[slot.id]
                        if (proc == null || proc.type != type) {
                            proc = FxProcessor.create(type, SAMPLE_RATE)
                            fxProcessors[slot.id] = proc
                        }
                        used.add(slot.id)
                        out.add(FxUnit(slot, proc))
                    }
                    return out.toTypedArray()
                }
                val keys = ArrayList<String>()
                val chains = ArrayList<Array<FxUnit>>()
                for (t in project.tracks.toList()) {
                    if (keys.size >= FxRackPlan.MAX_TRACK_BUSES) break
                    if (t.type == TrackType.AUDIO_IMPORT) continue
                    val slots = t.fxSlots()
                    if (slots.isEmpty()) continue
                    val chain = unitsFor(slots)
                    if (chain.isEmpty()) continue
                    keys.add(t.id)
                    chains.add(chain)
                }
                val master = unitsFor(project.masterSlots())
                fxProcessors.keys.retainAll(used)
                fxPlan = FxRackPlan(keys.toTypedArray(), chains.toTypedArray(), master)
                FxMeters.publish(fxProcessors, project.bpm)
            }
        } catch (e: Exception) {
            // The UI thread may be editing a rack list concurrently; the next rebuild fixes it
            Log.w(TAG, "Insert rack rebuild skipped", e)
        }
    }

    private val prewarmExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "SanwolfPrewarm").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    /** Instrument/drum keys already warmed (prewarm thread only). */
    private val warmedKeys = HashSet<String>()

    // Metronome (quarter-note click while playing; accent on the first beat of each bar)
    @Volatile
    var metronomeEnabled = false
    @Volatile
    var metronomeVolume = 0.5f

    init {
        MultisampleLibrary.init(context)
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
        audioThread = Thread({
            try {
                audioLoop()
            } catch (t: Throwable) {
                // Last line of defence: stop audio instead of crashing the app
                Log.e(TAG, "Audio thread died", t)
                com.sanwolf.daw.CrashReporter.breadcrumb("audio thread died: ${t.javaClass.simpleName}: ${t.message}")
                running = false
            }
        }, "SanwolfAudio").apply {
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

        val grScratch = FloatArray(2)
        // Spectral balance splits (mastering assistant): state persists across blocks
        val balLowAlpha = (1.0 - kotlin.math.exp(-2.0 * Math.PI * 150.0 / SAMPLE_RATE)).toFloat()
        val balHighAlpha = (1.0 - kotlin.math.exp(-2.0 * Math.PI * 5000.0 / SAMPLE_RATE)).toFloat()
        var balLowLp = 0f
        var balHighLp = 0f
        // Per-track insert rack buses and scratch frames
        val busSet = FxBusSet(FxRackPlan.MAX_TRACK_BUSES, BUFFER_SIZE)
        val fxIo = FloatArray(2)
        val fxTmp = FloatArray(2)

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
                loudnessMeter.addSilence(BUFFER_SIZE)
                masterMomentaryLufs = loudnessMeter.momentaryLufs
                masterShortTermLufs = loudnessMeter.shortTermLufs
                writeFully(track, silence)
                continue
            }

            // ---- Per-block parameters (hoisted out of the sample loop) ----
            val bpm = (proj?.bpm ?: 120).coerceAtLeast(1)
            val samplesPerBeat = (SAMPLE_RATE * 60.0) / bpm
            val vol = (proj?.masterVolume ?: masterVolume).coerceIn(0f, 1.5f)
            masterBus.configure(proj?.masteringConfig, vol)

            // Insert racks: publish this block's track buses and configure every module
            val plan = fxPlan
            val busCount = minOf(plan.trackKeys.size, plan.trackChains.size, FxRackPlan.MAX_TRACK_BUSES)
            busSet.count = busCount
            for (b in 0 until busCount) {
                busSet.keys[b] = plan.trackKeys[b]
                configureFxChain(plan.trackChains[b], bpm.toDouble())
            }
            configureFxChain(plan.master, bpm.toDouble())

            // ---- Sequencer + voice rendering, in small chunks for tight step timing ----
            var anyVoiceOutput = false
            var maxSteps = 32
            var loopBeats = 8.0
            var noteLoopBeats = 0.0
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
                // Arranger clips can sit anywhere on the 16-bar timeline: extend the loop to the
                // bar after the last note (capped at 16 bars) so moved clips still play.
                // Moved patterns and audio clips extend it the same way.
                noteLoopBeats = try {
                    var end = 0.0
                    for (t in proj.tracks) {
                        for (n in t.notes) {
                            if (!t.isNoteInClip(n)) continue
                            val e = n.startBeat + n.lengthBeats
                            if (e > end) end = e
                        }
                        if (t.notesClipEndBeat > end && t.notes.isNotEmpty()) end = t.notesClipEndBeat
                        if (t.patternStartBeat > 0.0) {
                            val pe = t.patternStartBeat + t.stepCount.coerceIn(1, 64) / 4.0
                            if (pe > end) end = pe
                        }
                        if (t.type == TrackType.AUDIO_IMPORT && t.audioStartBeat > 0.0) {
                            val durBeats = if (t.audioDurationMs > 0L) t.audioDurationMs * bpm / 60_000.0 else 4.0
                            val ae = t.audioStartBeat + durBeats
                            if (ae > end) end = ae
                        }
                    }
                    (kotlin.math.ceil(end / 4.0) * 4.0).coerceAtMost(MAX_LOOP_BEATS)
                } catch (e: Exception) {
                    noteLoopBeats
                }
                loopBeats = maxOf((maxSteps / 4.0).coerceAtLeast(4.0), noteLoopBeats)
            }
            val blockStartBeat = currentBeat
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
                if (voiceManager.renderBlock(n, leftBlock, rightBlock, offset, busSet, softClipMain = false)) anyVoiceOutput = true
                offset += n
            }
            // Track insert racks: process each bus and sum it into the main mix, then soft-clip
            for (b in 0 until busCount) {
                val chain = plan.trackChains[b]
                val bl = busSet.left[b]
                val br = busSet.right[b]
                for (i in 0 until BUFFER_SIZE) {
                    fxIo[0] = bl[i]
                    fxIo[1] = br[i]
                    runFxChain(chain, fxIo, fxTmp)
                    leftBlock[i] += fxIo[0]
                    rightBlock[i] += fxIo[1]
                }
            }
            for (i in 0 until BUFFER_SIZE) {
                leftBlock[i] = voiceManager.softClip(leftBlock[i])
                rightBlock[i] = voiceManager.softClip(rightBlock[i])
            }
            // Audio clips placed later on the timeline start when the playhead reaches them
            if (playing && proj != null && isPlaying) checkStemClipStarts(proj, blockStartBeat, currentBeat)

            // ---- Master bus: volume, mastering, limiter, meters, PCM ----
            var maxLeft = 0f
            var maxRight = 0f
            var sumLR = 0f
            var sumLL = 0f
            var sumRR = 0f
            var goniometerSampleIdx = 0
            var sumLow = 0f
            var sumMid = 0f
            var sumHigh = 0f
            var gonioCountdown = 0

            val masterInserts = plan.master
            val hasMasterInserts = masterInserts.isNotEmpty()
            for (i in 0 until BUFFER_SIZE) {
                if (hasMasterInserts) {
                    // Master insert rack runs on the summed mix, before the mastering chain
                    fxIo[0] = leftBlock[i]
                    fxIo[1] = rightBlock[i]
                    runFxChain(masterInserts, fxIo, fxTmp)
                    masterBus.process(fxIo[0], fxIo[1], limited)
                } else {
                    masterBus.process(leftBlock[i], rightBlock[i], limited)
                }
                val sampleL = limited[0]
                val sampleR = limited[1]
                loudnessMeter.process(sampleL, sampleR)

                val mono = (sampleL + sampleR) * 0.5f
                balLowLp += balLowAlpha * (mono - balLowLp)
                balHighLp += balHighAlpha * (mono - balHighLp)
                val bandHigh = mono - balHighLp
                val bandMid = mono - balLowLp - bandHigh
                sumLow += balLowLp * balLowLp
                sumMid += bandMid * bandMid
                sumHigh += bandHigh * bandHigh

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

            masterBus.takeGainReductionDb(grScratch)
            compressorReductionDb = compressorReductionDb * 0.6f + grScratch[0] * 0.4f
            limiterReductionDb = limiterReductionDb * 0.6f + grScratch[1] * 0.4f
            val meanSquare = (sumLL + sumRR) / (2f * BUFFER_SIZE)
            val rmsDb = if (meanSquare > 1e-9f) (10f * kotlin.math.log10(meanSquare)) else -90f
            masterRmsDb = masterRmsDb * 0.8f + rmsDb * 0.2f
            masterMomentaryLufs = loudnessMeter.momentaryLufs
            masterShortTermLufs = loudnessMeter.shortTermLufs
            masterLowBandDb = smoothBandDb(masterLowBandDb, sumLow)
            masterMidBandDb = smoothBandDb(masterMidBandDb, sumMid)
            masterHighBandDb = smoothBandDb(masterHighBandDb, sumHigh)
            masterBlockPeak = if (maxLeft > maxRight) maxLeft else maxRight
            currentPeakLeft = currentPeakLeft * 0.7f + maxLeft * 0.3f
            currentPeakRight = currentPeakRight * 0.7f + maxRight * 0.3f

            // A full block (longer than the 5 ms look-ahead) of silence in and out means the
            // limiter's delay line is empty; from then on idle blocks can skip all DSP.
            // (~-100 dBFS threshold: EQ filter tails decay towards, but never exactly reach, zero)
            // Reverb / delay tails keep the block "live" so echoes are neither cut nor reset
            val blockSilent = !anyVoiceOutput && maxLeft < 1e-5f && maxRight < 1e-5f && !fxPlanHasTail(plan)
            if (blockSilent && !outputSilent) {
                masterBus.reset()
                for (chain in plan.trackChains) for (u in chain) u.processor.reset()
                for (u in plan.master) u.processor.reset()
            }
            outputSilent = blockSilent

            writeFully(track, audioBuffer)
            } catch (e: Throwable) {
                // Never let one bad block kill the audio thread (an uncaught throwable here would
                // take the whole process down): log it, leave a breadcrumb, output silence.
                Log.e(TAG, "Audio block failed", e)
                com.sanwolf.daw.CrashReporter.breadcrumb("audio block failed: ${e.javaClass.simpleName}: ${e.message}")
                outputSilent = false
                try {
                    writeFully(track, silence)
                } catch (w: Throwable) {
                    Log.e(TAG, "Silence write failed", w)
                    SystemClock.sleep(20)
                }
                if (e is OutOfMemoryError) {
                    runCatching { PcmSampleCache.trim() }
                    runCatching { MultisampleLibrary.trim() }
                }
            }
        }
    }

    /** Meter ballistics for idle blocks, matching what a silent rendered block would produce. */
    private fun decayMeters() {
        currentPeakLeft *= 0.7f
        currentPeakRight *= 0.7f
        phaseCorrelation = phaseCorrelation * 0.7f + 0.3f
        for (b in 0 until 8) frequencyBands[b] *= 0.5f
        compressorReductionDb *= 0.6f
        limiterReductionDb *= 0.6f
        masterRmsDb = maxOf(-90f, masterRmsDb - 3f)
        masterLowBandDb = maxOf(-90f, masterLowBandDb - 3f)
        masterMidBandDb = maxOf(-90f, masterMidBandDb - 3f)
        masterHighBandDb = maxOf(-90f, masterHighBandDb - 3f)
        masterBlockPeak = 0f
    }

    /** One block of band energy (sum of squares over BUFFER_SIZE frames) into a smoothed dBFS reading. */
    private fun smoothBandDb(previous: Float, sumSquares: Float): Float {
        val ms = sumSquares / BUFFER_SIZE
        val db = if (ms > 1e-9f) 10f * kotlin.math.log10(ms) else -90f
        return previous * 0.8f + db * 0.2f
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

            // A pattern placed at a later bar (arranger) is silent before its start, then loops.
            val trackSteps = track.stepCount.coerceAtLeast(1)
            val startStep = (track.patternStartBeat * 4.0 + 0.5).toInt()
            val effectiveStep = if (startStep <= 0) {
                step % trackSteps
            } else {
                val rel = (beat * 4.0).toInt() - startStep
                if (rel < 0) -1 else rel % trackSteps
            }
            if (effectiveStep >= 0 && track.steps.getOrElse(effectiveStep) { false }) {
                val vel = track.stepVelocities.getOrElse(effectiveStep) { 0.8f } * track.volume
                if (track.type == TrackType.DRUM_MACHINE) {
                    // The instrument (or a loaded sample) decides the sound; the name is just a label
                    triggerDrumTrack(track, vel, track.pan)
                } else {
                    triggerTrackNote(track, 60, 0.25f, vel)
                }
            }

            for (note in track.notes) {
                if (!track.isNoteInClip(note)) continue // cropped by the clip's right edge
                if (kotlin.math.abs(note.startBeat - beat) < 0.125) {
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
            triggerDrumTrack(track, finalVol, finalPan)
            return
        }

        // Authoritative resolution via InstrumentLibrary
        val instrumentQuery = when {
            !track?.synthPresetName.isNullOrEmpty() -> track!!.synthPresetName
            !track?.name.isNullOrEmpty() -> track!!.name
            else -> "keys.grand.piano"
        }
        // Bundled multisample instruments (VCSL) play their decoded samples. Until the decode is
        // done the note is skipped silently (logged once) rather than falling back to a synth.
        val multisample = MultisampleLibrary.find(instrumentQuery)
        if (multisample != null) {
            val msVoice = multisample.createVoice(pitch, durationSec, finalVol, finalPan)
            if (msVoice != null) voiceManager.addVoice(msVoice, track?.id)
            return
        }

        val def = InstrumentLibrary.getById(instrumentQuery)

        val voice: ActiveVoice = InstrumentVoices.createVoiceForDefinition(
            definition = def,
            pitch = pitch,
            durationSec = durationSec,
            velocity = finalVol,
            pan = finalPan
        )

        voiceManager.addVoice(voice, track?.id)
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
        definition: InstrumentDefinition? = null,
        busTrackId: String? = null
    ) {
        val def = definition ?: InstrumentLibrary.getById(drumName)
        val drumVoice: ActiveVoice = InstrumentVoices.createDrumVoice(def.drumType.ifEmpty { def.id }, velocity, pan)
        voiceManager.addVoice(drumVoice, busTrackId)
    }

    /**
     * One hit on a drum (step-sequencer) track: its loaded one-shot sample when it has one
     * ([TrackData.audioUri] decoded via [preloadDrumSample]), otherwise the synth drum voice.
     */
    fun triggerDrumTrack(track: TrackData, velocity: Float, pan: Float = track.pan) {
        val pcm = PcmSampleCache.get(track.audioUri)
        if (pcm != null) {
            voiceManager.addVoice(SampleVoice(pcm, velocity, pan), track.id)
        } else {
            triggerDrumSound(track.synthPresetName.ifEmpty { track.name }, velocity, pan, busTrackId = track.id)
        }
    }

    /**
     * Snapshots what the project's tracks need (on the caller's thread, which owns the project)
     * and warms it on [prewarmExecutor]: resolves instrument definitions (InstrumentLibrary's
     * resolve cache), runs each voice type briefly (class loading / JIT) and decodes drum samples.
     */
    private fun requestPrewarm(project: ProjectData) {
        if (released) return
        val jobs = try {
            project.tracks.map { t ->
                Triple(t.type, t.synthPresetName.ifEmpty { t.name }, t.audioUri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Prewarm snapshot failed", e)
            return
        }
        val sampleUris = jobs.filter { it.first == TrackType.DRUM_MACHINE && !it.third.isNullOrBlank() }
            .mapNotNull { it.third }.toSet()
        PcmSampleCache.setPinned(sampleUris)
        // Multisample instruments the project uses: pin them and decode them in the background.
        val multisampleNames = jobs
            .filter { it.first != TrackType.DRUM_MACHINE && it.first != TrackType.AUDIO_IMPORT && MultisampleLibrary.isMultisample(it.second) }
            .map { it.second }
        MultisampleLibrary.setPinned(multisampleNames)
        multisampleNames.distinct().forEach { MultisampleLibrary.prewarm(it) }
        runCatching {
            prewarmExecutor.execute {
                for ((type, instrument, uri) in jobs) {
                    try {
                        if (type == TrackType.AUDIO_IMPORT) continue
                        if (type == TrackType.DRUM_MACHINE && !uri.isNullOrBlank()) {
                            PcmSampleCache.load(uri)
                        }
                        // Multisample instruments are decoded by MultisampleLibrary (above).
                        if (type != TrackType.DRUM_MACHINE && MultisampleLibrary.isMultisample(instrument)) continue
                        val key = "${type.name}|$instrument"
                        if (!warmedKeys.add(key)) continue
                        val def = InstrumentLibrary.getById(instrument)
                        val voice: ActiveVoice = if (type == TrackType.DRUM_MACHINE) {
                            InstrumentVoices.createDrumVoice(def.drumType.ifEmpty { def.id }, 0f, 0f)
                        } else {
                            InstrumentVoices.createVoiceForDefinition(def, 60, 0.05f, 0f, 0f)
                        }
                        var n = 0
                        while (n < 512 && !voice.isFinished) {
                            voice.nextSample()
                            n++
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Prewarm failed for $instrument", e)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Prewarm not scheduled", it) }
    }

    /**
     * Instrument-picker preview of a multisample instrument: decodes it in the background if
     * needed, then plays [pitch] once it is ready. Safe to call from the UI thread.
     */
    fun previewMultisample(name: String, pitch: Int = 60, durationSec: Float = 0.8f, velocity: Float = 0.85f) {
        if (released) return
        MultisampleLibrary.prewarm(name) {
            MultisampleLibrary.find(name)?.createVoice(pitch, durationSec, velocity, 0f)?.let { voiceManager.addVoice(it) }
        }
    }

    /** Decodes a drum track's sample so the audio thread can play it. Blocking: call off the main thread. */
    fun preloadDrumSample(uri: String): Boolean = PcmSampleCache.load(uri) != null

    /** One metronome / count-in click. Safe to call from the UI thread. */
    fun playMetronomeClick(accent: Boolean) {
        val vel = (metronomeVolume * if (accent) 1.0f else 0.7f).coerceIn(0f, 1f)
        if (vel <= 0f || released) return
        voiceManager.addVoice(InstrumentVoices.createDrumVoice(if (accent) "rim click" else "closed hihat", vel, 0f))
    }

    /** Releases every imported-audio stem player (used when a different project is loaded). */
    fun clearStems() {
        val players = stemPlayers.values.toList()
        stemPlayers.clear()
        postStem { players.forEach { runCatching { it.release() } } }
    }

    fun play() {
        if (!running && !released) initAudioTrack()
        isPlaying = true
        scheduleStemSync(currentBeat, start = true)
    }

    fun pause() {
        isPlaying = false
        postStem { stemPlayers.values.forEach { mp -> runCatching { if (mp.isPlaying) mp.pause() } } }
    }

    fun stop() {
        isPlaying = false
        currentBeat = 0.0
        currentStep = 0
        voiceManager.clear()
        postStem {
            stemPlayers.values.forEach { mp ->
                runCatching {
                    if (mp.isPlaying) mp.pause()
                    mp.seekTo(0)
                }.onFailure { Log.w(TAG, "Stem stop failed", it) }
            }
        }
    }

    fun seekToBeat(beat: Double) {
        currentBeat = beat
        currentStep = (beat * 4).toInt()
        scheduleStemSync(beat, start = isPlaying)
    }

    /** Re-aligns every stem with the playhead (after an audio clip was moved in the arranger). */
    fun resyncStems() = scheduleStemSync(currentBeat, start = isPlaying)

    /**
     * Prepares a stem player for [trackId]. Returns the file's duration in ms (0 if unknown,
     * -1 if it could not be opened), which callers store in TrackData.audioDurationMs.
     */
    fun addStemAudio(trackId: String, uri: Uri): Int {
        return runCatching {
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
            val old = stemPlayers.put(trackId, mp)
            if (old != null) postStem { runCatching { old.release() } }
            mp.duration.coerceAtLeast(0)
        }.onFailure { Log.e(TAG, "Couldn't open stem audio $uri", it) }.getOrDefault(-1)
    }

    private fun postStem(action: () -> Unit) {
        if (!stemThread.isAlive) return
        stemHandler.post {
            try {
                action()
            } catch (e: Throwable) {
                Log.w(TAG, "Stem transport call failed", e)
            }
        }
    }

    /**
     * Seeks every stem to where its clip is at [beat]: inside the clip -> seek to the offset
     * (and start if [start]); before the clip -> paused at 0, the audio thread starts it later.
     */
    private fun scheduleStemSync(beat: Double, start: Boolean) {
        val proj = currentProject ?: return
        val bpm = proj.bpm.coerceAtLeast(1)
        val clipStarts = proj.tracks
            .filter { it.type == TrackType.AUDIO_IMPORT }
            .map { it.id to it.audioStartBeat.coerceAtLeast(0.0) }
        postStem {
            for ((id, clipStart) in clipStarts) {
                val mp = stemPlayers[id] ?: continue
                runCatching {
                    if (beat >= clipStart) {
                        val ms = ((beat - clipStart) * 60_000.0 / bpm).toInt()
                        val dur = mp.duration
                        if (dur <= 0 || ms < dur) {
                            mp.seekTo(ms)
                            if (start && isPlaying) {
                                if (!mp.isPlaying) mp.start()
                            } else if (mp.isPlaying) {
                                mp.pause()
                            }
                        } else if (mp.isPlaying) {
                            mp.pause()
                        }
                    } else {
                        if (mp.isPlaying) mp.pause()
                        mp.seekTo(0)
                    }
                }.onFailure { Log.w(TAG, "Stem sync failed for $id", it) }
            }
        }
    }

    /**
     * Audio thread: posts a start for every audio clip whose start the playhead crossed in
     * this block (including at the loop wrap). A stem that is still playing keeps running.
     */
    private fun checkStemClipStarts(proj: ProjectData, fromBeat: Double, toBeat: Double) {
        if (stemPlayers.isEmpty()) return
        try {
            val wrapped = toBeat < fromBeat
            val tracks = proj.tracks
            for (i in 0 until tracks.size) {
                val t = tracks.getOrNull(i) ?: continue
                if (t.type != TrackType.AUDIO_IMPORT) continue
                val s = t.audioStartBeat
                val crossed = if (wrapped) (s > fromBeat || s <= toBeat) else (s > fromBeat && s <= toBeat)
                if (crossed) {
                    val id = t.id
                    postStem {
                        val mp = stemPlayers[id]
                        if (mp != null && isPlaying && !mp.isPlaying) {
                            mp.seekTo(0)
                            mp.start()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Track list edited concurrently on the UI thread: try again next block
            Log.w(TAG, "Stem clip check skipped", e)
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
        val players = stemPlayers.values.toList()
        stemPlayers.clear()
        postStem { players.forEach { runCatching { it.release() } } }
        stemThread.quitSafely()
        prewarmExecutor.shutdownNow()
        voiceManager.clear()
    }

    private fun midiToFreq(midiNote: Int): Float {
        return (440.0 * 2.0.pow((midiNote - 69) / 12.0)).toFloat()
    }

    private fun freqToMidi(freq: Float): Int {
        return (69 + 12 * kotlin.math.ln(freq / 440.0) / kotlin.math.ln(2.0)).toInt().coerceIn(0, 127)
    }
}
