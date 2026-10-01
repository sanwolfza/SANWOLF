package com.example.audio

import com.example.model.SynthWaveform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Enterprise Audio Voice Generators for SANWOLF Audio Studio.
 * Shared between live playback (SanwolfAudioEngine) and offline render (WavExporter, MixerRenderer),
 * guaranteeing 1:1 identical sound reproduction.
 */
object InstrumentVoices {
    const val SAMPLE_RATE = 44100

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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            phase1 += (2.0 * PI * freq) / SAMPLE_RATE
            phase2 += (2.0 * PI * freq * 2.002) / SAMPLE_RATE
            phase3 += (2.0 * PI * freq * 3.005) / SAMPLE_RATE

            if (phase1 >= 2.0 * PI) phase1 -= 2.0 * PI
            if (phase2 >= 2.0 * PI) phase2 -= 2.0 * PI
            if (phase3 >= 2.0 * PI) phase3 -= 2.0 * PI

            val hammerClick = if (t < 0.004f) pseudoNoise(sampleIndex) * (1f - t / 0.004f) * 0.35f else 0f
            val fundamental = sin(phase1).toFloat()
            val overtone2 = sin(phase2).toFloat() * 0.4f
            val overtone3 = sin(phase3).toFloat() * 0.2f
            val raw = fundamental + overtone2 + overtone3 + hammerClick

            val attack = (attackMs / 1000f).coerceAtLeast(0.002f)
            val decay = (decayMs / 1000f).coerceAtLeast(0.05f)
            val env = when {
                t < attack -> t / attack
                else -> exp(-((t - attack) / decay) * 3.5f).coerceAtLeast(0.02f)
            }

            return raw * env * velocity * 0.65f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            p1 += (2.0 * PI * freq * 1.0) / SAMPLE_RATE
            p2 += (2.0 * PI * freq * 2.76) / SAMPLE_RATE
            p3 += (2.0 * PI * freq * 5.40) / SAMPLE_RATE
            p4 += (2.0 * PI * freq * 8.93) / SAMPLE_RATE
            if (p1 >= 2.0 * PI) p1 -= 2.0 * PI
            if (p2 >= 2.0 * PI) p2 -= 2.0 * PI
            if (p3 >= 2.0 * PI) p3 -= 2.0 * PI
            if (p4 >= 2.0 * PI) p4 -= 2.0 * PI

            val s1 = sin(p1).toFloat() * exp(-t * 2.5f)
            val s2 = sin(p2).toFloat() * exp(-t * 4.5f) * 0.5f
            val s3 = sin(p3).toFloat() * exp(-t * 7.0f) * 0.3f
            val s4 = sin(p4).toFloat() * exp(-t * 11.0f) * 0.15f
            val strike = if (t < 0.003f) pseudoNoise(sampleIndex) * 0.25f else 0f

            return (s1 + s2 + s3 + s4 + strike) * velocity * 0.65f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val overtoneRatio = if (type.contains("kalimba")) 3.0 else 4.0
            p1 += (2.0 * PI * freq) / SAMPLE_RATE
            p2 += (2.0 * PI * freq * overtoneRatio) / SAMPLE_RATE
            if (p1 >= 2.0 * PI) p1 -= 2.0 * PI
            if (p2 >= 2.0 * PI) p2 -= 2.0 * PI

            val decayRate = if (type.contains("kalimba")) 4.0f else 8.5f
            val tone1 = sin(p1).toFloat() * exp(-t * decayRate)
            val tone2 = sin(p2).toFloat() * exp(-t * (decayRate * 2.2f)) * 0.35f
            val click = if (t < 0.003f) pseudoNoise(sampleIndex) * 0.4f else 0f

            return (tone1 + tone2 + click) * velocity * 0.7f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val vibrato = 1.0f + 0.012f * sin(t * 2.0 * PI * 5.5).toFloat()
            ph1 += (2.0 * PI * freq * 0.998 * vibrato) / SAMPLE_RATE
            ph2 += (2.0 * PI * freq * 1.000 * vibrato) / SAMPLE_RATE
            ph3 += (2.0 * PI * freq * 1.002 * vibrato) / SAMPLE_RATE
            if (ph1 >= 2.0 * PI) ph1 -= 2.0 * PI
            if (ph2 >= 2.0 * PI) ph2 -= 2.0 * PI
            if (ph3 >= 2.0 * PI) ph3 -= 2.0 * PI

