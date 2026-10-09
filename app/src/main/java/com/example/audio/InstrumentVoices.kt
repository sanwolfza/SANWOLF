package com.example.audio

import com.example.model.SynthWaveform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Enterprise Audio Voice Generators for SANWOLF Audio Studio.
 * Shared between live playback (SanwolfAudioEngine) and offline render (WavExporter, MixerRenderer),
 * guaranteeing 1:1 identical sound reproduction.
 *
 * Performance notes (real-time audio thread):
 *  - Every per-note constant (phase increments, envelope times, filter coefficients, string
 *    "type" checks) is computed once in the constructor, never per sample.
 *  - Exponential decays exp(-t * rate) are produced recursively (one multiply per sample)
 *    instead of calling exp() per sample; the curve is mathematically identical.
 *  - Sine oscillators use an interpolated lookup table (error < 1e-6, far below 16-bit noise).
 */
object InstrumentVoices {
    const val SAMPLE_RATE = 44100

    private const val TWO_PI = 2.0 * PI
    private const val INV_SR = 1.0f / SAMPLE_RATE

    // ---------------------------------------------------------------------
    // Fast math helpers (allocation-free)
    // ---------------------------------------------------------------------
    private const val SIN_TABLE_SIZE = 4096
    private const val SIN_INDEX_SCALE = SIN_TABLE_SIZE / (2.0 * PI)
    private val SIN_TABLE = FloatArray(SIN_TABLE_SIZE + 1) { i ->
        sin(i * 2.0 * PI / SIN_TABLE_SIZE).toFloat()
    }

    /** Linear-interpolated table sine. Accepts any phase in radians. */
    fun fastSin(phase: Double): Float {
        var idx = phase * SIN_INDEX_SCALE
        if (idx < 0.0 || idx >= SIN_TABLE_SIZE) {
            idx -= floor(idx / SIN_TABLE_SIZE) * SIN_TABLE_SIZE
        }
        var i = idx.toInt()
        if (i >= SIN_TABLE_SIZE) i = 0
        if (i < 0) i = 0
        val frac = (idx - i).toFloat()
        val a = SIN_TABLE[i]
        return a + (SIN_TABLE[i + 1] - a) * frac
    }

    /** Per-sample multiplier so that value(n) = exp(-rate * n / SAMPLE_RATE). */
    private fun decayCoeff(rate: Float): Double = exp(-rate.toDouble() / SAMPLE_RATE)

    private fun phaseInc(freq: Double): Double = (TWO_PI * freq) / SAMPLE_RATE

    fun createVoiceForDefinition(
        definition: InstrumentDefinition,
        pitch: Int = 60,
        durationSec: Float = 0.4f,
        velocity: Float = 0.8f,
        pan: Float = 0f
    ): ActiveVoice {
        val freq = midiToFreq(pitch)
        val vType = definition.voiceType.ifEmpty { definition.id }

        return when {
            definition.isDrum || definition.category == InstrumentCategory.DRUMS || definition.category == InstrumentCategory.WORLD_PERCUSSION || definition.category == InstrumentCategory.SOUND_EFFECTS_TEXTURES ->
                createDrumVoice(definition.drumType.ifEmpty { vType }, velocity, pan)

            vType.contains("bell") ->
                BellVoice(freq, durationSec, velocity, pan)

            vType.contains("kalimba") || vType.contains("marimba") || vType.contains("xylophone") ||
            vType.contains("glockenspiel") || vType.contains("vibraphone") || vType.contains("music_box") ->
                MalletVoice(freq, durationSec, velocity, pan, vType)

            vType.contains("piano") || vType.contains("rhodes") || vType.contains("wurlitzer") || definition.category == InstrumentCategory.KEYS_PIANO ->
                PianoVoice(freq, durationSec, velocity, pan, definition.attackMs, definition.decayMs, definition.sustainLevel, definition.releaseMs)

            vType.contains("organ") ->
                OrganVoice(freq, durationSec, velocity, pan, definition.filterCutoffHz, definition.attackMs, definition.releaseMs)

            vType.contains("violin") || vType.contains("viola") || vType.contains("cello") ||
            vType.contains("strings") || definition.category == InstrumentCategory.STRINGS ->
                StringsVoice(freq, durationSec, velocity, pan, definition.attackMs, definition.releaseMs)

            vType.contains("trumpet") || vType.contains("trombone") || vType.contains("horn") ||
            vType.contains("saxophone") || vType.contains("brass") ->
                BrassVoice(freq, durationSec, velocity, pan, definition.attackMs, definition.releaseMs)

            vType.contains("flute") || vType.contains("clarinet") || vType.contains("oboe") ->
                FluteVoice(freq, durationSec, velocity, pan, definition.attackMs, definition.releaseMs)

            vType.contains("bass") || definition.category == InstrumentCategory.BASS ->
                BassVoice(freq, durationSec, velocity, pan, definition.filterCutoffHz, definition.filterResonance, definition.attackMs, definition.decayMs, definition.sustainLevel, definition.releaseMs, vType)

            vType.contains("supersaw") ->
                SuperSawVoice(freq, durationSec, velocity, pan, definition.attackMs, definition.releaseMs)

            vType.contains("pad") || definition.category == InstrumentCategory.PADS ->
                PadVoice(freq, durationSec, velocity, pan, definition.filterCutoffHz, definition.filterResonance, definition.attackMs, definition.releaseMs, vType)

            vType.contains("pluck") ->
                PluckVoice(freq, durationSec, velocity, pan, definition.filterCutoffHz, definition.filterResonance, definition.attackMs, definition.releaseMs, definition.defaultWaveform)

            else ->
                LeadVoice(freq, durationSec, velocity, pan, definition.filterCutoffHz, definition.filterResonance, definition.attackMs, definition.decayMs, definition.sustainLevel, definition.releaseMs, definition.defaultWaveform)
        }
    }

