package com.example.mixer

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages active audio tracks feeding into the Mixer in SANWOLF Audio Studio.
 */
class MixerAudioTrackManager {
    val tracks = CopyOnWriteArrayList<MixerAudioTrack>()

    fun getOrCreateTrack(
        id: String,
        name: String,
        instrumentId: String = "",
        category: String = "",
        sampleSource: String = "",
        volume: Float = 0.8f,
        pan: Float = 0f
    ): MixerAudioTrack {
        val existing = tracks.find { it.id == id || (instrumentId.isNotEmpty() && it.instrumentId == instrumentId) }
        if (existing != null) {
            existing.name = name
            existing.volume = volume
            existing.pan = pan
            if (instrumentId.isNotEmpty()) existing.instrumentId = instrumentId
            if (category.isNotEmpty()) existing.category = category
            if (sampleSource.isNotEmpty()) existing.sampleSource = sampleSource
            return existing
        }

        val newTrack = MixerAudioTrack(
            id = id,
            name = name,
            instrumentId = instrumentId,
            category = category,
            sampleSource = sampleSource,
            volume = volume,
            pan = pan
        )
        tracks.add(newTrack)
        return newTrack
    }

    fun findTrackById(id: String): MixerAudioTrack? = tracks.find { it.id == id }

    fun findTrackByInstrument(instrumentId: String): MixerAudioTrack? =
        tracks.find { it.instrumentId == instrumentId }

    fun removeTrack(id: String) {
        tracks.removeAll { it.id == id }
    }

    fun clear() {
        tracks.clear()
    }
}
