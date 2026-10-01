package com.example.audio

import kotlin.math.abs

class WaveformAnalyzer {
    fun computePeaks(pcm: ShortArray, numPoints: Int): FloatArray {
        if (pcm.isEmpty() || numPoints <= 0) return FloatArray(0)
        val peaks = FloatArray(numPoints)
        val blockSize = pcm.size / numPoints
        if (blockSize <= 0) return pcm.map { abs(it.toFloat()) / 32768f }.toFloatArray()

        for (i in 0 until numPoints) {
            var maxVal = 0f
            val start = i * blockSize
            val end = minOf(start + blockSize, pcm.size)
            for (j in start until end) {
                val v = abs(pcm[j].toFloat()) / 32768f
                if (v > maxVal) maxVal = v
            }
            peaks[i] = maxVal
        }
        return peaks
    }
}
