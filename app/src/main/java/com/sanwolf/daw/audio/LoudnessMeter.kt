package com.sanwolf.daw.audio

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * ITU-R BS.1770-4 / EBU R128 loudness meter for a stereo signal.
 *
 * - K-weighting: a high-shelf (+4 dB above ~1.7 kHz, the head model) followed by a ~38 Hz
 *   high-pass (RLB curve). The coefficients are derived for the actual sample rate with the
 *   same bilinear-transform design as libebur128, so at 48 kHz they equal the values printed in
 *   BS.1770 and at 44.1 kHz they are the correct equivalent.
 * - Momentary loudness: 400 ms window, short-term: 3 s window, both updated every 100 ms.
 * - Integrated loudness (optional, for offline export): 400 ms blocks with 75 % overlap,
 *   absolute gate at -70 LUFS and relative gate at -10 LU.
 *
 * [process] is allocation-free and safe to call per sample on the audio thread. With
 * [trackIntegrated] the block history grows (amortised) and is meant for offline use only.
 */
class LoudnessMeter(
    sampleRate: Int = SanwolfAudioEngine.SAMPLE_RATE,
    private val trackIntegrated: Boolean = false
) {
    // ---- K-weighting coefficients (stage 1: shelf, stage 2: high-pass), normalised a0 = 1 ----
    private val s1b0: Double
    private val s1b1: Double
    private val s1b2: Double
    private val s1a1: Double
    private val s1a2: Double
    private val s2b0 = 1.0
    private val s2b1 = -2.0
    private val s2b2 = 1.0
    private val s2a1: Double
    private val s2a2: Double

    // Filter state (transposed direct form II), per channel and stage
    private var l1z1 = 0.0; private var l1z2 = 0.0; private var l2z1 = 0.0; private var l2z2 = 0.0
    private var r1z1 = 0.0; private var r1z2 = 0.0; private var r2z1 = 0.0; private var r2z2 = 0.0

    // 100 ms sub-blocks: the last 30 cover the 3 s short-term window
    private val subBlockSize = (sampleRate / 10).coerceAtLeast(1)
    private val ring = DoubleArray(SHORT_TERM_SUBBLOCKS)
    private var ringPos = 0
    private var ringFilled = 0
    private var acc = 0.0
    private var accCount = 0

    // Integrated: mean square of every 400 ms block (hop 100 ms)
    private var blocks = DoubleArray(if (trackIntegrated) 1024 else 0)
    private var blockCount = 0

    /** Latest momentary loudness (LUFS), -inf as [SILENCE_LUFS]. */
    @Volatile
    var momentaryLufs: Float = SILENCE_LUFS
        private set

    /** Latest short-term loudness (LUFS). */
    @Volatile
    var shortTermLufs: Float = SILENCE_LUFS
        private set

    init {
        val fs = sampleRate.toDouble()
        // Stage 1: high shelf
        val shelfF0 = 1681.974450955533
        val shelfGainDb = 3.999843853973347
        val shelfQ = 0.7071752369554196
        val k1 = tan(PI * shelfF0 / fs)
        val vh = 10.0.pow(shelfGainDb / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val a01 = 1.0 + k1 / shelfQ + k1 * k1
        s1b0 = (vh + vb * k1 / shelfQ + k1 * k1) / a01
        s1b1 = 2.0 * (k1 * k1 - vh) / a01
        s1b2 = (vh - vb * k1 / shelfQ + k1 * k1) / a01
        s1a1 = 2.0 * (k1 * k1 - 1.0) / a01
        s1a2 = (1.0 - k1 / shelfQ + k1 * k1) / a01
        // Stage 2: RLB high-pass
        val hpF0 = 38.13547087602444
        val hpQ = 0.5003270373238773
        val k2 = tan(PI * hpF0 / fs)
        val a02 = 1.0 + k2 / hpQ + k2 * k2
        s2a1 = 2.0 * (k2 * k2 - 1.0) / a02
        s2a2 = (1.0 - k2 / hpQ + k2 * k2) / a02
    }

    /** Feeds one stereo frame. Allocation-free (unless [trackIntegrated] needs to grow). */
    fun process(left: Float, right: Float) {
        // Left: shelf then high-pass
        var x = left.toDouble()
        var y = s1b0 * x + l1z1
        l1z1 = s1b1 * x - s1a1 * y + l1z2
        l1z2 = s1b2 * x - s1a2 * y
        x = y
        y = s2b0 * x + l2z1
        l2z1 = s2b1 * x - s2a1 * y + l2z2
        l2z2 = s2b2 * x - s2a2 * y
        val kl = y

        x = right.toDouble()
        y = s1b0 * x + r1z1
        r1z1 = s1b1 * x - s1a1 * y + r1z2
        r1z2 = s1b2 * x - s1a2 * y
        x = y
        y = s2b0 * x + r2z1
        r2z1 = s2b1 * x - s2a1 * y + r2z2
        r2z2 = s2b2 * x - s2a2 * y
        val kr = y

        // Channel weights for L and R are 1.0 (BS.1770)
        acc += kl * kl + kr * kr
        accCount++
        if (accCount >= subBlockSize) finishSubBlock()
    }

    /**
     * Accounts for [frames] of digital silence without running the filters (idle engine blocks).
     * Resets the filter state, since its tail would have decayed in that time anyway.
     */
    fun addSilence(frames: Int) {
        l1z1 = 0.0; l1z2 = 0.0; l2z1 = 0.0; l2z2 = 0.0
        r1z1 = 0.0; r1z2 = 0.0; r2z1 = 0.0; r2z2 = 0.0
        var remaining = frames
        while (remaining > 0) {
            val take = minOf(remaining, subBlockSize - accCount)
            accCount += take
            remaining -= take
            if (accCount >= subBlockSize) finishSubBlock()
        }
    }

    private fun finishSubBlock() {
        ring[ringPos] = acc / accCount.coerceAtLeast(1)
        ringPos = (ringPos + 1) % SHORT_TERM_SUBBLOCKS
        if (ringFilled < SHORT_TERM_SUBBLOCKS) ringFilled++
        acc = 0.0
        accCount = 0

        val m = meanOfLast(MOMENTARY_SUBBLOCKS)
        momentaryLufs = toLufs(m)
        shortTermLufs = toLufs(meanOfLast(SHORT_TERM_SUBBLOCKS))
        if (trackIntegrated && ringFilled >= MOMENTARY_SUBBLOCKS) {
            if (blockCount == blocks.size) blocks = blocks.copyOf(blocks.size * 2)
            blocks[blockCount++] = m
        }
    }

    /** Mean square of the newest [n] sub-blocks (fewer while the meter is filling). */
    private fun meanOfLast(n: Int): Double {
        val count = minOf(n, ringFilled)
        if (count == 0) return 0.0
        var sum = 0.0
        var idx = ringPos
        for (i in 0 until count) {
            idx = if (idx == 0) SHORT_TERM_SUBBLOCKS - 1 else idx - 1
            sum += ring[idx]
        }
        return sum / count
    }

    /**
     * Gated integrated loudness over everything processed so far (needs [trackIntegrated]).
     * Returns [SILENCE_LUFS] when nothing passes the gates.
     */
    fun integratedLufs(): Float {
        if (!trackIntegrated || blockCount == 0) return SILENCE_LUFS
        val absGate = fromLufs(ABSOLUTE_GATE_LUFS)
        var sum = 0.0
        var n = 0
        for (i in 0 until blockCount) {
            if (blocks[i] > absGate) { sum += blocks[i]; n++ }
        }
        if (n == 0) return SILENCE_LUFS
        val relGate = fromLufs(toLufs(sum / n) + RELATIVE_GATE_LU)
        sum = 0.0
        n = 0
        for (i in 0 until blockCount) {
            if (blocks[i] > absGate && blocks[i] > relGate) { sum += blocks[i]; n++ }
        }
        return if (n == 0) SILENCE_LUFS else toLufs(sum / n)
    }

    fun reset() {
        addSilence(0)
        java.util.Arrays.fill(ring, 0.0)
        ringPos = 0
        ringFilled = 0
        acc = 0.0
        accCount = 0
        blockCount = 0
        momentaryLufs = SILENCE_LUFS
        shortTermLufs = SILENCE_LUFS
    }

    companion object {
        /** Reported for silence / nothing measured. */
        const val SILENCE_LUFS = -70f
        private const val MOMENTARY_SUBBLOCKS = 4   // 400 ms
        private const val SHORT_TERM_SUBBLOCKS = 30 // 3 s
        private const val ABSOLUTE_GATE_LUFS = -70f
        private const val RELATIVE_GATE_LU = -10f

        /** BS.1770: L = -0.691 + 10 log10(sum of weighted mean squares). */
        fun toLufs(meanSquare: Double): Float =
            if (meanSquare <= 1e-12) SILENCE_LUFS else (-0.691 + 10.0 * log10(meanSquare)).toFloat().coerceAtLeast(SILENCE_LUFS)

        private fun fromLufs(lufs: Float): Double = 10.0.pow((lufs + 0.691) / 10.0)
    }
}