            val saw1 = 1f - (ph1 / PI).toFloat()
            val saw2 = 1f - (ph2 / PI).toFloat()
            val saw3 = 1f - (ph3 / PI).toFloat()
            val raw = (saw1 + saw2 + saw3) * 0.333f

            val attack = (attackMs / 1000f).coerceIn(0.04f, 0.4f)
            val release = (releaseMs / 1000f).coerceAtLeast(0.1f)
            val env = when {
                t < attack -> t / attack
                t < duration -> 0.9f
                else -> (0.9f * (1f - (t - duration) / release)).coerceAtLeast(0f)
            }

            filterY1 += 0.14f * (raw - filterY1)
            return filterY1 * env * velocity * 0.7f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            ph1 += (2.0 * PI * freq) / SAMPLE_RATE
            ph2 += (2.0 * PI * freq * 1.003) / SAMPLE_RATE
            if (ph1 >= 2.0 * PI) ph1 -= 2.0 * PI
            if (ph2 >= 2.0 * PI) ph2 -= 2.0 * PI

            val saw1 = 1f - (ph1 / PI).toFloat()
            val saw2 = 1f - (ph2 / PI).toFloat()
            val raw = (saw1 * 0.7f + saw2 * 0.3f)

            val attack = (attackMs / 1000f).coerceIn(0.015f, 0.12f)
            val release = (releaseMs / 1000f).coerceAtLeast(0.05f)
            val env = when {
                t < attack -> t / attack
                t < duration -> 0.85f
                else -> (0.85f * (1f - (t - duration) / release)).coerceAtLeast(0f)
            }

            val bite = exp(-t * 18f) * 0.25f
            val cutoffNorm = (0.20f + bite).coerceIn(0.05f, 0.45f)
            filterY1 += cutoffNorm * (raw - filterY1)
            return filterY1 * env * velocity * 0.75f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val vibrato = 1.0f + 0.01f * sin(t * 2.0 * PI * 5.0).toFloat()
            ph += (2.0 * PI * freq * vibrato) / SAMPLE_RATE
            if (ph >= 2.0 * PI) ph -= 2.0 * PI

            val sine = sin(ph).toFloat()
            val breathNoise = pseudoNoise(sampleIndex) * 0.08f * exp(-t * 8f)
            val raw = sine * 0.92f + breathNoise

            val attack = (attackMs / 1000f).coerceIn(0.02f, 0.15f)
            val release = (releaseMs / 1000f).coerceAtLeast(0.05f)
            val env = when {
                t < attack -> t / attack
                t < duration -> 0.9f
                else -> (0.9f * (1f - (t - duration) / release)).coerceAtLeast(0f)
            }

            return raw * env * velocity * 0.65f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val effectiveFreq = if (vType.contains("808") && t < 0.05f) {
                freq * (1f + 2f * (1f - t / 0.05f))
            } else {
                freq
            }

            ph1 += (2.0 * PI * effectiveFreq) / SAMPLE_RATE
            ph2 += (2.0 * PI * effectiveFreq * 1.008) / SAMPLE_RATE
            if (ph1 >= 2.0 * PI) ph1 -= 2.0 * PI
            if (ph2 >= 2.0 * PI) ph2 -= 2.0 * PI

            val raw = when {
                vType.contains("sub") || vType.contains("808") -> sin(ph1).toFloat()
                vType.contains("reese") -> ((1f - (ph1 / PI).toFloat()) + (1f - (ph2 / PI).toFloat())) * 0.5f
                else -> sin(ph1).toFloat() * 0.7f + (1f - (ph1 / PI).toFloat()) * 0.3f
            }

            val attack = (attackMs / 1000f).coerceAtLeast(0.005f)
            val decay = (decayMs / 1000f).coerceAtLeast(0.05f)
            val env = when {
                t < attack -> t / attack
                t < attack + decay -> 1f - (1f - sustainLevel) * ((t - attack) / decay)
                t < duration -> sustainLevel
                else -> (sustainLevel * (1f - (t - duration) / (releaseMs / 1000f).coerceAtLeast(0.02f))).coerceAtLeast(0f)
            }

            val cutoffNorm = (cutoffHz / SAMPLE_RATE).coerceIn(0.02f, 0.45f)
            filterY1 += cutoffNorm * (raw - filterY1)
            return filterY1 * env * velocity * 0.85f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            ph1 += (2.0 * PI * freq) / SAMPLE_RATE
            ph2 += (2.0 * PI * freq * 1.004) / SAMPLE_RATE
            if (ph1 >= 2.0 * PI) ph1 -= 2.0 * PI
            if (ph2 >= 2.0 * PI) ph2 -= 2.0 * PI

