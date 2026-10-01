package com.example.studio

import com.example.audio.SanwolfAudioEngine

class StudioRenderController(private val audioEngine: SanwolfAudioEngine) {
    fun startRender() {
        audioEngine.play()
    }
    fun stopRender() {
        audioEngine.stop()
    }
}
