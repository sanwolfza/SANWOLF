package com.example.mixer

import android.net.Uri

class MixerTrackImporter {
    fun importTrack(uri: Uri): MixerAudioTrack {
        return MixerAudioTrack("imported_${System.currentTimeMillis()}", "Imported Stem")
    }
}
