package com.example

import com.example.ui.TextInputDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `test deduplicator prevents duplicate typing from emulator bridge`() {
        val dedup = TextInputDeduplicator()

        // 1. User types 's' at t=1000ms
        var text = ""
        text = dedup.processChange(text, "s", currentTimeMs = 1000L)
        assertEquals("s", text)

        // 2. Emulator bridge sends duplicate 's' at t=1080ms (80ms delta)
        text = dedup.processChange(text, "ss", currentTimeMs = 1080L)
        assertEquals("s", text) // Duplicate dropped

        // 3. User types 'a' at t=1300ms
        text = dedup.processChange(text, "sa", currentTimeMs = 1300L)
        assertEquals("sa", text)

        // 4. Emulator bridge sends duplicate 'a' at t=1390ms
        text = dedup.processChange(text, "saa", currentTimeMs = 1390L)
        assertEquals("sa", text) // Duplicate dropped

        // 5. User types 'n' at t=1600ms
        text = dedup.processChange(text, "san", currentTimeMs = 1600L)
        assertEquals("san", text)

        // 6. Single event double insertion artifact (e.g. "san" -> "sanww" in 1 event at t=1900ms)
        text = dedup.processChange(text, "sanww", currentTimeMs = 1900L)
        assertEquals("sanw", text) // Collapsed to 1 'w'
    }

    @Test
    fun `test deduplicator prevents duplicate deletion from emulator bridge`() {
        val dedup = TextInputDeduplicator()
        var text = "sanwolf"

        // 1. User presses Backspace once at t=1000ms -> deletes 'f'
        text = dedup.processChange(text, "sanwol", currentTimeMs = 1000L)
        assertEquals("sanwol", text)

        // 2. Emulator bridge sends duplicate backspace at t=1100ms (100ms delta)
        text = dedup.processChange(text, "sanwo", currentTimeMs = 1100L)
        assertEquals("sanwol", text) // Duplicate deletion dropped

        // 3. Single event double deletion artifact (deletes 2 chars at once: "sanwol" -> "sanw" at t=2000ms)
        text = dedup.processChange(text, "sanw", currentTimeMs = 2000L)
        assertEquals("sanwo", text) // Only 1 char deleted instead of 2
    }

    @Test
    fun `test deduplicator allows intentional double letters with normal human delay`() {
        val dedup = TextInputDeduplicator()

        // Typing 'b' at t=1000ms
        var text = dedup.processChange("", "b", currentTimeMs = 1000L)
        assertEquals("b", text)

        // Typing 'a' at t=1200ms
        text = dedup.processChange(text, "ba", currentTimeMs = 1200L)
        assertEquals("ba", text)

        // Typing first 's' in "bass" at t=1400ms
        text = dedup.processChange(text, "bas", currentTimeMs = 1400L)
        assertEquals("bas", text)

        // Typing second 's' deliberately after 350ms at t=1750ms
        text = dedup.processChange(text, "bass", currentTimeMs = 1750L)
        assertEquals("bass", text) // Allowed because delta 350ms > 280ms
    }
}
