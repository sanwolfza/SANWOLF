package com.sanwolf.daw

import com.sanwolf.daw.ai.MasteringAssistant
import com.sanwolf.daw.ai.MasteringGenre
import com.sanwolf.daw.ai.MasteringTarget
import com.sanwolf.daw.ai.MixMeasurement
import com.sanwolf.daw.model.ProjectData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JUnit (no org.json): covers the offline rules and apply/restore. Not run in CI. */
class MasteringAssistantTest {

    private fun measured(lufs: Float?, peak: Float = -1f, rms: Float = -14f) = MixMeasurement(
        seconds = 20f, integratedLufs = lufs, shortTermLufs = lufs, maxMomentaryLufs = lufs,
        peakDbfs = peak, rmsDbfs = rms, lowBandDb = -18f, midBandDb = -20f, highBandDb = -32f,
        compGrDb = 0f, limGrDb = 0f, lastExportLufs = null
    )

    @Test
    fun quietMixGetsDriveAndCeiling() {
        val p = ProjectData()
        p.masteringConfig.limiterGainDb = 0f
        val s = MasteringAssistant.localSuggestion(p, measured(-20f), MasteringTarget.STREAMING, -14f, MasteringGenre.GQOM)
        assertEquals(6f, s.chain.limiterGainDb, 0.01f)
        assertEquals(-1f, s.chain.limiterCeilingDb, 0.01f)
        assertTrue(s.chain.stereoWidth <= 1.2f)
    }

    @Test
    fun loudMixTakesDriveBackThenVolume() {
        val p = ProjectData()
        p.masteringConfig.limiterGainDb = 2f
        p.masterVolume = 1f
        val s = MasteringAssistant.localSuggestion(p, measured(-10f), MasteringTarget.PODCAST, -16f, MasteringGenre.OTHER)
        assertEquals(0f, s.chain.limiterGainDb, 0.01f)
        assertTrue(s.chain.masterVolume < 1f)
    }

    @Test
    fun valuesStayInSafeRanges() {
        val p = ProjectData()
        val s = MasteringAssistant.localSuggestion(p, measured(-40f, peak = -1f, rms = -30f), MasteringTarget.CLUB, -8f, MasteringGenre.AMAPIANO)
        assertTrue(s.chain.limiterGainDb in MasteringAssistant.DRIVE_RANGE)
        assertTrue(s.chain.lowGainDb in MasteringAssistant.LOW_SHELF_RANGE)
        assertTrue(s.chain.compressorThresholdDb in MasteringAssistant.COMP_THRESHOLD_RANGE)
        assertTrue(s.chain.masterVolume in MasteringAssistant.MASTER_VOLUME_RANGE)
    }

    @Test
    fun restorePutsEverythingBack() {
        val p = ProjectData()
        p.masteringConfig.enabled = false
        val before = MasteringAssistant.capture(p)
        val s = MasteringAssistant.localSuggestion(p, measured(-20f), MasteringTarget.STREAMING, -14f, MasteringGenre.POP)
        MasteringAssistant.write(p, s)
        assertTrue(p.masteringConfig.enabled)
        MasteringAssistant.restore(p, before, wasEnabled = false)
        assertEquals(before.chain, MasteringAssistant.capture(p).chain)
        assertTrue(!p.masteringConfig.enabled)
    }
}
