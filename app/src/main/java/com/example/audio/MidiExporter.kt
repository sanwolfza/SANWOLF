package com.example.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.model.TrackType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object MidiExporter {

    private const val TICKS_PER_BEAT = 480

    fun exportTrackSequenceToMidi(
        context: Context,
        track: TrackData,
        bpm: Int = 120,
        projectName: String = "Sanwolf"
    ): File {
        val sanitizedTrackName = track.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val fileName = "${sanitizedTrackName}_sequence_${System.currentTimeMillis()}.mid"
        val outFile = File(context.cacheDir, fileName)

        val trackDataStreams = mutableListOf<ByteArray>()

        // 1. Conductor track (tempo & time signature)
        val conductor = ByteArrayOutputStream()
        // Time signature 4/4
        writeVarLen(conductor, 0)
        conductor.write(byteArrayOf(0xFF.toByte(), 0x58.toByte(), 0x04.toByte(), 0x04.toByte(), 0x02.toByte(), 0x18.toByte(), 0x08.toByte()))

        // Set Tempo: 60,000,000 / BPM
        val microsecPerQuarter = (60_000_000 / bpm.coerceIn(20, 300))
        writeVarLen(conductor, 0)
        conductor.write(byteArrayOf(0xFF.toByte(), 0x51.toByte(), 0x03.toByte()))
        conductor.write((microsecPerQuarter shr 16) and 0xFF)
        conductor.write((microsecPerQuarter shr 8) and 0xFF)
        conductor.write(microsecPerQuarter and 0xFF)

        // End of conductor track
        writeVarLen(conductor, 0)
        conductor.write(byteArrayOf(0xFF.toByte(), 0x2F.toByte(), 0x00.toByte()))
        trackDataStreams.add(conductor.toByteArray())

        // 2. Piano roll sequence track
        val trackStream = ByteArrayOutputStream()
        val channel = if (track.type == com.example.model.TrackType.DRUM_MACHINE) 9 else 0 // MIDI Ch 10 for drums, Ch 1 for melodic

        // Track Name meta-event
        writeVarLen(trackStream, 0)
        val nameBytes = track.name.toByteArray(Charsets.UTF_8)
        trackStream.write(0xFF)
        trackStream.write(0x03)
        writeVarLen(trackStream, nameBytes.size.toLong())
        trackStream.write(nameBytes)

        // MIDI Program Change (optional instrument identifier)
        writeVarLen(trackStream, 0)
        trackStream.write(0xC0 or channel)
        val program = if (track.type == com.example.model.TrackType.DRUM_MACHINE) 0 else 80 // Synth Lead
        trackStream.write(program and 0x7F)

        class MidiEvent(val tick: Long, val type: Int, val pitch: Int, val vel: Int)
        val events = mutableListOf<MidiEvent>()

        // A. Add Piano roll notes
        for (note in track.notes) {
            val startTick = (note.startBeat * TICKS_PER_BEAT).toLong().coerceAtLeast(0L)
            val endTick = ((note.startBeat + note.lengthBeats.coerceAtLeast(0.0625)) * TICKS_PER_BEAT).toLong()
            val velocity = (note.velocity * 127).toInt().coerceIn(1, 127)

            events.add(MidiEvent(startTick, 0x90 or channel, note.pitch.coerceIn(0, 127), velocity))
            events.add(MidiEvent(endTick, 0x80 or channel, note.pitch.coerceIn(0, 127), 0))
        }

        // B. If no piano roll notes but sequencer steps exist (e.g. drum track)
        if (track.notes.isEmpty()) {
            val drumPitch = when {
                track.name.contains("Kick", ignoreCase = true) -> 36
                track.name.contains("Snare", ignoreCase = true) -> 38
                track.name.contains("Clap", ignoreCase = true) -> 39
                track.name.contains("Hat", ignoreCase = true) || track.name.contains("HiHat", ignoreCase = true) -> 42
                track.name.contains("Open", ignoreCase = true) -> 46
                track.name.contains("Crash", ignoreCase = true) -> 49
                track.name.contains("Perc", ignoreCase = true) || track.name.contains("Cowbell", ignoreCase = true) -> 56
                else -> 36
            }
            for (step in 0 until 64) {
                val effectiveStep = step % track.stepCount.coerceAtLeast(1)
                if (track.steps.getOrElse(effectiveStep) { false }) {
                    val startTick = (step * (TICKS_PER_BEAT / 4)).toLong()
                    val endTick = startTick + (TICKS_PER_BEAT / 8)
                    val vel = ((track.stepVelocities.getOrElse(effectiveStep) { 0.85f }) * 127).toInt().coerceIn(1, 127)
                    events.add(MidiEvent(startTick, 0x90 or channel, drumPitch, vel))
                    events.add(MidiEvent(endTick, 0x80 or channel, drumPitch, 0))
                }
            }
        }

        // Sort events chronologically. If NoteOff and NoteOn have identical tick, process NoteOff first.
        events.sortWith(compareBy({ it.tick }, { if ((it.type and 0xF0) == 0x80) 0 else 1 }, { it.pitch }))

        var lastTick = 0L
        for (e in events) {
            val delta = (e.tick - lastTick).coerceAtLeast(0L)
            writeVarLen(trackStream, delta)
            trackStream.write(e.type)
            trackStream.write(e.pitch and 0x7F)
            trackStream.write(e.vel and 0x7F)
            lastTick = e.tick
        }

        // End of track meta-event
        writeVarLen(trackStream, 0)
        trackStream.write(byteArrayOf(0xFF.toByte(), 0x2F.toByte(), 0x00.toByte()))
        trackDataStreams.add(trackStream.toByteArray())

        // Write complete SMF Format 1 file
        FileOutputStream(outFile).use { fos ->
            writeSmfHeader(fos, trackDataStreams.size)
            for (td in trackDataStreams) {
                writeTrackChunk(fos, td)
            }
        }

        return outFile
    }

    private fun writeSmfHeader(fos: FileOutputStream, numTracks: Int) {
        fos.write("MThd".toByteArray())
        fos.write(byteArrayOf(0x00, 0x00, 0x00, 0x06)) // Header length = 6
        fos.write(byteArrayOf(0x00, 0x01)) // Format 1 (multi-track synchronous)
        fos.write(byteArrayOf(((numTracks shr 8) and 0xFF).toByte(), (numTracks and 0xFF).toByte()))
        fos.write(byteArrayOf(((TICKS_PER_BEAT shr 8) and 0xFF).toByte(), (TICKS_PER_BEAT and 0xFF).toByte()))
    }

    private fun writeTrackChunk(fos: FileOutputStream, trackData: ByteArray) {
        fos.write("MTrk".toByteArray())
        val len = trackData.size
        fos.write((len shr 24) and 0xFF)
        fos.write((len shr 16) and 0xFF)
        fos.write((len shr 8) and 0xFF)
        fos.write(len and 0xFF)
        fos.write(trackData)
    }

    fun exportProjectToMidi(context: Context, project: ProjectData): File {
        val fileName = "${project.title.replace(" ", "_")}_${System.currentTimeMillis()}.mid"
        val outFile = File(context.cacheDir, fileName)

        val trackDataStreams = mutableListOf<ByteArray>()

        // 1. Conductor track (tempo & time signature)
        val conductor = ByteArrayOutputStream()
        // Set Tempo: 60,000,000 / BPM
        val microsecPerQuarter = (60_000_000 / project.bpm.coerceAtLeast(20))
        writeVarLen(conductor, 0)
        conductor.write(byteArrayOf(0xFF.toByte(), 0x51.toByte(), 0x03.toByte()))
        conductor.write((microsecPerQuarter shr 16) and 0xFF)
        conductor.write((microsecPerQuarter shr 8) and 0xFF)
        conductor.write(microsecPerQuarter and 0xFF)

        // End of track
        writeVarLen(conductor, 0)
        conductor.write(byteArrayOf(0xFF.toByte(), 0x2F.toByte(), 0x00.toByte()))
        trackDataStreams.add(conductor.toByteArray())

        // 2. Note tracks from project
        for ((trackIdx, track) in project.tracks.withIndex()) {
            val trackStream = ByteArrayOutputStream()
            val channel = (trackIdx % 16)

            // Gather all MIDI note events sorted by tick
            class MidiEvent(val tick: Long, val type: Int, val pitch: Int, val vel: Int)
            val events = mutableListOf<MidiEvent>()

            // A. Piano roll notes
            for (note in track.notes) {
                val startTick = (note.startBeat * TICKS_PER_BEAT).toLong()
                val endTick = ((note.startBeat + note.lengthBeats) * TICKS_PER_BEAT).toLong()
                val velocity = (note.velocity * 127).toInt().coerceIn(1, 127)

                events.add(MidiEvent(startTick, 0x90 or channel, note.pitch, velocity))
                events.add(MidiEvent(endTick, 0x80 or channel, note.pitch, 0))
            }

            // B. Step sequencer steps
            val drumPitch = 36 + trackIdx // General MIDI drum notes
            for (step in 0 until 64) {
                val effectiveStep = step % track.stepCount.coerceAtLeast(1)
                if (track.steps.getOrElse(effectiveStep) { false }) {
                    val startTick = (step * (TICKS_PER_BEAT / 4)).toLong()
                    val endTick = startTick + (TICKS_PER_BEAT / 8)
                    val vel = ((track.stepVelocities.getOrElse(effectiveStep) { 0.8f }) * 127).toInt().coerceIn(1, 127)
                    events.add(MidiEvent(startTick, 0x90 or channel, drumPitch, vel))
                    events.add(MidiEvent(endTick, 0x80 or channel, drumPitch, 0))
                }
            }

            events.sortBy { it.tick }

            var lastTick = 0L
            for (e in events) {
                val delta = e.tick - lastTick
                writeVarLen(trackStream, delta)
                trackStream.write(e.type)
                trackStream.write(e.pitch and 0x7F)
                trackStream.write(e.vel and 0x7F)
                lastTick = e.tick
            }

            // End of track
            writeVarLen(trackStream, 0)
            trackStream.write(byteArrayOf(0xFF.toByte(), 0x2F.toByte(), 0x00.toByte()))
            trackDataStreams.add(trackStream.toByteArray())
        }

        // Write complete SMF Format 1 file
        FileOutputStream(outFile).use { fos ->
            // MThd header: type 1, numTracks, ticksPerBeat
            fos.write("MThd".toByteArray())
            fos.write(byteArrayOf(0x00, 0x00, 0x00, 0x06)) // header length = 6
            fos.write(byteArrayOf(0x00, 0x01)) // Format 1
            val numTracks = trackDataStreams.size
            fos.write(byteArrayOf(((numTracks shr 8) and 0xFF).toByte(), (numTracks and 0xFF).toByte()))
            fos.write(byteArrayOf(((TICKS_PER_BEAT shr 8) and 0xFF).toByte(), (TICKS_PER_BEAT and 0xFF).toByte()))

            for (td in trackDataStreams) {
                fos.write("MTrk".toByteArray())
                val len = td.size
                fos.write((len shr 24) and 0xFF)
                fos.write((len shr 16) and 0xFF)
                fos.write((len shr 8) and 0xFF)
                fos.write(len and 0xFF)
                fos.write(td)
            }
        }

        return outFile
    }

    private fun writeVarLen(out: ByteArrayOutputStream, value: Long) {
        var v = value
        var buffer = v and 0x7F
        while ((v shr 7).also { v = it } > 0) {
            buffer = buffer shl 8
            buffer = buffer or ((v and 0x7F) or 0x80)
        }
        while (true) {
            out.write((buffer and 0xFF).toInt())
            if ((buffer and 0x80L) != 0L) {
                buffer = buffer shr 8
            } else {
                break
            }
        }
    }

    fun createShareIntent(context: Context, file: File, subject: String = "Share MIDI Sequence"): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/midi"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "Exported MIDI: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun saveMidiToDownloads(context: Context, file: File): android.net.Uri? {
        return try {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/midi")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/SanwolfDaw")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                android.provider.MediaStore.Files.getContentUri("external")
            }
            val uri = resolver.insert(collection, contentValues) ?: return null
            resolver.openOutputStream(uri)?.use { os ->
                file.inputStream().use { input ->
                    input.copyTo(os)
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            val extDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: context.filesDir
            val copyFile = File(extDir, file.name)
            file.copyTo(copyFile, overwrite = true)
            android.net.Uri.fromFile(copyFile)
        }
    }
}