            val saw1 = 1f - (ph1 / PI).toFloat()
            val saw2 = 1f - (ph2 / PI).toFloat()
            val raw = (saw1 + saw2) * 0.5f

            val attack = (attackMs / 1000f).coerceAtLeast(0.15f)
            val release = (releaseMs / 1000f).coerceAtLeast(0.25f)
            val env = when {
                t < attack -> t / attack
                t < duration -> 0.82f + 0.18f * sin(t * 2.0 * PI * 0.5f).toFloat()
                else -> (0.82f * (1f - (t - duration) / release)).coerceAtLeast(0f)
            }

            val cutoffNorm = (cutoffHz / SAMPLE_RATE).coerceIn(0.01f, 0.35f)
            filterY1 += cutoffNorm * (raw - filterY1)
            return filterY1 * env * velocity * 0.65f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val vibrato = 1.0f + 0.015f * sin(t * 2.0 * PI * 5.5).toFloat()
            phase += (2.0 * PI * freq * vibrato) / SAMPLE_RATE
            if (phase >= 2.0 * PI) phase -= 2.0 * PI

            val raw = when (waveform) {
                SynthWaveform.SINE -> sin(phase).toFloat()
                SynthWaveform.SAWTOOTH -> (1f - (phase / PI).toFloat())
                SynthWaveform.SQUARE -> if (phase < PI) 1f else -1f
                SynthWaveform.TRIANGLE -> {
                    val p = (phase / (2.0 * PI)).toFloat()
                    if (p < 0.5f) 4f * p - 1f else 3f - 4f * p
                }
                SynthWaveform.NOISE -> pseudoNoise(sampleIndex)
            }

            val attack = (attackMs / 1000f).coerceAtLeast(0.005f)
            val decay = (decayMs / 1000f).coerceAtLeast(0.05f)
            val env = when {
                t < attack -> t / attack
                t < attack + decay -> 1f - (1f - sustainLevel) * ((t - attack) / decay)
                t < duration -> sustainLevel
                else -> (sustainLevel * (1f - (t - duration) / (releaseMs / 1000f).coerceAtLeast(0.02f))).coerceAtLeast(0f)
            }

            val cutoffNorm = (cutoffHz / SAMPLE_RATE).coerceIn(0.02f, 0.49f)
            filterY1 += cutoffNorm * (raw - filterY1)
            return filterY1 * env * velocity * 0.72f
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
        private val detuneFactors = doubleArrayOf(0.991, 0.996, 1.0, 1.004, 1.009)

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            var sum = 0f
            for (k in 0 until 5) {
                phases[k] += (2.0 * PI * freq * detuneFactors[k]) / SAMPLE_RATE
                if (phases[k] >= 2.0 * PI) phases[k] -= 2.0 * PI
                sum += (1f - (phases[k] / PI).toFloat())
            }
            val raw = sum * 0.2f

            val attack = (attackMs / 1000f).coerceIn(0.005f, 0.08f)
            val release = (releaseMs / 1000f).coerceAtLeast(0.05f)
            val env = when {
                t < attack -> t / attack
                t < duration -> 0.85f
                else -> (0.85f * (1f - (t - duration) / release)).coerceAtLeast(0f)
            }

            return raw * env * velocity * 0.68f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            p1 += (2.0 * PI * freq) / SAMPLE_RATE
            p2 += (2.0 * PI * freq * 2.0) / SAMPLE_RATE
            p3 += (2.0 * PI * freq * 3.0) / SAMPLE_RATE
            p4 += (2.0 * PI * freq * 4.0) / SAMPLE_RATE
            if (p1 >= 2.0 * PI) p1 -= 2.0 * PI
            if (p2 >= 2.0 * PI) p2 -= 2.0 * PI
            if (p3 >= 2.0 * PI) p3 -= 2.0 * PI
            if (p4 >= 2.0 * PI) p4 -= 2.0 * PI

            val raw = (sin(p1) * 0.5f + sin(p2) * 0.3f + sin(p3) * 0.15f + sin(p4) * 0.1f).toFloat()

