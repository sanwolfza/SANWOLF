package com.example.audio

import android.content.Context
import android.content.Context.MODE_PRIVATE
import org.json.JSONArray
import org.json.JSONObject

data class ImportedSampleMetadata(
    val sampleId: String,
    val name: String,
    val filePath: String,
    val category: String
)

class UserSampleRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("sanwolf_imported_samples", MODE_PRIVATE)

    fun saveImportedSample(metadata: ImportedSampleMetadata) {
        val list = getImportedSamples().toMutableList()
        list.removeAll { it.sampleId == metadata.sampleId }
        list.add(metadata)
        
        val jsonArray = JSONArray()
        list.forEach { item ->
            jsonArray.put(JSONObject().apply {
                put("sampleId", item.sampleId)
                put("name", item.name)
                put("filePath", item.filePath)
                put("category", item.category)
            })
        }
        prefs.edit().putString("samples_json", jsonArray.toString()).apply()

        val cat = runCatching { InstrumentCategory.valueOf(metadata.category) }
            .getOrDefault(InstrumentCategory.WORLD_PERCUSSION)
        val file = java.io.File(metadata.filePath)
        val sample = InstrumentSample(
            sampleId = metadata.sampleId,
            name = metadata.name,
            rootPitch = 60,
            samplePath = metadata.filePath,
            volume = 1.0f,
            isAvailable = file.exists()
        )
        InstrumentLibrary.registerUserSample(
            InstrumentDefinition(
                id = "user.imported.${metadata.sampleId}",
                name = metadata.name,
                category = cat,
                subCategory = "User Imported",
                isDrum = true,
                drumType = "user_sample",
                voiceType = "user_sample",
                sample = sample,
                isUserImported = true
            )
        )
    }

    fun getImportedSamples(): List<ImportedSampleMetadata> {
        val jsonStr = prefs.getString("samples_json", "[]") ?: "[]"
        val list = mutableListOf<ImportedSampleMetadata>()
        runCatching {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ImportedSampleMetadata(
                        sampleId = obj.getString("sampleId"),
                        name = obj.getString("name"),
                        filePath = obj.getString("filePath"),
                        category = obj.getString("category")
                    )
                )
            }
        }
        return list
    }

    init {
        getImportedSamples().forEach { meta ->
            val cat = runCatching { InstrumentCategory.valueOf(meta.category) }
                .getOrDefault(InstrumentCategory.WORLD_PERCUSSION)
            val file = java.io.File(meta.filePath)
            val sample = InstrumentSample(
                sampleId = meta.sampleId,
                name = meta.name,
                rootPitch = 60,
                samplePath = meta.filePath,
                volume = 1.0f,
                isAvailable = file.exists()
            )
            InstrumentLibrary.registerUserSample(
                InstrumentDefinition(
                    id = "user.imported.${meta.sampleId}",
                    name = meta.name,
                    category = cat,
                    subCategory = "User Imported",
                    isDrum = true,
                    drumType = "user_sample",
                    voiceType = "user_sample",
                    sample = sample,
                    isUserImported = true
                )
            )
        }
    }
}
