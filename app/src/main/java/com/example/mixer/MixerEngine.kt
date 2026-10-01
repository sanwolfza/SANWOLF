package com.example.mixer

import com.example.audio.SanwolfAudioEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * Enterprise MixerEngine for SANWOLF Audio Studio.
 * Integrates per-instrument channels, gain, volume, pan, mute, solo, and master bus.
 */
class MixerEngine(val audioEngine: SanwolfAudioEngine) {
    val trackManager = MixerAudioTrackManager()
    val channels = ConcurrentHashMap<String, MixerChannel>()

    val masterVolume: Float
        get() = audioEngine.masterVolume

    fun setMasterVolume(volume: Float) {
        audioEngine.masterVolume = volume
    }

    fun getOrCreateChannel(
        instrumentId: String,
        trackName: String,
        category: String,
        sampleSource: String = ""
    ): MixerChannel {
        val channelId = "ch_${instrumentId.replace(".", "_")}"
        return channels.getOrPut(channelId) {
            MixerChannel(
                channelId = channelId,
                instrumentId = instrumentId,
                trackName = trackName,
                category = category,
                sampleSource = sampleSource,
                volume = 0.8f,
                gain = 1.0f,
                pan = 0f
            )
        }
    }

    fun setChannelVolume(channelId: String, volume: Float) {
        channels[channelId]?.volume = volume
    }

    fun setChannelPan(channelId: String, pan: Float) {
        channels[channelId]?.pan = pan
    }

    fun setChannelMute(channelId: String, muted: Boolean) {
        channels[channelId]?.muted = muted
    }

    fun setChannelSolo(channelId: String, solo: Boolean) {
        channels[channelId]?.solo = solo
    }

    fun isChannelAudible(channelId: String): Boolean {
        val ch = channels[channelId] ?: return true
        if (ch.muted) return false
        val anySolo = channels.values.any { it.solo }
        return !anySolo || ch.solo
    }

    fun getEffectiveVolume(channelId: String): Float {
        val ch = channels[channelId] ?: return masterVolume
        if (!isChannelAudible(channelId)) return 0f
        return (ch.volume * ch.gain * masterVolume).coerceIn(0f, 1.5f)
    }

    fun getEffectivePan(channelId: String): Float {
        return channels[channelId]?.pan ?: 0f
    }
}