            val attack = (attackMs / 1000f).coerceAtLeast(0.005f)
            val release = (releaseMs / 1000f).coerceAtLeast(0.02f)
            val env = when {
                t < attack -> t / attack
                t < duration -> 0.9f
                else -> (0.9f * (1f - (t - duration) / release)).coerceAtLeast(0f)
            }

            return raw * env * velocity * 0.65f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            phase += (2.0 * PI * freq) / SAMPLE_RATE
            if (phase >= 2.0 * PI) phase -= 2.0 * PI

            val raw = when (waveform) {
                SynthWaveform.SQUARE -> if (phase < PI) 1f else -1f
                else -> (1f - (phase / PI).toFloat())
            }

            val pluckEnv = exp(-t * 26f)
            val click = pseudoNoise(sampleIndex) * exp(-t * 80f) * 0.35f

            return (raw * 0.8f + click) * pluckEnv * velocity * 0.7f
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
        private val totalSamples = (SAMPLE_RATE * (if (type.contains("deep") || type.contains("sub") || type.contains("808")) 0.55 else 0.35)).toInt()
        private var phase = 0.0

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val startFreq = when {
                type.contains("punch") -> 220f
                type.contains("tech") -> 160f
                type.contains("afro") -> 130f
                else -> 110f
            }
            val endFreq = when {
                type.contains("deep") || type.contains("808") -> 35f
                type.contains("sub") -> 42f
                else -> 50f
            }

            val freq = endFreq + (startFreq - endFreq) * exp(-t * 26f)
            phase += (2.0 * PI * freq) / SAMPLE_RATE
            if (phase >= 2.0 * PI) phase -= 2.0 * PI

            val osc = sin(phase).toFloat()
            val click = if (t < 0.005f) pseudoNoise(sampleIndex) * (1f - t / 0.005f) * 0.45f else 0f
            val env = exp(-t * (if (type.contains("deep") || type.contains("808")) 6.0f else 8.5f))

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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val bodyFreq = if (type.contains("deep")) 150f else 200f
            phase += (2.0 * PI * bodyFreq * exp(-t * 12f)) / SAMPLE_RATE
            if (phase >= 2.0 * PI) phase -= 2.0 * PI

