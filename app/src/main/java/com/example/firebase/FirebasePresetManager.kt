package com.example.firebase

import com.example.model.Preset
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebasePresetManager {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val userId: String?
        get() = auth.currentUser?.uid

    suspend fun savePreset(preset: Preset): Result<String> {
        return runCatching {
            val uid = userId ?: throw IllegalStateException("User not logged in")
            val presetRef = firestore.collection("users")
                .document(uid)
                .collection("presets")
                .document(preset.id)

            val data = mapOf(
                "id" to preset.id,
                "name" to preset.name,
                "genre" to preset.genre,
                "style" to preset.style,
                "synthPresetName" to preset.synthPresetName,
                "synthWaveform" to preset.synthWaveform,
                "filterCutoffHz" to preset.filterCutoffHz,
                "filterResonance" to preset.filterResonance,
                "attackMs" to preset.attackMs,
                "decayMs" to preset.decayMs,
                "sustainLevel" to preset.sustainLevel,
                "releaseMs" to preset.releaseMs
            )
            presetRef.set(data).await()
            preset.id
        }
    }

    suspend fun loadPresets(): Result<List<Preset>> {
        return runCatching {
            val uid = userId ?: return@runCatching emptyList()
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("presets")
                .get()
                .await()

            snapshot.documents.map { doc ->
                Preset(
                    id = doc.getString("id") ?: doc.id,
                    name = doc.getString("name") ?: "Unnamed Preset",
                    genre = doc.getString("genre") ?: "Other",
                    style = doc.getString("style") ?: "Other",
                    synthPresetName = doc.getString("synthPresetName") ?: "Default",
                    synthWaveform = doc.getString("synthWaveform") ?: "SAWTOOTH",
                    filterCutoffHz = doc.getDouble("filterCutoffHz")?.toFloat() ?: 2000f,
                    filterResonance = doc.getDouble("filterResonance")?.toFloat() ?: 1.0f,
                    attackMs = doc.getDouble("attackMs")?.toFloat() ?: 10f,
                    decayMs = doc.getDouble("decayMs")?.toFloat() ?: 150f,
                    sustainLevel = doc.getDouble("sustainLevel")?.toFloat() ?: 0.5f,
                    releaseMs = doc.getDouble("releaseMs")?.toFloat() ?: 200f
                )
            }
        }
    }
}
