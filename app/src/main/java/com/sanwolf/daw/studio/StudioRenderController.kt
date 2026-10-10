package com.sanwolf.daw.studio

import com.sanwolf.daw.audio.SanwolfAudioEngine

class StudioRenderController(private val audioEngine: SanwolfAudioEngine) {
    fun startRender() {
        audioEngine.play()
    }
    fun stopRender() {
        audioEngine.stop()
    }
}