    fun createDrumVoice(drumType: String, velocity: Float = 0.8f, pan: Float = 0f): ActiveVoice {
        val lower = drumType.lowercase()
        return when {
            lower.contains("kick") || lower.contains("808") -> KickVoice(lower, velocity, pan)
            lower.contains("snare") || lower.contains("rim") -> SnareVoice(lower, velocity, pan)
            lower.contains("clap") -> ClapVoice(lower, velocity, pan)
            lower.contains("open") -> HiHatVoice(isOpen = true, velocity = velocity, pan = pan)
            lower.contains("hihat") || lower.contains("hat") -> HiHatVoice(isOpen = false, velocity = velocity, pan = pan)
            lower.contains("tom") -> TomVoice(lower, velocity, pan)
            lower.contains("crash") || lower.contains("ride") || lower.contains("splash") || lower.contains("china") -> CymbalVoice(lower, velocity, pan)
            lower.contains("fx") || lower.contains("rise") || lower.contains("downlifter") || lower.contains("impact") ||
            lower.contains("sweep") || lower.contains("vinyl") || lower.contains("whoosh") || lower.contains("glitch") -> FXVoice(lower, velocity, pan)
            else -> WorldPercussionVoice(lower, velocity, pan)
        }
    }

    fun midiToFreq(midiNote: Int): Float {
        return (440.0 * 2.0.pow((midiNote - 69) / 12.0)).toFloat()
    }

    /**
     * Deterministic, zero-allocation pseudo-noise generator in [-1.0f, 1.0f]
     * seeded by sampleIndex. Guarantees 1:1 bit-for-bit equality between live preview and offline render.
     */
    fun pseudoNoise(index: Int): Float {
        var x = index * 1103515245 + 12345
        x = (x xor (x ushr 16)) * 0x45d9f3b
        x = (x xor (x ushr 16))
        return (x and 0x7FFFFFFF).toFloat() / 1073741824f - 1.0f
    }

    // =========================================================================
    // MELODIC VOICES
    // =========================================================================

