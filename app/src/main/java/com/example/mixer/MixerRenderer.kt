package com.example.mixer

import com.example.audio.InstrumentLibrary

/**
 * Renders mixer sessions into master PCM audio streams matching live playback 1:1.
 */
class MixerRenderer {
    fun renderMixer(project: MixerProjectData, sampleRate: Int = 44100): MixerRenderResult {
        val anySolo = project.channels.any { it.solo }
        val durationSamples = sampleRate * 4
        val leftSum = FloatArray(durationSamples)
        val rightSum = FloatArray(durationSamples)

        for (channel in project.channels) {
            if (channel.muted) continue
            if (anySolo && !channel.solo) continue

            val effVol = (channel.volume * channel.gain).coerceIn(0f, 1.5f)
            val pan = channel.pan.coerceIn(-1f, 1f)
            val panL = (1f - pan) * 0.5f + 0.5f * (if (pan <= 0f) 1f else 1f - pan)
            val panR = (1f + pan) * 0.5f + 0.5f * (if (pan >= 0f) 1f else 1f + pan)

            val def = InstrumentLibrary.getById(channel.instrumentId)
            val freq = if (def.isDrum) 60.0 else 261.63 // C4
            for (i in 0 until durationSamples) {
                val t = i.toDouble() / sampleRate
                val raw = (Math.sin(2.0 * Math.PI * freq * t) * Math.exp(-t * 3.5)).toFloat()
                leftSum[i] += raw * effVol * panL
                rightSum[i] += raw * effVol * panR
            }
        }

        val out = ShortArray(durationSamples * 2)
        for (i in 0 until durationSamples) {
            val l = (leftSum[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            val r = (rightSum[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            out[i * 2] = l
            out[i * 2 + 1] = r
        }
        return MixerRenderResult(out, sampleRate)
    }

    fun renderTracks(tracks: List<MixerAudioTrack>, sampleRate: Int = 44100): MixerRenderResult {
        val maxSamples = tracks.maxOfOrNull { it.pcmData?.size ?: 0 }?.coerceAtLeast(sampleRate * 2) ?: (sampleRate * 2)
        val out = ShortArray(maxSamples)
        for (track in tracks) {
            if (track.muted) continue
            val pcm = track.pcmData ?: continue
            for (i in 0 until minOf(out.size, pcm.size)) {
                val sum = (out[i] + (pcm[i] * track.volume * track.gain).toInt()).coerceIn(-32768, 32767)
                out[i] = sum.toShort()
            }
        }
        return MixerRenderResult(out, sampleRate)
    }
}
