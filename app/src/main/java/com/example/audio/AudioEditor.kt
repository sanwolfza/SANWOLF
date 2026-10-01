package com.example.audio

class AudioEditor {
    fun trim(data: FloatArray, startSample: Int, endSample: Int): FloatArray {
        if (startSample >= endSample || startSample < 0 || endSample > data.size) return data
        return data.copyOfRange(startSample, endSample)
    }
}
