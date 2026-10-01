package com.example.mixer

import android.content.Context
import android.net.Uri

class MixerAudioDecoder(private val context: Context) {
    fun decodeAudio(uri: Uri): MixerPcmData {
        return MixerPcmData(ShortArray(44100), 44100)
    }
}
