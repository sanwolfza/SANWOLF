package com.sanwolf.daw.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sanwolf.daw.audio.ProjectManager
import com.sanwolf.daw.model.ProjectData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Internal (editable) project saving: which project is open, autosave, and flushing on stop.
 *
 * - Every change is autosaved about 2 s after the edits stop ([ProjectLifecycleEffects]).
 * - A project that was never saved gets a file id on its first autosave, so nothing is lost.
 * - On ON_STOP (app backgrounded, screen off) and in DawViewModel.onCleared the project is
 *   flushed immediately.
 * - The open project's file id is remembered; on the next launch it is reopened as it was left.
 *
 * All writes go through one background thread in order, so an older autosave can never land
 * after a newer one.
 */
object ProjectSession {
    private const val TAG = "ProjectSession"
    private const val PREFS = "sanwolf_daw_prefs"
    private const val KEY_LAST_PROJECT = "last_project_file_id"
    private const val KEY_LAST_TRACK = "last_selected_track"

    /** Autosave fires once the project has been unchanged for this long after an edit. */
    const val AUTOSAVE_QUIET_MS = 1500L

    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SanwolfProjectSave")
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastProjectId(context: Context): String? = prefs(context).getString(KEY_LAST_PROJECT, null)

    fun setLastProjectId(context: Context, fileId: String?) {
        prefs(context).edit().apply {
            if (fileId == null) remove(KEY_LAST_PROJECT) else putString(KEY_LAST_PROJECT, fileId)
        }.apply()
    }

    /** Synchronous (commit) clear, so it is on disk even if the process dies right after. */
    fun clearLastProjectIdNow(context: Context) {
        runCatching { prefs(context).edit().remove(KEY_LAST_PROJECT).commit() }
    }

    fun lastSelectedTrack(context: Context): Int = prefs(context).getInt(KEY_LAST_TRACK, 0)

    fun setLastSelectedTrack(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_LAST_TRACK, index).apply()
    }

    /** Queues a write; [onDone] runs on the main thread. */
    fun submitWrite(pm: ProjectManager, fileId: String, json: String, onDone: (Result<Unit>) -> Unit = {}) {
        writer.execute {
            val res = pm.writeJson(fileId, json)
            res.onFailure { Log.e(TAG, "Autosave of $fileId failed", it) }
            mainHandler.post { onDone(res) }
        }
    }

    /** Queues a write behind any pending ones and waits for it (bounded). For onCleared only. */
    fun writeBlocking(pm: ProjectManager, fileId: String, json: String): Result<Unit> =
        runCatching { writer.submit(Callable { pm.writeJson(fileId, json) }).get(5, TimeUnit.SECONDS).getOrThrow() }

    /**
     * Saves the working project now if it changed since the last save. Call on the UI thread.
     * A never-saved project gets a new file id. Returns the file id written to, or null when
     * there was nothing to save.
     */
    fun flushNow(vm: DawViewModel, pm: ProjectManager, context: Context): String? {
        val json = runCatching { pm.toJson(vm.project) }.getOrNull() ?: return null
        if (json == vm.savedProjectJson.value) return null
        val id = vm.currentProjectFileId.value ?: pm.newFileId().also { vm.currentProjectFileId.value = it }
        vm.savedProjectJson.value = json
        vm.isProjectDirty.value = false
        setLastProjectId(context.applicationContext, id)
        submitWrite(pm, id, json) { res ->
            if (res.isFailure && vm.currentProjectFileId.value == id) vm.isProjectDirty.value = true
            vm.projectListVersion.intValue++
        }
        return id
    }
}

/**
 * Restores the last project on first composition, then autosaves while the DAW is on screen and
 * flushes on ON_STOP. Call once from SanwolfDawApp.
 *
 * [applyLoadedProject] is SanwolfDawApp's loader (copies a project into the working instance,
 * rebuilds stems, bumps versions).
 */
@Composable
fun ProjectLifecycleEffects(
    vm: DawViewModel,
    projectManager: ProjectManager,
    applyLoadedProject: (ProjectData) -> Unit
) {
    val appContext = LocalContext.current.applicationContext

    LaunchedEffect(Unit) {
        // 1. Reopen the project that was open last time, exactly as it was saved.
        if (!vm.sessionRestoreAttempted) {
            vm.sessionRestoreAttempted = true
            val lastId = ProjectSession.lastProjectId(appContext)
            if (lastId != null && vm.currentProjectFileId.value == null) {
                // Forget the id while restoring: if opening this project crashes the app, the next
                // launch starts clean instead of crashing again on the same file (crash loop).
                ProjectSession.clearLastProjectIdNow(appContext)
                com.sanwolf.daw.CrashReporter.breadcrumb("restore last project")
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { if (projectManager.exists(lastId)) projectManager.load(lastId).getOrNull() else null }.getOrNull()
                }
                val restored = loaded != null && try {
                    applyLoadedProject(loaded)
                    vm.currentProjectFileId.value = lastId
                    vm.savedProjectJson.value = runCatching { projectManager.toJson(vm.project) }.getOrNull()
                    vm.isProjectDirty.value = false
                    vm.undoRedoManager.clear()
                    val sel = ProjectSession.lastSelectedTrack(appContext)
                    vm.selectedTrackIndex.intValue = if (sel in vm.project.tracks.indices) sel else 0
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ProjectSession", "Reopening $lastId failed", e)
                    com.sanwolf.daw.CrashReporter.breadcrumb("restore failed: ${e.javaClass.simpleName}: ${e.message}")
                    false
                }
                if (restored) {
                    ProjectSession.setLastProjectId(appContext, lastId)
                } else {
                    Log.w("ProjectSession", "Last project $lastId could not be reopened")
                }
            }
        }

        // 2. Remember which project (and track) is open, for the next launch.
        launch {
            snapshotFlow { vm.currentProjectFileId.value }.collect { ProjectSession.setLastProjectId(appContext, it) }
        }
        launch {
            snapshotFlow { vm.selectedTrackIndex.intValue }.collect { ProjectSession.setLastSelectedTrack(appContext, it) }
        }

        // 3. Autosave: ~2 s after the last change. Many edits mutate the project in place
        // without a state change, so compare serialized snapshots.
        var lastSeen: String? = null
        var changedAt = 0L
        while (true) {
            delay(1000)
            val json = runCatching { projectManager.toJson(vm.project) }.getOrNull() ?: continue
            if (json == vm.savedProjectJson.value) {
                lastSeen = null
                continue
            }
            val now = SystemClock.elapsedRealtime()
            if (json != lastSeen) {
                lastSeen = json
                changedAt = now
                continue
            }
            if (now - changedAt >= ProjectSession.AUTOSAVE_QUIET_MS) {
                ProjectSession.flushNow(vm, projectManager, appContext)
                lastSeen = null
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        ProjectSession.flushNow(vm, projectManager, appContext)
    }
}
