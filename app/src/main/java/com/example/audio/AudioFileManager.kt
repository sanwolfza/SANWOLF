package com.example.audio

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages audio sample files and persistent user-imported samples in SANWOLF Audio Studio.
 * Follows authoritative architecture:
 * AudioFileManager -> sample metadata -> InstrumentSample -> instrument assignment -> InstrumentLibrary -> playback/render/mixer
 */
class AudioFileManager(private val context: Context) {

    private val samplesDir = File(context.filesDir, "imported_samples").apply {
        if (!exists()) mkdirs()
    }

    private val userSampleRepo = UserSampleRepository(context)

    fun getTempAudioFile(name: String): File {
        return File(context.cacheDir, "$name.wav")
    }

    /**
     * Imports a user audio file (from URI or path), saves it to persistent app storage,
     * builds the InstrumentSample metadata, creates an InstrumentDefinition,
     * persists it across app restarts, and registers it in InstrumentLibrary.
     */
    fun importUserSample(
        sourceUri: Uri,
        displayName: String,
        category: InstrumentCategory = InstrumentCategory.WORLD_PERCUSSION,
        rootPitch: Int = 60,
        isDrum: Boolean = true
    ): InstrumentDefinition {
        val sampleId = UUID.randomUUID().toString().take(8)
        val targetFile = File(samplesDir, "sample_${sampleId}.wav")

        // Copy stream into persistent internal storage
        runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        val instrumentId = "user.sample.${sampleId}"
        val sample = InstrumentSample(
            sampleId = sampleId,
            name = displayName,
            rootPitch = rootPitch,
            samplePath = targetFile.absolutePath,
            volume = 1.0f,
            isAvailable = targetFile.exists()
        )

        val definition = InstrumentDefinition(
            id = instrumentId,
            name = displayName,
            category = category,
            subCategory = "User Imported",
            isDrum = isDrum,
            drumType = if (isDrum) "user_sample" else "",
            voiceType = "user_sample",
            sample = sample,
            isUserImported = true
        )

        // Persist metadata
        userSampleRepo.saveImportedSample(
            ImportedSampleMetadata(
                sampleId = sampleId,
                name = displayName,
                filePath = targetFile.absolutePath,
                category = category.name
            )
        )

        // Register in authoritative InstrumentLibrary
        InstrumentLibrary.registerUserSample(definition)

        return definition
    }
}
