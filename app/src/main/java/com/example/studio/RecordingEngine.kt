package com.example.studio

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class RecordingEngine(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun startRecording(outputFile: File) {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    fun stopRecording() {
        runCatching {
            recorder?.stop()
            recorder?.release()
            recorder = null
        }
    }
}
