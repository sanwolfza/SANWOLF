package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.GeminiDawClient
import com.example.audio.MidiExporter
import com.example.audio.WavExporter
import com.example.model.MidiNote
import com.example.model.ProjectData
import com.example.model.TrackData
import com.example.model.TrackType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SANWOLF", appName)
  }

  @Test
  fun `verify wav and midi exporters produce valid files`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val project = ProjectData(title = "Test_Beat", bpm = 128)
    project.tracks.add(
        TrackData(
            name = "Kick",
            type = TrackType.DRUM_MACHINE,
            colorHex = 0xFFFF9100,
            stepCount = 16,
            steps = booleanArrayOf(true, false, false, false, true, false, false, false, true, false, false, false, true, false, false, false)
        )
    )
    project.tracks.add(
        TrackData(
            name = "Synth",
            type = TrackType.SYNTH,
            colorHex = 0xFF00E5FF,
            notes = mutableListOf(MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 1.0))
        )
    )

    val wavFile = WavExporter.exportProjectToWav(context, project, 2)
    assertNotNull(wavFile)
    assertTrue(wavFile.exists())
    assertTrue(wavFile.length() > 44) // Contains RIFF header + PCM audio

    val midiFile = MidiExporter.exportProjectToMidi(context, project)
    assertNotNull(midiFile)
    assertTrue(midiFile.exists())
    assertTrue(midiFile.length() > 14) // Contains MThd header + tracks
  }

  @Test
  fun `verify AI music generator composes according to genre bpm drums and instruments`() = runBlocking {
    val client = GeminiDawClient()
    val result = client.generateMusicByParameters(
        genre = "Trap 808",
        bpm = 140,
        drumElements = listOf("808 Sub Kick", "Trap Snare", "Hi-Hat Rolls"),
        drumStyle = "Heavy 808 & Slides",
        instruments = listOf("Analog Saw Lead", "Reese Sub Bass"),
        keyScale = "C Minor",
        includeLyriaAudio = false
    )

    assertNotNull(result)
    assertEquals("Trap 808", result.genre)
    assertEquals(140, result.bpm)
    assertEquals("C Minor", result.keyScale)
    assertTrue(result.tracks.isNotEmpty())
    assertTrue(result.tracks.any { it.type == TrackType.DRUM_MACHINE })
    assertTrue(result.tracks.any { it.type == TrackType.SYNTH })
  }

  @Test
  fun `verify note velocity can be dynamically adjusted and exported`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val note = MidiNote(pitch = 64, startBeat = 1.0, lengthBeats = 1.0, velocity = 0.85f)
    assertEquals(0.85f, note.velocity, 0.01f)

    // Adjust note velocity (simulating vertical drag or dedicated slider)
    note.velocity = 0.45f
    assertEquals(0.45f, note.velocity, 0.01f)

    note.velocity = 1.0f
    assertEquals(1.0f, note.velocity, 0.01f)

    val track = TrackData(
        name = "Lead",
        type = TrackType.SYNTH,
        colorHex = 0xFF00E5FF,
        notes = mutableListOf(note)
    )
    val project = ProjectData(title = "Velocity_Test")
    project.tracks.add(track)

    val midiFile = MidiExporter.exportProjectToMidi(context, project)
    assertNotNull(midiFile)
    assertTrue(midiFile.exists())
  }

  @Test
  fun `verify piano roll track sequence exports as standard SMF Type 1 MIDI file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Create a synth track with multiple piano roll notes having distinct velocities and lengths
    val synthTrack = TrackData(
        name = "Amapiano_Lead",
        type = TrackType.SYNTH,
        colorHex = 0xFFFFD700,
        notes = mutableListOf(
            MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 1.0, velocity = 0.90f), // C4
            MidiNote(pitch = 64, startBeat = 1.0, lengthBeats = 0.5, velocity = 0.70f), // E4
            MidiNote(pitch = 67, startBeat = 1.5, lengthBeats = 0.5, velocity = 0.85f), // G4
            MidiNote(pitch = 71, startBeat = 2.0, lengthBeats = 2.0, velocity = 1.0f)   // B4 (ff)
        )
    )

    val midiFile = MidiExporter.exportTrackSequenceToMidi(
        context = context,
        track = synthTrack,
        bpm = 126
    )

    assertNotNull(midiFile)
    assertTrue(midiFile.exists())
    assertTrue(midiFile.length() > 30) // Header + Conductor track + Sequence track

    // Verify SMF Header bytes
    val bytes = midiFile.readBytes()
    val headerStr = String(bytes.copyOfRange(0, 4))
    assertEquals("MThd", headerStr)

    // Verify Format 1 (bytes 8-9 = 0x00 0x01)
    assertEquals(0x00.toByte(), bytes[8])
    assertEquals(0x01.toByte(), bytes[9])

    // Verify 2 Tracks (bytes 10-11 = 0x00 0x02)
    assertEquals(0x00.toByte(), bytes[10])
    assertEquals(0x02.toByte(), bytes[11])

    // Verify Ticks per beat = 480 (0x01 0xE0)
    assertEquals(0x01.toByte(), bytes[12])
    assertEquals(0xE0.toByte(), bytes[13])

    // 2. Verify Share Intent creation
    val shareIntent = MidiExporter.createShareIntent(context, midiFile, "Share Track Sequence")
    assertEquals(android.content.Intent.ACTION_SEND, shareIntent.action)
    assertEquals("audio/midi", shareIntent.type)
    assertTrue((shareIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    assertNotNull(shareIntent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java))

    // 3. Verify drum track step sequence MIDI export
    val drumTrack = TrackData(
        name = "Kick",
        type = TrackType.DRUM_MACHINE,
        colorHex = 0xFFFF5722,
        stepCount = 16,
        steps = booleanArrayOf(true, false, false, false, true, false, false, false, true, false, false, false, true, false, false, false),
        stepVelocities = floatArrayOf(1.0f, 0.8f, 0.8f, 0.8f, 0.9f, 0.8f, 0.8f, 0.8f, 0.95f, 0.8f, 0.8f, 0.8f, 0.85f, 0.8f, 0.8f, 0.8f)
    )

    val drumMidi = MidiExporter.exportTrackSequenceToMidi(context, drumTrack, bpm = 130)
    assertNotNull(drumMidi)
    assertTrue(drumMidi.exists())
    assertTrue(drumMidi.length() > 30)
  }

  @Test
  fun `verify swing and quantize intensity groove algorithms`() {
    // 1. Hard quantize (100% intensity, 0% swing): snaps loose unaligned notes to 1/16th grid (0.25)
    val notes1 = mutableListOf(
        MidiNote(pitch = 60, startBeat = 0.04, lengthBeats = 1.0), // slightly late note near beat 0.0
        MidiNote(pitch = 64, startBeat = 0.28, lengthBeats = 0.5), // loose note near 0.25 (1/16th)
        MidiNote(pitch = 67, startBeat = 0.47, lengthBeats = 0.5)  // early note near 0.50 (1/8th)
    )
    val modified1 = com.example.ui.applyQuantizeAndSwing(
        notes = notes1,
        division = 0.25,
        intensity = 1.0f,
        swing = 0.0f
    )
    assertEquals(3, modified1)
    assertEquals(0.0, notes1[0].startBeat, 0.001)
    assertEquals(0.25, notes1[1].startBeat, 0.001)
    assertEquals(0.50, notes1[2].startBeat, 0.001)

    // 2. Partial quantize intensity (50% intensity): moves note halfway towards grid, retaining human feel
    val notes2 = mutableListOf(
        MidiNote(pitch = 60, startBeat = 0.30, lengthBeats = 1.0) // target is 0.25, delta is -0.05
    )
    com.example.ui.applyQuantizeAndSwing(
        notes = notes2,
        division = 0.25,
        intensity = 0.5f,
        swing = 0.0f
    )
    // newBeat = 0.30 + (0.25 - 0.30) * 0.5 = 0.275
    assertEquals(0.275, notes2[0].startBeat, 0.002)

    // 3. Swing (50% swing, 100% intensity): on-beats stay on beat, off-beats (odd 1/16th) are delayed
    val notes3 = mutableListOf(
        MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 1.0),  // Step 0 (even): on the beat
        MidiNote(pitch = 64, startBeat = 0.25, lengthBeats = 1.0), // Step 1 (odd): off-beat, must be delayed by swing
        MidiNote(pitch = 67, startBeat = 0.50, lengthBeats = 1.0), // Step 2 (even): on the 8th beat
        MidiNote(pitch = 71, startBeat = 0.75, lengthBeats = 1.0)  // Step 3 (odd): off-beat, must be delayed by swing
    )
    com.example.ui.applyQuantizeAndSwing(
        notes = notes3,
        division = 0.25,
        intensity = 1.0f,
        swing = 0.50f
    )
    assertEquals(0.0, notes3[0].startBeat, 0.001)  // On-beat stays unchanged
    assertTrue(notes3[1].startBeat > 0.25)          // Off-beat delayed forward
    assertEquals(0.50, notes3[2].startBeat, 0.001) // Even 8th note stays on grid
    assertTrue(notes3[3].startBeat > 0.75)          // Fourth 16th note delayed forward
  }

  @Test
  fun `verify instrument catalog presets and track assignment`() {
    val allPresets = com.example.model.InstrumentCatalog.presets
    assertTrue(allPresets.size >= 15)

    // Check presence of key categories
    val leads = allPresets.filter { it.category == com.example.model.InstrumentCategory.SYNTH_LEAD }
    val basses = allPresets.filter { it.category == com.example.model.InstrumentCategory.BASS_808 }
    val keys = allPresets.filter { it.category == com.example.model.InstrumentCategory.KEYS_PAD }
    val drums = allPresets.filter { it.category == com.example.model.InstrumentCategory.DRUM_KIT }

    assertTrue(leads.isNotEmpty())
    assertTrue(basses.isNotEmpty())
    assertTrue(keys.isNotEmpty())
    assertTrue(drums.isNotEmpty())

    // Verify finding preset by ID and applying to track
    val cyberLead = com.example.model.InstrumentCatalog.findPresetById("cyber_lead")
    assertNotNull(cyberLead)
    assertEquals("Cyber Lead", cyberLead!!.name)

    val track = TrackData(
        name = "Init Track",
        type = TrackType.SYNTH,
        colorHex = 0xFFFFFFFF
    )

    com.example.model.InstrumentCatalog.applyPresetToTrack(track, cyberLead, renameTrack = true)
    assertEquals("Cyber Lead", track.name)
    assertEquals("Cyber Lead", track.synthPresetName)
    assertEquals(com.example.model.SynthWaveform.SAWTOOTH, track.synthWaveform)
    assertEquals(3800f, track.filterCutoffHz)
    assertEquals(1.3f, track.filterResonance)
    assertEquals(10f, track.attackMs)
    assertEquals(120f, track.decayMs)
    assertEquals(0.8f, track.sustainLevel)
    assertEquals(150f, track.releaseMs)
    assertEquals(0xFF00E5FF, track.colorHex)

    // Verify applying an 808 Bass preset
    val deep808 = com.example.model.InstrumentCatalog.findPresetById("deep_808")
    assertNotNull(deep808)
    com.example.model.InstrumentCatalog.applyPresetToTrack(track, deep808!!, renameTrack = true)
    assertEquals("808 Sub Boom", track.name)
    assertEquals(com.example.model.SynthWaveform.SINE, track.synthWaveform)
    assertEquals(450f, track.filterCutoffHz)
    assertEquals(5f, track.attackMs)
  }

  @Test
  fun `verify mute and solo track isolation logic`() {
    val track1 = TrackData(id = "trk_1", name = "Kick Drum", type = TrackType.DRUM_MACHINE, colorHex = 0xFFFF0055)
    val track2 = TrackData(id = "trk_2", name = "Synth Bass", type = TrackType.SYNTH, colorHex = 0xFFFFD700)
    val track3 = TrackData(id = "trk_3", name = "Lead Melody", type = TrackType.SYNTH, colorHex = 0xFF00E5FF)

    val project = ProjectData(
        title = "Mute_Solo_Test",
        tracks = mutableListOf(track1, track2, track3)
    )

    // Helper logic matching SanwolfAudioEngine.triggerSequencerEvents
    fun isTrackAudible(t: TrackData, proj: ProjectData): Boolean {
      val anySolo = proj.tracks.any { it.solo }
      return !t.muted && (!anySolo || t.solo)
    }

    // 1. Initially all tracks are audible
    assertTrue(isTrackAudible(track1, project))
    assertTrue(isTrackAudible(track2, project))
    assertTrue(isTrackAudible(track3, project))

    // 2. Muting track 1 silences only track 1
    track1.muted = true
    assertFalse(isTrackAudible(track1, project))
    assertTrue(isTrackAudible(track2, project))
    assertTrue(isTrackAudible(track3, project))

    // 3. Unmuting track 1 restores audio
    track1.muted = false
    assertTrue(isTrackAudible(track1, project))

    // 4. Soloing track 2 isolates track 2 and silences track 1 and track 3
    track2.solo = true
    assertFalse(isTrackAudible(track1, project))
    assertTrue(isTrackAudible(track2, project))
    assertFalse(isTrackAudible(track3, project))

    // 5. Multi-solo (soloing track 3 alongside track 2) allows both track 2 and 3 to play
    track3.solo = true
    assertFalse(isTrackAudible(track1, project))
    assertTrue(isTrackAudible(track2, project))
    assertTrue(isTrackAudible(track3, project))

    // 6. If a soloed track is also explicitly muted, it is silenced
    track2.muted = true
    assertFalse(isTrackAudible(track1, project))
    assertFalse(isTrackAudible(track2, project))
    assertTrue(isTrackAudible(track3, project))

    // 7. Clearing all solos restores normal playback (track 2 remains muted)
    track2.solo = false
    track3.solo = false
    assertTrue(isTrackAudible(track1, project))
    assertFalse(isTrackAudible(track2, project))
    assertTrue(isTrackAudible(track3, project))
  }

  @Test
  fun `verify automation lane parameter interpolation and preset shapes`() {
    val lane = com.example.model.AutomationLane(
        targetParam = "Filter Cutoff",
        points = mutableListOf(
            com.example.model.AutomationPoint(0.0, 0.2f),
            com.example.model.AutomationPoint(4.0, 0.8f),
            com.example.model.AutomationPoint(8.0, 0.4f)
        )
    )

    // Exact points
    assertEquals(0.2f, lane.getValueAtBeat(0.0), 0.001f)
    assertEquals(0.8f, lane.getValueAtBeat(4.0), 0.001f)
    assertEquals(0.4f, lane.getValueAtBeat(8.0), 0.001f)

    // Linear interpolation between beats 0.0 and 4.0
    assertEquals(0.5f, lane.getValueAtBeat(2.0), 0.01f)

    // Boundary conditions
    assertEquals(0.2f, lane.getValueAtBeat(-1.0), 0.001f)
    assertEquals(0.4f, lane.getValueAtBeat(12.0), 0.001f)

    // Test parameter display value formatting
    val displayCutoff = lane.toDisplayValue(0.5f)
    assertTrue(displayCutoff.contains("kHz") || displayCutoff.contains("Hz"))

    // Test preset shapes
    lane.applyPresetShape("Ramp Up", totalBeats = 16)
    assertEquals(0.1f, lane.getValueAtBeat(0.0), 0.01f)
    assertEquals(0.95f, lane.getValueAtBeat(16.0), 0.01f)

    lane.applyPresetShape("Sidechain Duck", totalBeats = 16)
    assertTrue(lane.points.size > 2)
    assertEquals(0.15f, lane.getValueAtBeat(0.0), 0.02f)
  }

  @Test
  fun `verify firestore daw project manager save load duplicate and template creation`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = com.example.firebase.FirebaseDawManager(context)

    // 1. Create a rich project with tracks, notes, and automation lanes
    val project = ProjectData(
        title = "Cloud_Wolf_Session",
        bpm = 135,
        swing = 0.12f
    )
    val synthTrack = TrackData(
        name = "Lead Saw",
        type = TrackType.SYNTH,
        colorHex = 0xFF00E5FF,
        synthPresetName = "Cyber Lead",
        synthWaveform = com.example.model.SynthWaveform.SAWTOOTH,
        filterCutoffHz = 3200f
    )
    synthTrack.notes.add(MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 1.0, velocity = 0.9f))
    synthTrack.notes.add(MidiNote(pitch = 64, startBeat = 1.0, lengthBeats = 1.0, velocity = 0.85f))

    val autoLane = com.example.model.AutomationLane(targetParam = "Filter Cutoff")
    autoLane.points.clear()
    autoLane.points.add(com.example.model.AutomationPoint(0.0, 0.3f))
    autoLane.points.add(com.example.model.AutomationPoint(4.0, 0.9f))
    synthTrack.automationLanes.add(autoLane)

    project.tracks.add(synthTrack)

    // 2. Save project to cloud / memory cache
    val saveResult = manager.saveProjectToCloud(project)
    assertTrue(saveResult.isSuccess)
    assertEquals(project.id, saveResult.getOrNull())
    assertTrue(project.isCloudSynced)

    // 3. Load cloud project summaries list
    val summariesResult = manager.loadCloudProjects()
    assertTrue(summariesResult.isSuccess)
    val summaries = summariesResult.getOrNull() ?: emptyList()
    assertTrue(summaries.any { it.id == project.id && it.title == "Cloud_Wolf_Session" })

    // 4. Load full project data
    val loadResult = manager.loadFullProject(project.id)
    assertTrue(loadResult.isSuccess)
    val loadedProject = loadResult.getOrNull()
    assertNotNull(loadedProject)
    assertEquals("Cloud_Wolf_Session", loadedProject!!.title)
    assertEquals(135, loadedProject.bpm)
    assertEquals(1, loadedProject.tracks.size)
    assertEquals("Lead Saw", loadedProject.tracks[0].name)
    assertEquals(2, loadedProject.tracks[0].notes.size)
    assertEquals(1, loadedProject.tracks[0].automationLanes.size)

    // 5. Duplicate project
    val dupResult = manager.duplicateProjectInCloud(project.id, "Cloud_Wolf_Session_COPY")
    assertTrue(dupResult.isSuccess)
    val dupProj = dupResult.getOrNull()
    assertNotNull(dupProj)
    assertEquals("Cloud_Wolf_Session_COPY", dupProj!!.title)
    assertEquals(135, dupProj.bpm)
    assertFalse(dupProj.id == project.id) // New unique ID

    // 6. Rename project
    val renameResult = manager.renameProjectInCloud(project.id, "Renamed_Wolf_Beat")
    assertTrue(renameResult.isSuccess)

    // 7. Delete duplicated project
    val deleteResult = manager.deleteProjectFromCloud(dupProj.id)
    assertTrue(deleteResult.isSuccess)

    // 8. Test genre templates creation
    val trapTemplate = manager.createProjectFromTemplate("Trap 808 Drill")
    assertEquals(140, trapTemplate.bpm)
    assertTrue(trapTemplate.tracks.size >= 4)
    assertTrue(trapTemplate.tracks.any { it.name.contains("808") })

    val cyberTemplate = manager.createProjectFromTemplate("Cyberpunk Synthwave")
    assertEquals(128, cyberTemplate.bpm)
    assertTrue(cyberTemplate.tracks.size >= 4)

    val amapianoTemplate = manager.createProjectFromTemplate("Amapiano Groove")
    assertEquals(113, amapianoTemplate.bpm)
    assertTrue(amapianoTemplate.tracks.any { it.name.contains("Log Drum") })
  }
}