    // 1. Piano Voice
    class PianoVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val attackMs: Float = 5f,
        val decayMs: Float = 320f,
        val sustainLevel: Float = 0.38f,
        val releaseMs: Float = 400f
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.05f))).toInt()
        private var phase1 = 0.0
        private var phase2 = 0.0
        private var phase3 = 0.0
        private val inc1 = phaseInc(freq.toDouble())
        private val inc2 = phaseInc(freq * 2.002)
        private val inc3 = phaseInc(freq * 3.005)
        private val attack = (attackMs / 1000f).coerceAtLeast(0.002f)
        private val invAttack = 1f / attack
        private val decay = (decayMs / 1000f).coerceAtLeast(0.05f)
        private val decayK = decayCoeff(3.5f / decay)
        private var decayEnv = -1.0
        private val gain = velocity * 0.65f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            phase1 += inc1
            phase2 += inc2
            phase3 += inc3
            if (phase1 >= TWO_PI) phase1 -= TWO_PI
            if (phase2 >= TWO_PI) phase2 -= TWO_PI
            if (phase3 >= TWO_PI) phase3 -= TWO_PI

            val hammerClick = if (t < 0.004f) pseudoNoise(sampleIndex) * (1f - t / 0.004f) * 0.35f else 0f
            val raw = fastSin(phase1) + fastSin(phase2) * 0.4f + fastSin(phase3) * 0.2f + hammerClick

            val env: Float
            if (t < attack) {
                env = t * invAttack
            } else {
                if (decayEnv < 0.0) decayEnv = exp(-((t - attack) / decay) * 3.5).toDouble()
                val e = decayEnv.toFloat()
                env = if (e < 0.02f) 0.02f else e
                if (e >= 0.02f) decayEnv *= decayK
            }

            return raw * env * gain
        }
    }

    // 2. Bell Voice (Inharmonic partials 1.0, 2.76, 5.4, 8.93)
    class BellVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + 0.6f)).toInt()
        private var p1 = 0.0
        private var p2 = 0.0
        private var p3 = 0.0
        private var p4 = 0.0
        private val i1 = phaseInc(freq * 1.0)
        private val i2 = phaseInc(freq * 2.76)
        private val i3 = phaseInc(freq * 5.40)
        private val i4 = phaseInc(freq * 8.93)
        private var e1 = 1.0
        private var e2 = 1.0
        private var e3 = 1.0
        private var e4 = 1.0
        private val k1 = decayCoeff(2.5f)
        private val k2 = decayCoeff(4.5f)
        private val k3 = decayCoeff(7.0f)
        private val k4 = decayCoeff(11.0f)
        private val gain = velocity * 0.65f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            p1 += i1; p2 += i2; p3 += i3; p4 += i4
            if (p1 >= TWO_PI) p1 -= TWO_PI
            if (p2 >= TWO_PI) p2 -= TWO_PI
            if (p3 >= TWO_PI) p3 -= TWO_PI
            if (p4 >= TWO_PI) p4 -= TWO_PI

            val s1 = fastSin(p1) * e1.toFloat()
            val s2 = fastSin(p2) * e2.toFloat() * 0.5f
            val s3 = fastSin(p3) * e3.toFloat() * 0.3f
            val s4 = fastSin(p4) * e4.toFloat() * 0.15f
            e1 *= k1; e2 *= k2; e3 *= k3; e4 *= k4
            val strike = if (t < 0.003f) pseudoNoise(sampleIndex) * 0.25f else 0f

            return (s1 + s2 + s3 + s4 + strike) * gain
        }
    }

    // 3. Mallet Voice (Kalimba, Marimba, Xylophone)
    class MalletVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val type: String
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + 0.4f)).toInt()
        private var p1 = 0.0
        private var p2 = 0.0
        private val isKalimba = type.contains("kalimba")
        private val i1 = phaseInc(freq.toDouble())
        private val i2 = phaseInc(freq * (if (isKalimba) 3.0 else 4.0))
        private val decayRate = if (isKalimba) 4.0f else 8.5f
        private var e1 = 1.0
        private var e2 = 1.0
        private val k1 = decayCoeff(decayRate)
        private val k2 = decayCoeff(decayRate * 2.2f)
        private val gain = velocity * 0.7f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            p1 += i1
            p2 += i2
            if (p1 >= TWO_PI) p1 -= TWO_PI
            if (p2 >= TWO_PI) p2 -= TWO_PI

            val tone1 = fastSin(p1) * e1.toFloat()
            val tone2 = fastSin(p2) * e2.toFloat() * 0.35f
            e1 *= k1; e2 *= k2
            val click = if (t < 0.003f) pseudoNoise(sampleIndex) * 0.4f else 0f

            return (tone1 + tone2 + click) * gain
        }
    }

    // 4. Strings Voice (Violin, Cello, Ensemble)
    class StringsVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val attackMs: Float = 70f,
        val releaseMs: Float = 280f
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.15f))).toInt()
        private var ph1 = 0.0
        private var ph2 = 0.0
        private var ph3 = 0.0
        private var filterY1 = 0f
        private val baseInc = phaseInc(freq.toDouble())
        private val attack = (attackMs / 1000f).coerceIn(0.04f, 0.4f)
        private val invAttack = 1f / attack
        private val invRelease = 1f / (releaseMs / 1000f).coerceAtLeast(0.1f)
        private val gain = velocity * 0.7f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            val vibrato = 1.0 + 0.012f * fastSin(t * TWO_PI * 5.5)
            val inc = baseInc * vibrato
            ph1 += inc * 0.998
            ph2 += inc
            ph3 += inc * 1.002
            if (ph1 >= TWO_PI) ph1 -= TWO_PI
            if (ph2 >= TWO_PI) ph2 -= TWO_PI
            if (ph3 >= TWO_PI) ph3 -= TWO_PI

            val raw = ((3.0 - (ph1 + ph2 + ph3) / PI).toFloat()) * 0.333f

            val env = when {
                t < attack -> t * invAttack
                t < duration -> 0.9f
                else -> (0.9f * (1f - (t - duration) * invRelease)).coerceAtLeast(0f)
            }

            filterY1 += 0.14f * (raw - filterY1)
            return filterY1 * env * gain
        }
    }

    // 5. Brass Voice (Trumpet, Sax, Trombone)
    class BrassVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val attackMs: Float = 25f,
        val releaseMs: Float = 160f
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.08f))).toInt()
        private var ph1 = 0.0
        private var ph2 = 0.0
        private var filterY1 = 0f
        private val i1 = phaseInc(freq.toDouble())
        private val i2 = phaseInc(freq * 1.003)
        private val attack = (attackMs / 1000f).coerceIn(0.015f, 0.12f)
        private val invAttack = 1f / attack
        private val invRelease = 1f / (releaseMs / 1000f).coerceAtLeast(0.05f)
        private var biteEnv = 1.0
        private val biteK = decayCoeff(18f)
        private val gain = velocity * 0.75f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            ph1 += i1
            ph2 += i2
            if (ph1 >= TWO_PI) ph1 -= TWO_PI
            if (ph2 >= TWO_PI) ph2 -= TWO_PI

            val saw1 = 1f - (ph1 / PI).toFloat()
            val saw2 = 1f - (ph2 / PI).toFloat()
            val raw = (saw1 * 0.7f + saw2 * 0.3f)

            val env = when {
                t < attack -> t * invAttack
                t < duration -> 0.85f
                else -> (0.85f * (1f - (t - duration) * invRelease)).coerceAtLeast(0f)
            }

            val bite = biteEnv.toFloat() * 0.25f
            biteEnv *= biteK
            val cutoffNorm = (0.20f + bite).coerceIn(0.05f, 0.45f)
            filterY1 += cutoffNorm * (raw - filterY1)
            return filterY1 * env * gain
        }
    }

    // 6. Flute Voice (Wind)
    class FluteVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val attackMs: Float = 35f,
        val releaseMs: Float = 170f
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.08f))).toInt()
        private var ph = 0.0
        private val baseInc = phaseInc(freq.toDouble())
        private val attack = (attackMs / 1000f).coerceIn(0.02f, 0.15f)
        private val invAttack = 1f / attack
        private val invRelease = 1f / (releaseMs / 1000f).coerceAtLeast(0.05f)
        private var breathEnv = 1.0
        private val breathK = decayCoeff(8f)
        private val gain = velocity * 0.65f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            val vibrato = 1.0 + 0.01f * fastSin(t * TWO_PI * 5.0)
            ph += baseInc * vibrato
            if (ph >= TWO_PI) ph -= TWO_PI

            val sine = fastSin(ph)
            val breathNoise = pseudoNoise(sampleIndex) * 0.08f * breathEnv.toFloat()
            breathEnv *= breathK
            val raw = sine * 0.92f + breathNoise

            val env = when {
                t < attack -> t * invAttack
                t < duration -> 0.9f
                else -> (0.9f * (1f - (t - duration) * invRelease)).coerceAtLeast(0f)
            }

            return raw * env * gain
        }
    }

    // 7. Bass Voice (Sub Bass, Moog, 808, Reese)
    class BassVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val cutoffHz: Float = 800f,
        val resonance: Float = 1.2f,
        val attackMs: Float = 15f,
        val decayMs: Float = 300f,
        val sustainLevel: Float = 0.85f,
        val releaseMs: Float = 250f,
        val vType: String = ""
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.1f))).toInt()
        private var ph1 = 0.0
        private var ph2 = 0.0
        private var filterY1 = 0f
        private val is808 = vType.contains("808")
        // 0 = pure sine (sub / 808), 1 = reese (detuned saws), 2 = sine/saw blend
        private val mode = when {
            vType.contains("sub") || is808 -> 0
            vType.contains("reese") -> 1
            else -> 2
        }
        private val baseInc = phaseInc(freq.toDouble())
        private val attack = (attackMs / 1000f).coerceAtLeast(0.005f)
        private val invAttack = 1f / attack
        private val decay = (decayMs / 1000f).coerceAtLeast(0.05f)
        private val invDecay = 1f / decay
        private val invRelease = 1f / (releaseMs / 1000f).coerceAtLeast(0.02f)
        private val cutoffNorm = (cutoffHz / SAMPLE_RATE).coerceIn(0.02f, 0.45f)
        private val gain = velocity * 0.85f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            val inc = if (is808 && t < 0.05f) {
                baseInc * (1f + 2f * (1f - t / 0.05f))
            } else {
                baseInc
            }

            ph1 += inc
            if (ph1 >= TWO_PI) ph1 -= TWO_PI

            val raw = when (mode) {
                0 -> fastSin(ph1)
                1 -> {
                    ph2 += inc * 1.008
                    if (ph2 >= TWO_PI) ph2 -= TWO_PI
                    ((1f - (ph1 / PI).toFloat()) + (1f - (ph2 / PI).toFloat())) * 0.5f
                }
                else -> fastSin(ph1) * 0.7f + (1f - (ph1 / PI).toFloat()) * 0.3f
            }

            val env = when {
                t < attack -> t * invAttack
                t < attack + decay -> 1f - (1f - sustainLevel) * ((t - attack) * invDecay)
                t < duration -> sustainLevel
                else -> (sustainLevel * (1f - (t - duration) * invRelease)).coerceAtLeast(0f)
            }

            filterY1 += cutoffNorm * (raw - filterY1)
            return filterY1 * env * gain
        }
    }

    // 8. Pad Voice (Warm, Dark, Ambient)
    class PadVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val cutoffHz: Float = 2000f,
        val resonance: Float = 1.0f,
        val attackMs: Float = 220f,
        val releaseMs: Float = 600f,
        val vType: String = ""
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.2f))).toInt()
        private var ph1 = 0.0
        private var ph2 = 0.0
        private var filterY1 = 0f
        private val i1 = phaseInc(freq.toDouble())
        private val i2 = phaseInc(freq * 1.004)
        private val attack = (attackMs / 1000f).coerceAtLeast(0.15f)
        private val invAttack = 1f / attack
        private val invRelease = 1f / (releaseMs / 1000f).coerceAtLeast(0.25f)
        private val cutoffNorm = (cutoffHz / SAMPLE_RATE).coerceIn(0.01f, 0.35f)
        private val gain = velocity * 0.65f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            ph1 += i1
            ph2 += i2
            if (ph1 >= TWO_PI) ph1 -= TWO_PI
            if (ph2 >= TWO_PI) ph2 -= TWO_PI

            val raw = ((2.0 - (ph1 + ph2) / PI).toFloat()) * 0.5f

            val env = when {
                t < attack -> t * invAttack
                t < duration -> 0.82f + 0.18f * fastSin(t * TWO_PI * 0.5)
                else -> (0.82f * (1f - (t - duration) * invRelease)).coerceAtLeast(0f)
            }

            filterY1 += cutoffNorm * (raw - filterY1)
            return filterY1 * env * gain
        }
    }

    // 9. Lead Voice
    class LeadVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val cutoffHz: Float = 3500f,
        val resonance: Float = 2.5f,
        val attackMs: Float = 12f,
        val decayMs: Float = 160f,
        val sustainLevel: Float = 0.7f,
        val releaseMs: Float = 200f,
        val waveform: SynthWaveform = SynthWaveform.SAWTOOTH
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.05f))).toInt()
        private var phase = 0.0
        private var filterY1 = 0f
        private val waveMode = waveform.ordinal
        private val baseInc = phaseInc(freq.toDouble())
        private val attack = (attackMs / 1000f).coerceAtLeast(0.005f)
        private val invAttack = 1f / attack
        private val decay = (decayMs / 1000f).coerceAtLeast(0.05f)
        private val invDecay = 1f / decay
        private val invRelease = 1f / (releaseMs / 1000f).coerceAtLeast(0.02f)
        private val cutoffNorm = (cutoffHz / SAMPLE_RATE).coerceIn(0.02f, 0.49f)
        private val gain = velocity * 0.72f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            val vibrato = 1.0 + 0.015f * fastSin(t * TWO_PI * 5.5)
            phase += baseInc * vibrato
            if (phase >= TWO_PI) phase -= TWO_PI

            val raw = when (waveMode) {
                WAVE_SINE -> fastSin(phase)
                WAVE_SAW -> (1f - (phase / PI).toFloat())
                WAVE_SQUARE -> if (phase < PI) 1f else -1f
                WAVE_TRIANGLE -> {
                    val p = (phase / TWO_PI).toFloat()
                    if (p < 0.5f) 4f * p - 1f else 3f - 4f * p
                }
                else -> pseudoNoise(sampleIndex)
            }

            val env = when {
                t < attack -> t * invAttack
                t < attack + decay -> 1f - (1f - sustainLevel) * ((t - attack) * invDecay)
                t < duration -> sustainLevel
                else -> (sustainLevel * (1f - (t - duration) * invRelease)).coerceAtLeast(0f)
            }

            filterY1 += cutoffNorm * (raw - filterY1)
            return filterY1 * env * gain
        }
    }

    // 10. SuperSaw Voice
    class SuperSawVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val attackMs: Float = 10f,
        val releaseMs: Float = 120f
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.08f))).toInt()
        private val phases = DoubleArray(5)
        private val incs = doubleArrayOf(0.991, 0.996, 1.0, 1.004, 1.009).let { d ->
            DoubleArray(5) { k -> phaseInc(freq * d[k]) }
        }
        private val attack = (attackMs / 1000f).coerceIn(0.005f, 0.08f)
        private val invAttack = 1f / attack
        private val invRelease = 1f / (releaseMs / 1000f).coerceAtLeast(0.05f)
        private val gain = velocity * 0.68f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            var phaseSum = 0.0
            for (k in 0 until 5) {
                var p = phases[k] + incs[k]
                if (p >= TWO_PI) p -= TWO_PI
                phases[k] = p
                phaseSum += p
            }
            // sum of (1 - p/PI) over 5 oscillators
            val raw = (5.0 - phaseSum / PI).toFloat() * 0.2f

            val env = when {
                t < attack -> t * invAttack
                t < duration -> 0.85f
                else -> (0.85f * (1f - (t - duration) * invRelease)).coerceAtLeast(0f)
            }

            return raw * env * gain
        }
    }

    // 11. Organ Voice
    class OrganVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val cutoffHz: Float = 7500f,
        val attackMs: Float = 3f,
        val releaseMs: Float = 60f
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.05f))).toInt()
        private var p1 = 0.0
        private var p2 = 0.0
        private var p3 = 0.0
        private var p4 = 0.0
        private val i1 = phaseInc(freq.toDouble())
        private val i2 = phaseInc(freq * 2.0)
        private val i3 = phaseInc(freq * 3.0)
        private val i4 = phaseInc(freq * 4.0)
        private val attack = (attackMs / 1000f).coerceAtLeast(0.005f)
        private val invAttack = 1f / attack
        private val invRelease = 1f / (releaseMs / 1000f).coerceAtLeast(0.02f)
        private val gain = velocity * 0.65f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            p1 += i1; p2 += i2; p3 += i3; p4 += i4
            if (p1 >= TWO_PI) p1 -= TWO_PI
            if (p2 >= TWO_PI) p2 -= TWO_PI
            if (p3 >= TWO_PI) p3 -= TWO_PI
            if (p4 >= TWO_PI) p4 -= TWO_PI

            val raw = fastSin(p1) * 0.5f + fastSin(p2) * 0.3f + fastSin(p3) * 0.15f + fastSin(p4) * 0.1f

            val env = when {
                t < attack -> t * invAttack
                t < duration -> 0.9f
                else -> (0.9f * (1f - (t - duration) * invRelease)).coerceAtLeast(0f)
            }

            return raw * env * gain
        }
    }

    // 12. Pluck Voice
    class PluckVoice(
        val freq: Float,
        val duration: Float,
        val velocity: Float,
        override val pan: Float,
        val cutoffHz: Float = 4500f,
        val resonance: Float = 1.3f,
        val attackMs: Float = 2f,
        val releaseMs: Float = 180f,
        val waveform: SynthWaveform = SynthWaveform.TRIANGLE
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (duration + (releaseMs / 1000f).coerceAtLeast(0.05f))).toInt()
        private var phase = 0.0
        private val inc = phaseInc(freq.toDouble())
        private val isSquare = waveform == SynthWaveform.SQUARE
        private var pluckEnv = 1.0
        private var clickEnv = 1.0
        private val pluckK = decayCoeff(26f)
        private val clickK = decayCoeff(80f)
        private val gain = velocity * 0.7f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            sampleIndex++

            phase += inc
            if (phase >= TWO_PI) phase -= TWO_PI

            val raw = if (isSquare) {
                if (phase < PI) 1f else -1f
            } else {
                (1f - (phase / PI).toFloat())
            }

            val pe = pluckEnv.toFloat()
            val click = pseudoNoise(sampleIndex) * clickEnv.toFloat() * 0.35f
            pluckEnv *= pluckK
            clickEnv *= clickK

            return (raw * 0.8f + click) * pe * gain
        }
    }

    // =========================================================================
    // DEDICATED DRUM VOICES
    // =========================================================================

    // 1. Kick Voice (Deep, Sub, Punch, Afro House, etc.)
    class KickVoice(
        val type: String,
        val velocity: Float,
        override val pan: Float
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val isDeep = type.contains("deep") || type.contains("808")
        private val totalSamples = (SAMPLE_RATE * (if (isDeep || type.contains("sub")) 0.55 else 0.35)).toInt()
        private var phase = 0.0
        private val startFreq = when {
            type.contains("punch") -> 220f
            type.contains("tech") -> 160f
            type.contains("afro") -> 130f
            else -> 110f
        }
        private val endFreq = when {
            isDeep -> 35f
            type.contains("sub") -> 42f
            else -> 50f
        }
        private val sweepRange = startFreq - endFreq
        private var sweepEnv = 1.0
        private val sweepK = decayCoeff(26f)
        private var ampEnv = 1.0
        private val ampK = decayCoeff(if (isDeep) 6.0f else 8.5f)

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            val freq = endFreq + sweepRange * sweepEnv.toFloat()
            sweepEnv *= sweepK
            phase += (TWO_PI * freq) / SAMPLE_RATE
            if (phase >= TWO_PI) phase -= TWO_PI

            val osc = fastSin(phase)
            val click = if (t < 0.005f) pseudoNoise(sampleIndex) * (1f - t / 0.005f) * 0.45f else 0f
            val env = ampEnv.toFloat()
            ampEnv *= ampK

            return (osc * 0.85f + click) * env * velocity
        }
    }

    // 2. Snare Voice (Tight, Deep, Acoustic, Rim)
    class SnareVoice(
        val type: String,
        val velocity: Float,
        override val pan: Float
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * 0.35).toInt()
        private var phase = 0.0
        private val bodyInc = phaseInc(if (type.contains("deep")) 150.0 else 200.0)
        private var e12 = 1.0
        private var e18 = 1.0
        private var e14 = 1.0
        private val k12 = decayCoeff(12f)
        private val k18 = decayCoeff(18f)
        private val k14 = decayCoeff(14f)

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            sampleIndex++

            phase += bodyInc * e12
            if (phase >= TWO_PI) phase -= TWO_PI

            val tone = fastSin(phase) * e18.toFloat()
            val noise = pseudoNoise(sampleIndex) * e14.toFloat()
            e12 *= k12; e18 *= k18; e14 *= k14

            return (tone * 0.45f + noise * 0.75f) * velocity
        }
    }

    // 3. Clap Voice
    class ClapVoice(
        val type: String,
        val velocity: Float,
        override val pan: Float
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * 0.28).toInt()
        private val gain = velocity * 0.8f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            val burstEnv = when {
                t < 0.012f -> exp(-t * 90f)
                t < 0.024f -> exp(-(t - 0.012f) * 90f)
                t < 0.036f -> exp(-(t - 0.024f) * 90f)
                else -> exp(-(t - 0.036f) * 18f)
            }
            val noise = pseudoNoise(sampleIndex)
            return noise * burstEnv * gain
        }
    }

    // 4. Hi-Hat Voice (Closed / Open)
    class HiHatVoice(
        val isOpen: Boolean,
        val velocity: Float,
        override val pan: Float
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (if (isOpen) 0.42 else 0.055)).toInt()
        private val invTotal = 1f / totalSamples
        private var metalPhase = 0.0
        private val metalInc = phaseInc(8200.0)
        private var openEnv = 1.0
        private val openK = decayCoeff(8.5f)
        private val gain = velocity * 0.65f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            sampleIndex++

            return if (isOpen) {
                metalPhase += metalInc
                if (metalPhase >= TWO_PI) metalPhase -= TWO_PI
                val sizzle = fastSin(metalPhase) * 0.4f
                val noise = pseudoNoise(sampleIndex * 5 + 97)
                val env = openEnv.toFloat()
                openEnv *= openK
                (noise * 0.65f + sizzle) * env * gain
            } else {
                val noise = pseudoNoise(sampleIndex)
                val lin = 1f - sampleIndex * invTotal
                noise * (lin * lin) * gain
            }
        }
    }

    // 5. Tom Voice
    class TomVoice(
        val type: String,
        val velocity: Float,
        override val pan: Float
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * 0.38).toInt()
        private var phase = 0.0
        private val baseInc = phaseInc(
            when {
                type.contains("low") -> 90.0
                type.contains("high") -> 180.0
                else -> 130.0
            }
        )
        private var pitchEnv = 1.0
        private var ampEnv = 1.0
        private val pitchK = decayCoeff(8f)
        private val ampK = decayCoeff(7f)
        private val gain = velocity * 0.85f

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            sampleIndex++

            phase += baseInc * pitchEnv
            if (phase >= TWO_PI) phase -= TWO_PI
            val out = fastSin(phase) * ampEnv.toFloat() * gain
            pitchEnv *= pitchK
            ampEnv *= ampK
            return out
        }
    }

    // 6. Cymbal Voice (Crash, Ride, Splash, China)
    class CymbalVoice(
        val type: String,
        val velocity: Float,
        override val pan: Float
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * (if (type.contains("crash") || type.contains("china")) 1.4 else 0.8)).toInt()
        private var phase = 0.0
        private val isRide = type.contains("ride")
        private val pingInc = phaseInc(4200.0)
        private var pingEnv = 1.0
        private var noiseEnv = 1.0
        private val pingK = decayCoeff(22f)
        private val noiseK = decayCoeff(if (isRide) 4.5f else 3.2f)

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            sampleIndex++

            return if (isRide) {
                phase += pingInc
                if (phase >= TWO_PI) phase -= TWO_PI
                val ping = fastSin(phase) * pingEnv.toFloat()
                val noise = pseudoNoise(sampleIndex) * noiseEnv.toFloat()
                pingEnv *= pingK
                noiseEnv *= noiseK
                (ping * 0.55f + noise * 0.35f) * velocity * 0.65f
            } else {
                val noise = pseudoNoise(sampleIndex)
                val env = noiseEnv.toFloat()
                noiseEnv *= noiseK
                noise * env * velocity * 0.68f
            }
        }
    }

    // 7. World & Afro Percussion Voice (Djembe, Conga, Bongo, Shaker, etc.)
    class WorldPercussionVoice(
        val type: String,
        val velocity: Float,
        override val pan: Float
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * 0.42).toInt()
        private var phase = 0.0

        // Resolve the percussion model once (same priority order as before)
        private val mode = when {
            type.contains("djembe") -> M_DJEMBE
            type.contains("conga") || type.contains("bongo") -> M_CONGA
            type.contains("shaker") || type.contains("shekere") || type.contains("maraca") -> M_SHAKER
            type.contains("talking") -> M_TALKING
            type.contains("cowbell") -> M_COWBELL
            type.contains("claves") || type.contains("woodblock") -> M_CLAVES
            type.contains("darbuka") || type.contains("tabla") -> M_DARBUKA
            else -> M_DEFAULT
        }
        private val congaHigh = type.contains("high") || type.contains("bongo")
        private val clavesInc = phaseInc(if (type.contains("claves")) 2600.0 else 850.0)
        private val cowbellInc = phaseInc(580.0)

        // Up to three independent exponential decays per model: exp(-t * rate)
        private val rateA: Float
        private val rateB: Float
        private val rateC: Float
        init {
            when (mode) {
                M_DJEMBE -> { rateA = 22f; rateB = 10f; rateC = 60f }
                M_CONGA -> { rateA = if (congaHigh) 18f else 16f; rateB = 13f; rateC = 0f }
                M_SHAKER -> { rateA = 32f; rateB = 0f; rateC = 0f }
                M_TALKING -> { rateA = 8f; rateB = 0f; rateC = 0f }
                M_COWBELL -> { rateA = 14f; rateB = 0f; rateC = 0f }
                M_CLAVES -> { rateA = 45f; rateB = 0f; rateC = 0f }
                M_DARBUKA -> { rateA = 16f; rateB = 12f; rateC = 20f }
                else -> { rateA = 20f; rateB = 12f; rateC = 0f }
            }
        }
        private var eA = 1.0
        private var eB = 1.0
        private var eC = 1.0
        private val kA = decayCoeff(rateA)
        private val kB = decayCoeff(rateB)
        private val kC = decayCoeff(rateC)

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val t = sampleIndex * INV_SR
            sampleIndex++

            val a = eA.toFloat()
            val b = eB.toFloat()
            val c = eC.toFloat()
            eA *= kA; eB *= kB; eC *= kC

            val sample = when (mode) {
                M_DJEMBE -> {
                    val f = 65f + 160f * a
                    phase += (TWO_PI * f) / SAMPLE_RATE
                    if (phase >= TWO_PI) phase -= TWO_PI
                    val thud = fastSin(phase) * b
                    val slap = pseudoNoise(sampleIndex) * c * 0.4f
                    (thud * 0.85f + slap)
                }
                M_CONGA -> {
                    val f = if (congaHigh) 280f * a else 180f * a
                    phase += (TWO_PI * f) / SAMPLE_RATE
                    if (phase >= TWO_PI) phase -= TWO_PI
                    val tone = fastSin(phase) * b
                    val slap = if (t < 0.005f) pseudoNoise(sampleIndex) * 0.5f else 0f
                    (tone * 0.85f + slap)
                }
                M_SHAKER -> {
                    val noise = pseudoNoise(sampleIndex * 13 + 43)
                    val grain = pseudoNoise(sampleIndex * 7 + 19) * 0.3f
                    (noise * 0.7f + grain) * a * 0.65f
                }
                M_TALKING -> {
                    // Talking drum bends up then down
                    val bend = if (t < 0.12f) t / 0.12f else (1f - (t - 0.12f) / 0.28f).coerceAtLeast(0f)
                    val f = 140f + 110f * bend
                    phase += (TWO_PI * f) / SAMPLE_RATE
                    if (phase >= TWO_PI) phase -= TWO_PI
                    fastSin(phase) * a * 0.85f
                }
                M_COWBELL -> {
                    phase += cowbellInc
                    if (phase >= TWO_PI) phase -= TWO_PI
                    val p2 = phase * 1.5
                    val square1 = if (phase < PI) 1f else -1f
                    val square2 = if ((if (p2 >= TWO_PI) p2 - TWO_PI else p2) < PI) 1f else -1f
                    (square1 * 0.6f + square2 * 0.4f) * a * 0.6f
                }
                M_CLAVES -> {
                    phase += clavesInc
                    if (phase >= TWO_PI) phase -= TWO_PI
                    fastSin(phase) * a * 0.8f
                }
                M_DARBUKA -> {
                    val f = 320f * a
                    phase += (TWO_PI * f) / SAMPLE_RATE
                    if (phase >= TWO_PI) phase -= TWO_PI
                    val body = fastSin(phase) * b
                    val rim = fastSin(phase * 2.3) * c * 0.4f
                    (body * 0.7f + rim * 0.5f)
                }
                else -> {
                    val f = 160f * a
                    phase += (TWO_PI * f) / SAMPLE_RATE
                    if (phase >= TWO_PI) phase -= TWO_PI
                    fastSin(phase) * b
                }
            }

            return sample * velocity
        }
    }

    // 8. Sound Effects / Textures (Impact, Rise, Downlifter, Vinyl, etc.)
    class FXVoice(
        val type: String,
        val velocity: Float,
        override val pan: Float
    ) : ActiveVoice {
        private var sampleIndex = 0
        private val totalSamples = (SAMPLE_RATE * 2.0).toInt()
        private val invTotal = 1f / totalSamples
        private var phase = 0.0
        private val mode = when {
            type.contains("rise") -> FX_RISE
            type.contains("downlifter") -> FX_DOWN
            type.contains("impact") -> FX_IMPACT
            type.contains("vinyl") -> FX_VINYL
            else -> FX_NOISE
        }
        private var eA = 1.0
        private var eB = 1.0
        private val kA = decayCoeff(
            when (mode) {
                FX_IMPACT -> 6f
                FX_NOISE -> 4f
                else -> 0f
            }
        )
        private val kB = decayCoeff(if (mode == FX_IMPACT) 3.5f else 0f)
        private var eC = 1.0
        private val kC = decayCoeff(if (mode == FX_IMPACT) 8f else 0f)

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (sampleIndex >= totalSamples) return 0f
            val progress = sampleIndex * invTotal
            sampleIndex++

            val a = eA.toFloat()
            val b = eB.toFloat()
            val c = eC.toFloat()
            eA *= kA; eB *= kB; eC *= kC

            val s = when (mode) {
                FX_RISE -> {
                    val sweepFreq = 120f + 4800f * (progress * progress)
                    phase += (TWO_PI * sweepFreq) / SAMPLE_RATE
                    if (phase >= TWO_PI) phase -= TWO_PI
                    val tone = fastSin(phase) * progress
                    val noise = pseudoNoise(sampleIndex) * progress * 0.4f
                    (tone * 0.6f + noise) * progress
                }
                FX_DOWN -> {
                    val inv = 1f - progress
                    val sweepFreq = 3000f * (inv * inv) + 60f
                    phase += (TWO_PI * sweepFreq) / SAMPLE_RATE
                    if (phase >= TWO_PI) phase -= TWO_PI
                    fastSin(phase) * inv
                }
                FX_IMPACT -> {
                    val boomFreq = 120f * a + 35f
                    phase += (TWO_PI * boomFreq) / SAMPLE_RATE
                    if (phase >= TWO_PI) phase -= TWO_PI
                    val boom = fastSin(phase) * b
                    val noise = pseudoNoise(sampleIndex) * c * 0.5f
                    boom * 0.8f + noise
                }
                FX_VINYL -> {
                    val crackle = if (abs(pseudoNoise(sampleIndex * 3)) > 0.985f) pseudoNoise(sampleIndex * 7) * 0.4f else 0f
                    val hiss = pseudoNoise(sampleIndex) * 0.04f
                    crackle + hiss
                }
                else -> {
                    val noise = pseudoNoise(sampleIndex)
                    noise * a * 0.6f
                }
            }
            return s * velocity
        }
    }

    // Lead waveform modes (SynthWaveform ordinal)
    private val WAVE_SINE = SynthWaveform.SINE.ordinal
    private val WAVE_SAW = SynthWaveform.SAWTOOTH.ordinal
    private val WAVE_SQUARE = SynthWaveform.SQUARE.ordinal
    private val WAVE_TRIANGLE = SynthWaveform.TRIANGLE.ordinal

    // World percussion models
    private const val M_DJEMBE = 0
    private const val M_CONGA = 1
    private const val M_SHAKER = 2
    private const val M_TALKING = 3
    private const val M_COWBELL = 4
    private const val M_CLAVES = 5
    private const val M_DARBUKA = 6
    private const val M_DEFAULT = 7

    // FX models
    private const val FX_RISE = 0
    private const val FX_DOWN = 1
    private const val FX_IMPACT = 2
    private const val FX_VINYL = 3
    private const val FX_NOISE = 4
}