            val tone = sin(phase).toFloat() * exp(-t * 18f)
            val noise = pseudoNoise(sampleIndex) * exp(-t * 14f)

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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val burstEnv = when {
                t < 0.012f -> exp(-t * 90f)
                t < 0.024f -> exp(-(t - 0.012f) * 90f)
                t < 0.036f -> exp(-(t - 0.024f) * 90f)
                else -> exp(-(t - 0.036f) * 18f)
            }
            val noise = pseudoNoise(sampleIndex)
            return noise * burstEnv * velocity * 0.8f
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
        private var metalPhase = 0.0

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            return if (isOpen) {
                metalPhase += (2.0 * PI * 8200.0) / SAMPLE_RATE
                if (metalPhase >= 2.0 * PI) metalPhase -= 2.0 * PI
                val sizzle = sin(metalPhase).toFloat() * 0.4f
                val noise = pseudoNoise(sampleIndex * 5 + 97)
                val env = exp(-t * 8.5f)
                (noise * 0.65f + sizzle) * env * velocity * 0.65f
            } else {
                val noise = pseudoNoise(sampleIndex)
                val env = (1f - (sampleIndex.toFloat() / totalSamples)).pow(2)
                noise * env * velocity * 0.65f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val baseFreq = when {
                type.contains("low") -> 90f
                type.contains("high") -> 180f
                else -> 130f
            }
            val freq = baseFreq * exp(-t * 8f)
            phase += (2.0 * PI * freq) / SAMPLE_RATE
            if (phase >= 2.0 * PI) phase -= 2.0 * PI

            return sin(phase).toFloat() * exp(-t * 7.0f) * velocity * 0.85f
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            return if (type.contains("ride")) {
                phase += (2.0 * PI * 4200.0) / SAMPLE_RATE
                if (phase >= 2.0 * PI) phase -= 2.0 * PI
                val ping = sin(phase).toFloat() * exp(-t * 22f)
                val noise = pseudoNoise(sampleIndex) * exp(-t * 4.5f)
                (ping * 0.55f + noise * 0.35f) * velocity * 0.65f
            } else {
                val noise = pseudoNoise(sampleIndex)
                val env = exp(-t * 3.2f)
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

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            sampleIndex++

            val sample = when {
                type.contains("djembe") -> {
                    val f = 65f + 160f * exp(-t * 22f)
                    phase += (2.0 * PI * f) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    val thud = sin(phase).toFloat() * exp(-t * 10f)
                    val slap = pseudoNoise(sampleIndex) * exp(-t * 60f) * 0.4f
                    (thud * 0.85f + slap)
                }
                type.contains("conga") || type.contains("bongo") -> {
                    val isHigh = type.contains("high") || type.contains("bongo")
                    val f = if (isHigh) 280f * exp(-t * 18f) else 180f * exp(-t * 16f)
                    phase += (2.0 * PI * f) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    val tone = sin(phase).toFloat() * exp(-t * 13f)
                    val slap = if (t < 0.005f) pseudoNoise(sampleIndex) * 0.5f else 0f
                    (tone * 0.85f + slap)
                }
                type.contains("shaker") || type.contains("shekere") || type.contains("maraca") -> {
                    val noise = pseudoNoise(sampleIndex * 13 + 43)
                    val grain = pseudoNoise(sampleIndex * 7 + 19) * 0.3f
                    val env = exp(-t * 32f)
                    (noise * 0.7f + grain) * env * 0.65f
                }
                type.contains("talking") -> {
                    // Talking drum bends up then down
                    val bend = if (t < 0.12f) t / 0.12f else (1f - (t - 0.12f) / 0.28f).coerceAtLeast(0f)
                    val f = 140f + 110f * bend
                    phase += (2.0 * PI * f) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    sin(phase).toFloat() * exp(-t * 8f) * 0.85f
                }
                type.contains("cowbell") -> {
                    phase += (2.0 * PI * 580.0) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    val p2 = phase * 1.5
                    val square1 = if (phase < PI) 1f else -1f
                    val square2 = if (p2 % (2.0 * PI) < PI) 1f else -1f
                    (square1 * 0.6f + square2 * 0.4f) * exp(-t * 14f) * 0.6f
                }
                type.contains("claves") || type.contains("woodblock") -> {
                    val f = if (type.contains("claves")) 2600.0 else 850.0
                    phase += (2.0 * PI * f) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    sin(phase).toFloat() * exp(-t * 45f) * 0.8f
                }
                type.contains("darbuka") || type.contains("tabla") -> {
                    val f = 320f * exp(-t * 16f)
                    phase += (2.0 * PI * f) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    val body = sin(phase).toFloat() * exp(-t * 12f)
                    val rim = sin(phase * 2.3).toFloat() * exp(-t * 20f) * 0.4f
                    (body * 0.7f + rim * 0.5f)
                }
                else -> {
                    val f = 160f * exp(-t * 20f)
                    phase += (2.0 * PI * f) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    sin(phase).toFloat() * exp(-t * 12f)
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
        private var phase = 0.0

        override val isFinished: Boolean get() = sampleIndex >= totalSamples

        override fun nextSample(): Float {
            if (isFinished) return 0f
            val t = sampleIndex.toFloat() / SAMPLE_RATE
            val progress = sampleIndex.toFloat() / totalSamples
            sampleIndex++

            val s = when {
                type.contains("rise") -> {
                    val sweepFreq = 120f + 4800f * progress.pow(2)
                    phase += (2.0 * PI * sweepFreq) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    val tone = sin(phase).toFloat() * progress
                    val noise = pseudoNoise(sampleIndex) * progress * 0.4f
                    (tone * 0.6f + noise) * progress
                }
                type.contains("downlifter") -> {
                    val sweepFreq = 3000f * (1f - progress).pow(2) + 60f
                    phase += (2.0 * PI * sweepFreq) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    sin(phase).toFloat() * (1f - progress)
                }
                type.contains("impact") -> {
                    val boomFreq = 120f * exp(-t * 6f) + 35f
                    phase += (2.0 * PI * boomFreq) / SAMPLE_RATE
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    val boom = sin(phase).toFloat() * exp(-t * 3.5f)
                    val noise = pseudoNoise(sampleIndex) * exp(-t * 8f) * 0.5f
                    boom * 0.8f + noise
                }
                type.contains("vinyl") -> {
                    val crackle = if (abs(pseudoNoise(sampleIndex * 3)) > 0.985f) pseudoNoise(sampleIndex * 7) * 0.4f else 0f
                    val hiss = pseudoNoise(sampleIndex) * 0.04f
                    crackle + hiss
                }
                else -> {
                    val noise = pseudoNoise(sampleIndex)
                    noise * exp(-t * 4f) * 0.6f
                }
            }
            return s * velocity
        }
    }
}
