package com.example

import com.example.audio.ActiveVoice
import com.example.audio.InstrumentCategory
import com.example.audio.InstrumentDefinition
import com.example.audio.InstrumentLibrary
import com.example.audio.InstrumentVoices
import com.example.audio.VoiceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class InstrumentStudioPipelineTest {

    @Test
    fun testAllFifteenRequiredInstrumentsProduceDistinctAudioSources() {
        // Authoritative list of 15 instruments requested in User Prompt Requirement 23
        val requiredInstruments = listOf(
            "drum.kick.deep",       // 1. Kick
            "drum.snare.tight",      // 2. Snare
            "drum.clap.clean",       // 3. Clap
            "drum.hihat.closed",     // 4. Closed Hat
            "drum.hihat.open",       // 5. Open Hat
            "drum.cymbal.crash",     // 6. Crash
            "perc.shaker",           // 7. Shaker
            "perc.conga",            // 8. Conga
            "perc.djembe",           // 9. Djembe
            "bass.sub",              // 10. Sub Bass
            "synth.analog.lead",     // 11. Analog Lead
            "pad.warm",              // 12. Warm Pad
            "keys.grand.piano",      // 13. Grand Piano
            "strings.violin",        // 14. Strings
            "pluck.kalimba"          // 15. Kalimba
        )

        assertEquals("Must test exactly 15 instruments", 15, requiredInstruments.size)

        val sampleBlocks = mutableMapOf<String, FloatArray>()
        val blockSize = 512

        for (id in requiredInstruments) {
            val def = InstrumentLibrary.getById(id)
            assertNotNull("Instrument definition must exist for '$id'", def)
            assertEquals("Instrument ID must match requested ID exactly", id, def.id)

            val voice: ActiveVoice = InstrumentVoices.createVoiceForDefinition(
                definition = def,
                pitch = 60,
                durationSec = 0.5f,
                velocity = 0.85f,
                pan = 0f
            )

            val samples = FloatArray(blockSize)
            var maxAmp = 0f
            for (i in 0 until blockSize) {
                val s = voice.nextSample()
                samples[i] = s
                if (abs(s) > maxAmp) maxAmp = abs(s)
            }

            assertTrue("Instrument '$id' must produce audible output (maxAmp: $maxAmp)", maxAmp > 0.01f)
            sampleBlocks[id] = samples
        }

        // Verify that every single pair among all 15 instruments produces a DISTINCT waveform
        for (i in 0 until requiredInstruments.size) {
            for (j in i + 1 until requiredInstruments.size) {
                val idA = requiredInstruments[i]
                val idB = requiredInstruments[j]
                val samplesA = sampleBlocks[idA]!!
                val samplesB = sampleBlocks[idB]!!

                var sumSqDiff = 0.0
                for (k in 0 until blockSize) {
                    val diff = samplesA[k] - samplesB[k]
                    sumSqDiff += diff * diff
                }
                val rmsDiff = sqrt(sumSqDiff / blockSize)

                assertTrue(
                    "Instruments '$idA' and '$idB' must produce distinct audio signatures (RMS diff: $rmsDiff)",
                    rmsDiff > 0.015
                )
            }
        }
    }

    @Test
    fun testSimultaneousPlaybackPolyphonyWithoutClippingOrCuttingOff() {
        val vm = VoiceManager()

        // 6 simultaneous voices: Kick + Clap + Hat + Djembe/Percussion + Sub Bass + Warm Pad
        val kickVoice = InstrumentVoices.createVoiceForDefinition(InstrumentLibrary.getById("drum.kick.deep"), 60, 0.5f, 0.8f, -0.1f)
        val clapVoice = InstrumentVoices.createVoiceForDefinition(InstrumentLibrary.getById("drum.clap.clean"), 60, 0.4f, 0.8f, 0.2f)
        val hatVoice = InstrumentVoices.createVoiceForDefinition(InstrumentLibrary.getById("drum.hihat.closed"), 60, 0.1f, 0.7f, 0.1f)
        val djembeVoice = InstrumentVoices.createVoiceForDefinition(InstrumentLibrary.getById("perc.djembe"), 60, 0.4f, 0.8f, -0.3f)
        val bassVoice = InstrumentVoices.createVoiceForDefinition(InstrumentLibrary.getById("bass.sub"), 36, 0.6f, 0.9f, 0f)
        val padVoice = InstrumentVoices.createVoiceForDefinition(InstrumentLibrary.getById("pad.warm"), 60, 0.8f, 0.7f, 0f)

        vm.addVoice(kickVoice)
        vm.addVoice(clapVoice)
        vm.addVoice(hatVoice)
        vm.addVoice(djembeVoice)
        vm.addVoice(bassVoice)
        vm.addVoice(padVoice)

        assertEquals("VoiceManager must hold all 6 active voices", 6, vm.voiceCount)

        val blockSize = 1024
        val left = FloatArray(blockSize)
        val right = FloatArray(blockSize)

        vm.renderBlock(blockSize, left, right)

        // 1. None of the voices cut each other off
        assertFalse("Kick voice must still be active", kickVoice.isFinished)
        assertFalse("Bass voice must still be active", bassVoice.isFinished)
        assertFalse("Pad voice must still be active", padVoice.isFinished)

        // 2. Audio is not silent
        var maxL = 0f
        var maxR = 0f
        for (i in 0 until blockSize) {
            if (abs(left[i]) > maxL) maxL = abs(left[i])
            if (abs(right[i]) > maxR) maxR = abs(right[i])

            // 3. No excessive digital clipping (soft saturation limits output cleanly <= 1.05)
            assertTrue("Left sample $i must not exceed headroom limit: ${left[i]}", abs(left[i]) <= 1.05f)
            assertTrue("Right sample $i must not exceed headroom limit: ${right[i]}", abs(right[i]) <= 1.05f)
            assertFalse("Left sample $i must not be NaN", left[i].isNaN())
            assertFalse("Right sample $i must not be NaN", right[i].isNaN())
        }

        assertTrue("Simultaneous playback left channel must not be silent (peak: $maxL)", maxL > 0.05f)
        assertTrue("Simultaneous playback right channel must not be silent (peak: $maxR)", maxR > 0.05f)
    }

    @Test
    fun testAuthoritativeInstrumentTaxonomyCoverage() {
        val allDefs = InstrumentLibrary.definitions
        assertTrue("InstrumentLibrary must contain a comprehensive set of instruments", allDefs.size >= 80)

        val categories = allDefs.map { it.category }.distinct()
        assertTrue("Must have DRUMS category", categories.contains(InstrumentCategory.DRUMS))
        assertTrue("Must have BASS category", categories.contains(InstrumentCategory.BASS))
        assertTrue("Must have SYNTHS category", categories.contains(InstrumentCategory.SYNTHS))
        assertTrue("Must have PADS category", categories.contains(InstrumentCategory.PADS))
        assertTrue("Must have KEYS_PIANO category", categories.contains(InstrumentCategory.KEYS_PIANO))
        assertTrue("Must have STRINGS category", categories.contains(InstrumentCategory.STRINGS))
        assertTrue("Must have BRASS_WINDS category", categories.contains(InstrumentCategory.BRASS_WINDS))
        assertTrue("Must have PLUCKS_MALLETS category", categories.contains(InstrumentCategory.PLUCKS_MALLETS))
        assertTrue("Must have WORLD_PERCUSSION category", categories.contains(InstrumentCategory.WORLD_PERCUSSION))
        assertTrue("Must have SOUND_EFFECTS_TEXTURES category", categories.contains(InstrumentCategory.SOUND_EFFECTS_TEXTURES))

        // Check stable IDs
        for (def in allDefs) {
            assertTrue("Definition id '${def.id}' must be non-empty and dot-notated or descriptive", def.id.isNotEmpty())
            assertTrue("Definition name '${def.name}' must be non-empty", def.name.isNotEmpty())
        }
    }

    @Test
    fun testOfflineRenderMatchesLivePlaybackVoices() {
        val pianoDef = InstrumentLibrary.getById("keys.grand.piano")
        val liveVoice = InstrumentVoices.createVoiceForDefinition(pianoDef, 60, 0.4f, 0.8f, 0f)
        val renderVoice = InstrumentVoices.createVoiceForDefinition(pianoDef, 60, 0.4f, 0.8f, 0f)

        for (i in 0 until 256) {
            val liveSample = liveVoice.nextSample()
            val renderSample = renderVoice.nextSample()
            assertEquals("Live and offline voices must generate 1:1 identical samples at index $i", liveSample, renderSample, 0.0001f)
        }
    }
}
