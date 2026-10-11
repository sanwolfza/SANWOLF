package com.sanwolf.daw

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash capture for test builds (there is no Crashlytics in the app).
 *
 * - [breadcrumb] keeps the last [MAX_BREADCRUMBS] user actions in memory (add track, open
 *   editor, play/stop, record, export, AI calls, project open/new, …).
 * - [install] sets a default uncaught-exception handler that writes the stack trace, device and
 *   app info and the breadcrumbs to `filesDir/crash/last_crash.txt`, then hands the crash to the
 *   previous handler (so Android still shows / kills the process as usual).
 * - On the next launch MainActivity shows the report ([readPendingCrash]) with Copy / Share /
 *   Dismiss; Dismiss calls [clearPendingCrash].
 * - [coroutineHandler] is installed on the UI coroutine scopes: a failed background job is
 *   logged (and noted as a breadcrumb) instead of crashing the app.
 *
 * Nothing leaves the device unless the user taps Share.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val MAX_BREADCRUMBS = 50
    private const val CRASH_DIR = "crash"
    private const val CRASH_FILE = "last_crash.txt"

    private val ring = ArrayDeque<String>(MAX_BREADCRUMBS)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var filesDir: File? = null

    @Volatile
    private var installed = false

    /** Call once from [SanwolfApplication.onCreate]. */
    fun install(context: Context) {
        filesDir = context.applicationContext.filesDir
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                writeReport(thread, error, fatal = true)
            } catch (t: Throwable) {
                Log.e(TAG, "Couldn't write crash report", t)
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
        breadcrumb("app start (v${BuildConfig.VERSION_NAME} build ${BuildConfig.VERSION_CODE})")
    }

    /** Records one user action / event. Cheap and thread-safe; safe from any thread. */
    fun breadcrumb(message: String) {
        val line = synchronized(ring) {
            val stamp = timeFormat.format(Date())
            val l = "$stamp [${Thread.currentThread().name}] ${message.take(300)}"
            if (ring.size >= MAX_BREADCRUMBS) ring.removeFirst()
            ring.addLast(l)
            l
        }
        Log.d(TAG, line)
    }

    private fun breadcrumbSnapshot(): List<String> = synchronized(ring) { ring.toList() }

    /**
     * Logs coroutine failures instead of crashing. Cancellation is normal and ignored. Put on
     * UI scopes with `rememberCoroutineScope { CrashReporter.coroutineHandler }`.
     */
    val coroutineHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
        if (error is CancellationException) return@CoroutineExceptionHandler
        Log.e(TAG, "Background task failed (app kept running)", error)
        breadcrumb("background task failed: ${error.javaClass.simpleName}: ${error.message}")
    }

    fun crashFile(context: Context): File = File(File(context.filesDir, CRASH_DIR), CRASH_FILE)

    /** The report from the last crash, or null. Blocking file read: call off the main thread. */
    fun readPendingCrash(context: Context): String? = runCatching {
        val f = crashFile(context)
        if (f.exists() && f.length() > 0) f.readText() else null
    }.getOrNull()

    fun clearPendingCrash(context: Context) {
        runCatching { crashFile(context).delete() }
    }

    private fun writeReport(thread: Thread, error: Throwable, fatal: Boolean) {
        val dir = File(filesDir ?: return, CRASH_DIR)
        if (!dir.exists()) dir.mkdirs()
        val sw = StringWriter()
        PrintWriter(sw).use { error.printStackTrace(it) }
        val rt = Runtime.getRuntime()
        val report = buildString {
            appendLine("SANWOLF DAW crash report")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine("Fatal: $fatal")
            appendLine("Thread: ${thread.name}")
            appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Memory: used ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} MB / max ${rt.maxMemory() / 1_048_576} MB")
            appendLine()
            appendLine("--- Stack trace ---")
            appendLine(sw.toString().take(48_000))
            appendLine("--- Last actions (oldest first) ---")
            breadcrumbSnapshot().forEach { appendLine(it) }
        }
        File(dir, CRASH_FILE).writeText(report)
    }
}
