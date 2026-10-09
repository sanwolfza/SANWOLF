package com.example.firebase

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AutomationLane
import com.example.model.AutomationPoint
import com.example.model.MasteringConfig
import com.example.model.MidiNote
import com.example.model.ProjectData
import com.example.model.SynthWaveform
import com.example.model.TrackData
import com.example.model.TrackType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class CloudProjectSummary(
    val id: String,
    val title: String,
    val bpm: Int,
    val trackCount: Int,
    val lastSavedTimestamp: Long,
    val isLocalOnly: Boolean = false
)

class FirebaseDawManager(private val context: Context) {

    init {
        try {
            com.google.firebase.FirebaseApp.initializeApp(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("sanwolf_cloud_projects_cache", Context.MODE_PRIVATE)
    }

    // In-memory registry of cached projects for instant fast loading
    private val memoryProjects = mutableMapOf<String, ProjectData>()

    val currentUser: FirebaseUser?
        get() = runCatching { auth.currentUser }.getOrNull()

    val isUserLoggedIn: Boolean
        get() = currentUser != null

    val currentProducerName: String
        get() = currentUser?.displayName ?: currentUser?.email?.substringBefore("@") ?: "Producer_Wolf"

    // Sign in anonymously / guest producer mode
    suspend fun signInGuestProducer(): Result<FirebaseUser> {
        return runCatching {
            val result = auth.signInAnonymously().await()
            val user = result.user ?: throw IllegalStateException("Auth user null")
            user
        }
    }

    // Update producer display name
    suspend fun updateProducerName(newName: String): Result<Unit> {
        return runCatching {
            val user = currentUser ?: auth.signInAnonymously().await().user ?: throw IllegalStateException("No user")
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()
            user.updateProfile(profileUpdates).await()
        }
    }

    // Save project to Firestore for cross-device syncing
    suspend fun saveProjectToCloud(project: ProjectData): Result<String> {
        return runCatching {
            project.lastSavedTimestamp = System.currentTimeMillis()
            project.isCloudSynced = true
            memoryProjects[project.id] = project

            var userId = currentUser?.uid
            if (userId == null) {
                val signResult = runCatching { auth.signInAnonymously().await().user?.uid }.getOrNull()
                userId = signResult ?: "local_guest_user"
            }

            val trackListMap = project.tracks.map { t ->
                val notesMapList = t.notes.map { n ->
                    mapOf(
                        "id" to n.id,
                        "pitch" to n.pitch,
                        "startBeat" to n.startBeat,
                        "lengthBeats" to n.lengthBeats,
                        "velocity" to n.velocity
                    )
                }

                val autoLanesMapList = t.automationLanes.map { al ->
                    mapOf(
                        "id" to al.id,
                        "targetParam" to al.targetParam,
                        "points" to al.points.map { pt ->
                            mapOf("beat" to pt.beat, "value" to pt.value)
                        }
                    )
                }

                val stepsList = t.steps.map { it }
                val velocitiesList = t.stepVelocities.map { it }

                mapOf(
                    "id" to t.id,
                    "name" to t.name,
                    "type" to t.type.name,
                    "colorHex" to t.colorHex,
                    "volume" to t.volume,
                    "pan" to t.pan,
                    "muted" to t.muted,
                    "solo" to t.solo,
                    "stepCount" to t.stepCount,
                    "steps" to stepsList,
                    "stepVelocities" to velocitiesList,
                    "notes" to notesMapList,
                    "automationLanes" to autoLanesMapList,
                    "synthPresetName" to t.synthPresetName,
                    "synthWaveform" to t.synthWaveform.name,
                    "filterCutoffHz" to t.filterCutoffHz,
                    "filterResonance" to t.filterResonance,
                    "attackMs" to t.attackMs,
                    "decayMs" to t.decayMs,
                    "sustainLevel" to t.sustainLevel,
                    "releaseMs" to t.releaseMs,
                    "audioUri" to (t.audioUri ?: ""),
                    "audioFileName" to (t.audioFileName ?: "")
                )
            }

            val masteringMap = mapOf(
                "enabled" to project.masteringConfig.enabled,
                "targetLufs" to project.masteringConfig.targetLufs,
                "lowGainDb" to project.masteringConfig.lowGainDb,
                "midLowGainDb" to project.masteringConfig.midLowGainDb,
                "midGainDb" to project.masteringConfig.midGainDb,
                "highMidGainDb" to project.masteringConfig.highMidGainDb,
                "highGainDb" to project.masteringConfig.highGainDb,
                "stereoWidth" to project.masteringConfig.stereoWidth,
                "compressorThresholdDb" to project.masteringConfig.compressorThresholdDb,
                "limiterCeilingDb" to project.masteringConfig.limiterCeilingDb,
                "profileName" to project.masteringConfig.profileName
            )

            val data = mapOf(
                "id" to project.id,
                "title" to project.title,
                "bpm" to project.bpm,
                "swing" to project.swing,
                "timeSignatureNumerator" to project.timeSignatureNumerator,
                "timeSignatureDenominator" to project.timeSignatureDenominator,
                "masterVolume" to project.masterVolume,
                "trackCount" to project.tracks.size,
                "tracks" to trackListMap,
                "masteringConfig" to masteringMap,
                "collaborationRoomId" to (project.collaborationRoomId ?: ""),
                "lastSavedTimestamp" to project.lastSavedTimestamp,
                "artist" to project.artist,
                "musicalKey" to project.musicalKey,
                "genre" to project.genre,
                "songNotes" to project.songNotes
            )

            // Try saving to Firestore
            runCatching {
                val docRef = firestore.collection("users")
                    .document(userId)
                    .collection("projects")
                    .document(project.id)
                docRef.set(data, SetOptions.merge()).await()
            }

            project.id
        }
    }

    // Load projects list from Firestore (and local cache)
    suspend fun loadCloudProjects(): Result<List<CloudProjectSummary>> {
        return runCatching {
            val list = mutableListOf<CloudProjectSummary>()
            val userId = currentUser?.uid

            if (userId != null) {
                val firestoreResult = runCatching {
                    val snapshot = firestore.collection("users")
                        .document(userId)
                        .collection("projects")
                        .get()
                        .await()

                    snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val title = doc.getString("title") ?: "Untitled Project"
                        val bpm = doc.getLong("bpm")?.toInt() ?: 120
                        val trackCount = doc.getLong("trackCount")?.toInt() ?: (doc.get("tracks") as? List<*>)?.size ?: 0
                        val timestamp = doc.getLong("lastSavedTimestamp") ?: System.currentTimeMillis()
                        CloudProjectSummary(id, title, bpm, trackCount, timestamp, isLocalOnly = false)
                    }
                }.getOrNull()

                if (firestoreResult != null && firestoreResult.isNotEmpty()) {
                    list.addAll(firestoreResult)
                }
            }

            // Include any memory-cached projects not yet synced
            memoryProjects.forEach { (id, prj) ->
                if (list.none { it.id == id }) {
                    list.add(
                        CloudProjectSummary(
                            id = prj.id,
                            title = prj.title,
                            bpm = prj.bpm,
                            trackCount = prj.tracks.size,
                            lastSavedTimestamp = prj.lastSavedTimestamp,
                            isLocalOnly = true
                        )
                    )
                }
            }

            list.sortedByDescending { it.lastSavedTimestamp }
        }
    }

    // Load full project data from Firestore or local cache
    suspend fun loadFullProject(projectId: String): Result<ProjectData> {
        return runCatching {
            // Check memory cache first
            val cached = memoryProjects[projectId]
            val userId = currentUser?.uid

            if (userId != null) {
                val docSnapshot = runCatching {
                    firestore.collection("users")
                        .document(userId)
                        .collection("projects")
                        .document(projectId)
                        .get()
                        .await()
                }.getOrNull()

                if (docSnapshot != null && docSnapshot.exists()) {
                    val title = docSnapshot.getString("title") ?: "Untitled Project"
                    val bpm = docSnapshot.getLong("bpm")?.toInt() ?: 120
                    val swing = (docSnapshot.getDouble("swing") ?: 0.15).toFloat()
                    val masterVol = (docSnapshot.getDouble("masterVolume") ?: 0.85).toFloat()
                    val timeNum = docSnapshot.getLong("timeSignatureNumerator")?.toInt() ?: 4
                    val timeDen = docSnapshot.getLong("timeSignatureDenominator")?.toInt() ?: 4
                    val collabRoom = docSnapshot.getString("collaborationRoomId")
                    val timestamp = docSnapshot.getLong("lastSavedTimestamp") ?: System.currentTimeMillis()

                    val proj = ProjectData(
                        id = projectId,
                        title = title,
                        bpm = bpm,
                        swing = swing,
                        timeSignatureNumerator = timeNum,
                        timeSignatureDenominator = timeDen,
                        masterVolume = masterVol,
                        collaborationRoomId = collabRoom,
                        isCloudSynced = true,
                        lastSavedTimestamp = timestamp,
                        artist = docSnapshot.getString("artist") ?: "",
                        musicalKey = docSnapshot.getString("musicalKey") ?: "",
                        genre = docSnapshot.getString("genre") ?: "",
                        songNotes = docSnapshot.getString("songNotes") ?: ""
                    )

                    // Parse tracks
                    val rawTracks = docSnapshot.get("tracks") as? List<Map<String, Any?>>
                    if (rawTracks != null) {
                        for (rawT in rawTracks) {
                            val trackId = rawT["id"] as? String ?: UUID.randomUUID().toString()
                            val name = rawT["name"] as? String ?: "Track"
                            val typeName = rawT["type"] as? String ?: "SYNTH"
                            val type = runCatching { TrackType.valueOf(typeName) }.getOrDefault(TrackType.SYNTH)
                            val colorHex = (rawT["colorHex"] as? Number)?.toLong() ?: 0xFF00E5FF
                            val volume = (rawT["volume"] as? Number)?.toFloat() ?: 0.8f
                            val pan = (rawT["pan"] as? Number)?.toFloat() ?: 0.0f
                            val muted = rawT["muted"] as? Boolean ?: false
                            val solo = rawT["solo"] as? Boolean ?: false
                            val stepCount = (rawT["stepCount"] as? Number)?.toInt() ?: 16

                            val track = TrackData(
                                id = trackId,
                                name = name,
                                type = type,
                                colorHex = colorHex,
                                volume = volume,
                                pan = pan,
                                muted = muted,
                                solo = solo,
                                stepCount = stepCount
                            )

                            // Steps
                            val rawSteps = rawT["steps"] as? List<Boolean>
                            if (rawSteps != null) {
                                rawSteps.take(32).forEachIndexed { i, b ->
                                    track.steps[i] = b
                                }
                            }

                            // Step Velocities
                            val rawVels = rawT["stepVelocities"] as? List<Number>
                            if (rawVels != null) {
                                rawVels.take(32).forEachIndexed { i, num ->
                                    track.stepVelocities[i] = num.toFloat()
                                }
                            }

                            // Synth settings
                            track.synthPresetName = rawT["synthPresetName"] as? String ?: "Lead Wolf"
                            val rawWaveform = rawT["synthWaveform"] as? String ?: "SAWTOOTH"
                            track.synthWaveform = runCatching { SynthWaveform.valueOf(rawWaveform) }.getOrDefault(SynthWaveform.SAWTOOTH)
                            track.filterCutoffHz = (rawT["filterCutoffHz"] as? Number)?.toFloat() ?: 2500f
                            track.filterResonance = (rawT["filterResonance"] as? Number)?.toFloat() ?: 1.2f
                            track.attackMs = (rawT["attackMs"] as? Number)?.toFloat() ?: 10f
                            track.decayMs = (rawT["decayMs"] as? Number)?.toFloat() ?: 150f
                            track.sustainLevel = (rawT["sustainLevel"] as? Number)?.toFloat() ?: 0.6f
                            track.releaseMs = (rawT["releaseMs"] as? Number)?.toFloat() ?: 200f
                            track.audioUri = rawT["audioUri"] as? String
                            track.audioFileName = rawT["audioFileName"] as? String

                            // Notes
                            val rawNotes = rawT["notes"] as? List<Map<String, Any?>>
                            if (rawNotes != null) {
                                for (rn in rawNotes) {
                                    val noteId = rn["id"] as? String ?: UUID.randomUUID().toString()
                                    val pitch = (rn["pitch"] as? Number)?.toInt() ?: 60
                                    val startB = (rn["startBeat"] as? Number)?.toDouble() ?: 0.0
                                    val lenB = (rn["lengthBeats"] as? Number)?.toDouble() ?: 1.0
                                    val vel = (rn["velocity"] as? Number)?.toFloat() ?: 0.8f
                                    track.notes.add(MidiNote(noteId, pitch, startB, lenB, vel))
                                }
                            }

                            // Automation Lanes
                            val rawAuto = rawT["automationLanes"] as? List<Map<String, Any?>>
                            if (rawAuto != null) {
                                for (ra in rawAuto) {
                                    val autoId = ra["id"] as? String ?: UUID.randomUUID().toString()
                                    val targetParam = ra["targetParam"] as? String ?: "Filter Cutoff"
                                    val lane = AutomationLane(id = autoId, targetParam = targetParam, points = mutableListOf())
                                    val rawPoints = ra["points"] as? List<Map<String, Any?>>
                                    if (rawPoints != null) {
                                        for (rpt in rawPoints) {
                                            val b = (rpt["beat"] as? Number)?.toDouble() ?: 0.0
                                            val v = (rpt["value"] as? Number)?.toFloat() ?: 0.5f
                                            lane.points.add(AutomationPoint(b, v))
                                        }
                                    }
                                    track.automationLanes.add(lane)
                                }
                            }

                            proj.tracks.add(track)
                        }
                    }

                    // Mastering config
                    val rawMastering = docSnapshot.get("masteringConfig") as? Map<String, Any?>
                    if (rawMastering != null) {
                        proj.masteringConfig = MasteringConfig(
                            enabled = rawMastering["enabled"] as? Boolean ?: true,
                            targetLufs = (rawMastering["targetLufs"] as? Number)?.toFloat() ?: -14f,
                            lowGainDb = (rawMastering["lowGainDb"] as? Number)?.toFloat() ?: 1.5f,
                            midLowGainDb = (rawMastering["midLowGainDb"] as? Number)?.toFloat() ?: -0.5f,
                            midGainDb = (rawMastering["midGainDb"] as? Number)?.toFloat() ?: 0.0f,
                            highMidGainDb = (rawMastering["highMidGainDb"] as? Number)?.toFloat() ?: 1.0f,
                            highGainDb = (rawMastering["highGainDb"] as? Number)?.toFloat() ?: 2.0f,
                            stereoWidth = (rawMastering["stereoWidth"] as? Number)?.toFloat() ?: 1.25f,
                            compressorThresholdDb = (rawMastering["compressorThresholdDb"] as? Number)?.toFloat() ?: -12f,
                            limiterCeilingDb = (rawMastering["limiterCeilingDb"] as? Number)?.toFloat() ?: -0.3f,
                            profileName = rawMastering["profileName"] as? String ?: "Modern EDM / Trap Punch"
                        )
                    }

                    memoryProjects[projectId] = proj
                    return@runCatching proj
                }
            }

            if (cached != null) {
                return@runCatching cached
            }

            throw IllegalStateException("Project $projectId not found in Firestore or memory cache.")
        }
    }

    // Delete project from Firestore
    suspend fun deleteProjectFromCloud(projectId: String): Result<Unit> {
        return runCatching {
            memoryProjects.remove(projectId)
            val userId = currentUser?.uid
            if (userId != null) {
                firestore.collection("users")
                    .document(userId)
                    .collection("projects")
                    .document(projectId)
                    .delete()
                    .await()
            }
        }
    }

    // Rename project
    suspend fun renameProjectInCloud(projectId: String, newTitle: String): Result<Unit> {
        return runCatching {
            memoryProjects[projectId]?.title = newTitle
            val userId = currentUser?.uid
            if (userId != null) {
                firestore.collection("users")
                    .document(userId)
                    .collection("projects")
                    .document(projectId)
                    .update("title", newTitle, "lastSavedTimestamp", System.currentTimeMillis())
                    .await()
            }
        }
    }

    // Duplicate project in Firestore
    suspend fun duplicateProjectInCloud(originalProjectId: String, newTitle: String): Result<ProjectData> {
        return runCatching {
            val original = loadFullProject(originalProjectId).getOrThrow()
            val duplicated = ProjectData(
                id = UUID.randomUUID().toString(),
                title = newTitle,
                bpm = original.bpm,
                swing = original.swing,
                timeSignatureNumerator = original.timeSignatureNumerator,
                timeSignatureDenominator = original.timeSignatureDenominator,
                masterVolume = original.masterVolume,
                masteringConfig = original.masteringConfig.copy(),
                isCloudSynced = false,
                lastSavedTimestamp = System.currentTimeMillis()
            )

            // Deep copy tracks
            for (t in original.tracks) {
                val copyTrack = TrackData(
                    id = UUID.randomUUID().toString(),
                    name = t.name,
                    type = t.type,
                    colorHex = t.colorHex,
                    volume = t.volume,
                    pan = t.pan,
                    muted = t.muted,
                    solo = t.solo,
                    stepCount = t.stepCount,
                    synthPresetName = t.synthPresetName,
                    synthWaveform = t.synthWaveform,
                    filterCutoffHz = t.filterCutoffHz,
                    filterResonance = t.filterResonance,
                    attackMs = t.attackMs,
                    decayMs = t.decayMs,
                    sustainLevel = t.sustainLevel,
                    releaseMs = t.releaseMs,
                    audioUri = t.audioUri,
                    audioFileName = t.audioFileName
                )
                System.arraycopy(t.steps, 0, copyTrack.steps, 0, 32)
                System.arraycopy(t.stepVelocities, 0, copyTrack.stepVelocities, 0, 32)

                t.notes.forEach { n ->
                    copyTrack.notes.add(n.copy(id = UUID.randomUUID().toString()))
                }
                t.automationLanes.forEach { al ->
                    val copyLane = AutomationLane(
                        id = UUID.randomUUID().toString(),
                        targetParam = al.targetParam,
                        points = al.points.map { it.copy() }.toMutableList()
                    )
                    copyTrack.automationLanes.add(copyLane)
                }
                duplicated.tracks.add(copyTrack)
            }

            saveProjectToCloud(duplicated).getOrThrow()
            duplicated
        }
    }

    // Create project from rich genre templates
    fun createProjectFromTemplate(templateName: String): ProjectData {
        val newProj = ProjectData(
            id = UUID.randomUUID().toString(),
            lastSavedTimestamp = System.currentTimeMillis()
        )

        when (templateName) {
            "Trap 808 Drill" -> {
                newProj.title = "TRAP_DRILL_808"
                newProj.bpm = 140
                newProj.swing = 0.08f
                newProj.tracks.add(
                    TrackData(
                        name = "808 Heavy Kick",
                        type = TrackType.DRUM_MACHINE,
                        colorHex = 0xFFFF1744,
                        stepCount = 16,
                        steps = booleanArrayOf(
                            true, false, false, false,
                            false, false, true, false,
                            true, false, false, false,
                            false, false, false, true
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "Snare / Clap 808",
                        type = TrackType.DRUM_MACHINE,
                        colorHex = 0xFFFF5722,
                        stepCount = 16,
                        steps = booleanArrayOf(
                            false, false, false, false,
                            true, false, false, false,
                            false, false, false, false,
                            true, false, false, false
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "Rolling Hi-Hats",
                        type = TrackType.DRUM_MACHINE,
                        colorHex = 0xFFFFD700,
                        stepCount = 16,
                        steps = booleanArrayOf(
                            true, true, true, true,
                            true, true, true, true,
                            true, true, true, true,
                            true, true, true, true
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "808 Sub Bass",
                        type = TrackType.SYNTH,
                        colorHex = 0xFFD500F9,
                        synthPresetName = "808 Sub Boom",
                        synthWaveform = SynthWaveform.SINE,
                        filterCutoffHz = 450f,
                        notes = mutableListOf(
                            MidiNote(pitch = 36, startBeat = 0.0, lengthBeats = 1.5, velocity = 0.95f),
                            MidiNote(pitch = 36, startBeat = 2.0, lengthBeats = 1.0, velocity = 0.9f),
                            MidiNote(pitch = 34, startBeat = 3.5, lengthBeats = 0.5, velocity = 0.85f),
                            MidiNote(pitch = 39, startBeat = 4.0, lengthBeats = 2.0, velocity = 0.95f),
                            MidiNote(pitch = 38, startBeat = 6.0, lengthBeats = 2.0, velocity = 0.9f)
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "Dark Pluck Melody",
                        type = TrackType.SYNTH,
                        colorHex = 0xFF00E5FF,
                        synthPresetName = "Neon Pluck",
                        synthWaveform = SynthWaveform.SQUARE,
                        filterCutoffHz = 2200f,
                        notes = mutableListOf(
                            MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 0.5, velocity = 0.85f),
                            MidiNote(pitch = 63, startBeat = 0.5, lengthBeats = 0.5, velocity = 0.8f),
                            MidiNote(pitch = 67, startBeat = 1.0, lengthBeats = 1.0, velocity = 0.9f),
                            MidiNote(pitch = 65, startBeat = 2.0, lengthBeats = 1.0, velocity = 0.85f),
                            MidiNote(pitch = 63, startBeat = 3.0, lengthBeats = 1.0, velocity = 0.8f),
                            MidiNote(pitch = 62, startBeat = 4.0, lengthBeats = 2.0, velocity = 0.85f)
                        )
                    )
                )
            }
            "Cyberpunk Synthwave" -> {
                newProj.title = "CYBER_SYNTHWAVE_1984"
                newProj.bpm = 128
                newProj.swing = 0.0f
                newProj.tracks.add(
                    TrackData(
                        name = "Electro 909 Kick",
                        type = TrackType.DRUM_MACHINE,
                        colorHex = 0xFF00E676,
                        stepCount = 16,
                        steps = booleanArrayOf(
                            true, false, false, false,
                            true, false, false, false,
                            true, false, false, false,
                            true, false, false, false
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "Gated Snare & Claps",
                        type = TrackType.DRUM_MACHINE,
                        colorHex = 0xFFFF007F,
                        stepCount = 16,
                        steps = booleanArrayOf(
                            false, false, false, false,
                            true, false, false, false,
                            false, false, false, false,
                            true, false, false, false
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "SuperSaw Anthem",
                        type = TrackType.SYNTH,
                        colorHex = 0xFF00E5FF,
                        synthPresetName = "SuperSaw Anthem",
                        synthWaveform = SynthWaveform.SAWTOOTH,
                        filterCutoffHz = 4200f,
                        notes = mutableListOf(
                            MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 1.5, velocity = 0.9f),
                            MidiNote(pitch = 64, startBeat = 0.0, lengthBeats = 1.5, velocity = 0.85f),
                            MidiNote(pitch = 67, startBeat = 0.0, lengthBeats = 1.5, velocity = 0.9f),
                            MidiNote(pitch = 62, startBeat = 2.0, lengthBeats = 2.0, velocity = 0.9f),
                            MidiNote(pitch = 65, startBeat = 2.0, lengthBeats = 2.0, velocity = 0.85f),
                            MidiNote(pitch = 69, startBeat = 2.0, lengthBeats = 2.0, velocity = 0.9f)
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "Acid 303 Bassline",
                        type = TrackType.SYNTH,
                        colorHex = 0xFF76FF03,
                        synthPresetName = "Acid 303 Resonator",
                        synthWaveform = SynthWaveform.SAWTOOTH,
                        filterCutoffHz = 1600f,
                        filterResonance = 2.4f,
                        notes = mutableListOf(
                            MidiNote(pitch = 48, startBeat = 0.0, lengthBeats = 0.5, velocity = 0.9f),
                            MidiNote(pitch = 48, startBeat = 0.5, lengthBeats = 0.5, velocity = 0.8f),
                            MidiNote(pitch = 51, startBeat = 1.0, lengthBeats = 0.5, velocity = 0.95f),
                            MidiNote(pitch = 48, startBeat = 1.5, lengthBeats = 0.5, velocity = 0.8f),
                            MidiNote(pitch = 55, startBeat = 2.0, lengthBeats = 1.0, velocity = 0.9f)
                        )
                    )
                )
            }
            "Amapiano Groove" -> {
                newProj.title = "AMAPIANO_SUNSET_GROOVE"
                newProj.bpm = 113
                newProj.swing = 0.22f
                newProj.tracks.add(
                    TrackData(
                        name = "Amapiano Log Drum",
                        type = TrackType.DRUM_MACHINE,
                        colorHex = 0xFFFFD700,
                        stepCount = 16,
                        steps = booleanArrayOf(
                            true, false, false, true,
                            false, false, true, false,
                            false, true, false, false,
                            true, false, true, false
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "Shaker Polyrhythm",
                        type = TrackType.DRUM_MACHINE,
                        colorHex = 0xFF1DE9B6,
                        stepCount = 12,
                        steps = booleanArrayOf(
                            true, false, true, true,
                            false, true, true, false,
                            true, true, false, true
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "Vintage Rhodes E-Piano",
                        type = TrackType.SYNTH,
                        colorHex = 0xFFFFC107,
                        synthPresetName = "Vintage Rhodes E-Piano",
                        synthWaveform = SynthWaveform.TRIANGLE,
                        filterCutoffHz = 2400f,
                        notes = mutableListOf(
                            MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 2.0, velocity = 0.85f),
                            MidiNote(pitch = 64, startBeat = 0.0, lengthBeats = 2.0, velocity = 0.8f),
                            MidiNote(pitch = 67, startBeat = 0.0, lengthBeats = 2.0, velocity = 0.85f),
                            MidiNote(pitch = 71, startBeat = 0.0, lengthBeats = 2.0, velocity = 0.8f),
                            MidiNote(pitch = 57, startBeat = 2.5, lengthBeats = 1.5, velocity = 0.85f),
                            MidiNote(pitch = 60, startBeat = 2.5, lengthBeats = 1.5, velocity = 0.8f),
                            MidiNote(pitch = 64, startBeat = 2.5, lengthBeats = 1.5, velocity = 0.85f)
                        )
                    )
                )
            }
            "Lo-Fi Midnight Chill" -> {
                newProj.title = "LOFI_MIDNIGHT_TAPE"
                newProj.bpm = 84
                newProj.swing = 0.28f
                newProj.tracks.add(
                    TrackData(
                        name = "Boom Bap Dusty Kit",
                        type = TrackType.DRUM_MACHINE,
                        colorHex = 0xFFFF9100,
                        stepCount = 16,
                        steps = booleanArrayOf(
                            true, false, false, false,
                            false, false, true, false,
                            false, true, false, false,
                            false, false, true, false
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "Lo-Fi Tape Organ",
                        type = TrackType.SYNTH,
                        colorHex = 0xFFFF9100,
                        synthPresetName = "Lo-Fi Tape Organ",
                        synthWaveform = SynthWaveform.SQUARE,
                        filterCutoffHz = 1600f,
                        notes = mutableListOf(
                            MidiNote(pitch = 57, startBeat = 0.0, lengthBeats = 2.0, velocity = 0.75f),
                            MidiNote(pitch = 60, startBeat = 0.0, lengthBeats = 2.0, velocity = 0.7f),
                            MidiNote(pitch = 64, startBeat = 0.0, lengthBeats = 2.0, velocity = 0.75f),
                            MidiNote(pitch = 53, startBeat = 2.0, lengthBeats = 2.0, velocity = 0.75f),
                            MidiNote(pitch = 57, startBeat = 2.0, lengthBeats = 2.0, velocity = 0.7f),
                            MidiNote(pitch = 60, startBeat = 2.0, lengthBeats = 2.0, velocity = 0.75f)
                        )
                    )
                )
                newProj.tracks.add(
                    TrackData(
                        name = "Sub Zero Bass",
                        type = TrackType.SYNTH,
                        colorHex = 0xFF2979FF,
                        synthPresetName = "Sub Zero Triangle",
                        synthWaveform = SynthWaveform.TRIANGLE,
                        filterCutoffHz = 650f,
                        notes = mutableListOf(
                            MidiNote(pitch = 45, startBeat = 0.0, lengthBeats = 1.5, velocity = 0.8f),
                            MidiNote(pitch = 41, startBeat = 2.0, lengthBeats = 1.5, velocity = 0.8f)
                        )
                    )
                )
            }
            else -> {
                // Empty 4-track template
                newProj.title = "NEW_SESSION_${(100..999).random()}"
                newProj.bpm = 120
                newProj.tracks.add(TrackData(name = "Kick", type = TrackType.DRUM_MACHINE, colorHex = 0xFFFF1744))
                newProj.tracks.add(TrackData(name = "Snare", type = TrackType.DRUM_MACHINE, colorHex = 0xFFFF9100))
                newProj.tracks.add(TrackData(name = "Hi-Hat", type = TrackType.DRUM_MACHINE, colorHex = 0xFFFFD700))
                newProj.tracks.add(TrackData(name = "Synth Lead", type = TrackType.SYNTH, colorHex = 0xFF00E5FF))
            }
        }

        memoryProjects[newProj.id] = newProj
        return newProj
    }

    // Create or join a Real-Time Producer Collaboration Room
    suspend fun joinOrCreateCollaborationRoom(
        roomId: String = UUID.randomUUID().toString().take(6).uppercase(),
        project: ProjectData
    ): Result<String> {
        return runCatching {
            val user = currentUser ?: auth.signInAnonymously().await().user
                ?: throw IllegalStateException("Producer identity required")

            val roomRef = firestore.collection("collab_rooms").document(roomId)
            val roomData = mapOf(
                "roomId" to roomId,
                "hostUserId" to user.uid,
                "hostName" to currentProducerName,
                "projectTitle" to project.title,
                "bpm" to project.bpm,
                "activeProducers" to listOf(currentProducerName),
                "lastUpdate" to System.currentTimeMillis()
            )
            roomRef.set(roomData, SetOptions.merge()).await()
            project.collaborationRoomId = roomId
            roomId
        }
    }

    fun signOut() {
        runCatching { auth.signOut() }
    }
}

