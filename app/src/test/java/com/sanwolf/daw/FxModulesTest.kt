package com.sanwolf.daw

import com.sanwolf.daw.audio.FxProcessor
import com.sanwolf.daw.audio.FxUnit
import com.sanwolf.daw.audio.configureFxChain
import com.sanwolf.daw.audio.runFxChain
import com.sanwolf.daw.model.FxIdx
import com.sanwolf.daw.model.FxModules
import com.sanwolf.daw.model.FxSlot
import com.sanwolf.daw.model.FxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Plain JUnit: every module and preset stays finite and bounded; sync, old slots, presets. */
class FxModulesTest {

    @Test
    fun everyTypeHasSpecAndPresets() {
        for (t in FxType.values()) {
            val spec = FxModules.spec(t)
            assertTrue("$t presets", spec.presets.size in 2..4)
            for (p in spec.presets) assertEquals("$t ${p.name}", spec.params.size, p.values.size)
        }
    }

    @Test
    fun allModulesAndPresetsStayFinite() {
        val io = FloatArray(2)
        val tmp = FloatArray(2)
        for (type in FxType.values()) {
            val spec = FxModules.spec(type)
            for (pr in spec.presets) {
                val slot = FxModules.newSlot(type)
                FxModules.applyPreset(slot, spec, pr)
                val chain = arrayOf(FxUnit(slot, FxProcessor.create(type, 44100)))
                var t = 0
                repeat(100) {
                    configureFxChain(chain, 126.0)
                    repeat(1024) {
                        val x = if (t < 44100) 0.5f * sin(2 * PI * 220.0 * t / 44100).toFloat() else 0f
                        io[0] = x; io[1] = x
                        runFxChain(chain, io, tmp)
                        assertFalse("$type ${pr.name} NaN", io[0].isNaN() || io[1].isNaN())
                        assertTrue("$type ${pr.name} runaway", abs(io[0]) < 8f && abs(io[1]) < 8f)
                        t++
                    }
                }
            }
        }
    }

    @Test
    fun delayEighthAt120BpmEchoesAfterQuarterSecond() {
        val slot = FxModules.newSlot(FxType.DELAY)
        val spec = FxModules.spec(FxType.DELAY)
        FxModules.set(slot, spec, FxIdx.Delay.MIX, 1f)
        FxModules.set(slot, spec, FxIdx.Delay.HIGH_CUT, 20000f)
        val chain = arrayOf(FxUnit(slot, FxProcessor.create(FxType.DELAY, 44100)))
        val io = FloatArray(2); val tmp = FloatArray(2)
        var first = -1; var n = 0
        repeat(20) {
            configureFxChain(chain, 120.0)
            repeat(1024) {
                io[0] = if (n == 0) 1f else 0f; io[1] = io[0]
                runFxChain(chain, io, tmp)
                if (n > 10 && first < 0 && abs(io[0]) > 0.05f) first = n
                n++
            }
        }
        assertTrue("echo at $first", abs(first - 11025) <= 2)
    }

    @Test
    fun oldSlotWithoutParamsUsesDefaults() {
        val old = FxSlot(type = FxType.REVERB.key) // as Gson loads a slot saved before params existed
        val spec = FxModules.spec(FxType.REVERB)
        assertEquals(spec.params[FxIdx.Reverb.MIX].default, FxModules.get(old, spec, FxIdx.Reverb.MIX), 0f)
        val short = FxSlot(type = FxType.DELAY.key, params = floatArrayOf(1f))
        FxModules.set(short, FxModules.spec(FxType.DELAY), FxIdx.Delay.MIX, 0.5f)
        assertEquals(6, short.params!!.size)
        assertEquals(1f, short.params!![0], 0f)
    }

    @Test
    fun presetMatchingAndUnknownTypeSkipped() {
        val spec = FxModules.spec(FxType.REVERB)
        val slot = FxModules.newSlot(FxType.REVERB)
        FxModules.applyPreset(slot, spec, spec.presets[1])
        assertEquals(spec.presets[1].name, FxModules.matchingPreset(slot, spec))
        assertEquals(null, FxProcessor.createFor(FxSlot(type = "future_module")))
    }
}
