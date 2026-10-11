package com.sanwolf.daw

import com.sanwolf.daw.audio.MultisampleLibrary
import com.sanwolf.daw.audio.MultisampleVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultisampleVoiceTest {

    private fun ramp(n: Int) = ShortArray(n) { 10000 }

    private fun render(voice: MultisampleVoice, max: Int): Int {
        var n = 0
        while (!voice.isFinished && n < max) {
            voice.nextSample()
            n++
        }
        return n
    }

    @Test
    fun octaveUpPlaysTwiceAsFast() {
        val data = ramp(44100)
        val unity = render(MultisampleVoice(data, 1.0, 0.8f, 0f, Int.MAX_VALUE, 4410), 200_000)
        val octave = render(MultisampleVoice(data, 2.0, 0.8f, 0f, Int.MAX_VALUE, 4410), 200_000)
        assertEquals(unity / 2.0, octave.toDouble(), 2.0)
    }

    @Test
    fun releaseEndsHeldNoteShortlyAfterNoteOff() {
        val data = ramp(44100 * 4)
        val hold = 4410
        val release = 11025 // 250 ms
        val n = render(MultisampleVoice(data, 1.0, 0.8f, 0f, hold, release), 200_000)
        assertTrue("finished at $n", n in (hold + release - 10)..(hold + release + 10))
    }

    @Test
    fun outputStaysInRange() {
        val data = ShortArray(1000) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        val v = MultisampleVoice(data, 1.37, 1f, 0f, Int.MAX_VALUE, 1000)
        while (!v.isFinished) {
            val s = v.nextSample()
            assertTrue(s <= 1f && s >= -1f)
        }
    }

    @Test
    fun namesResolveExactlyOnly() {
        assertNotNull(MultisampleLibrary.find("Steinway Grand Piano"))
        assertNotNull(MultisampleLibrary.find("concert harp"))
        assertNotNull(MultisampleLibrary.find("vcsl.marimba"))
        assertNull(MultisampleLibrary.find("Grand Piano"))
        assertNull(MultisampleLibrary.find("Marimba Lead"))
        assertEquals(7, MultisampleLibrary.specs.size)
    }
}
