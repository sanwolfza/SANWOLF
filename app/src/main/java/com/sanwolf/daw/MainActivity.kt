package com.sanwolf.daw

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sanwolf.daw.ui.CrashReportPrompt
import com.sanwolf.daw.ui.SanwolfDawApp
import com.sanwolf.daw.ui.SanwolfSplashGate
import com.sanwolf.daw.ui.theme.LocalUiSizeState
import com.sanwolf.daw.ui.theme.MyApplicationTheme
import com.sanwolf.daw.ui.theme.ProvideUiScale
import com.sanwolf.daw.ui.theme.UiSize
import com.sanwolf.daw.ui.theme.UiSizeState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var lastDownTime = 0L
    private var lastKeyCode = -1

    /**
     * UI scale applied through the configuration override in [attachBaseContext] (0 = not
     * applied; Compose then scales the density itself in ProvideUiScale).
     */
    private var configScale = 0f

    /**
     * Applies the UI size to the whole activity: densityDpi scaled (screen dp sizes adjusted to
     * match) and system font scale capped at 1.0. Every window this activity opens (dialogs,
     * menus, popups, sheets) inherits it, unlike a Compose-only density, which each new window
     * resets. Changing the size in Settings recreates the activity.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        try {
            val sys = newBase.resources.configuration
            val scale = UiSize.load(newBase).scale
            val override = Configuration() // all fields "undefined": only what is set below overrides
            if (sys.densityDpi > 0 && scale != 1f) {
                override.densityDpi = (sys.densityDpi * scale).roundToInt().coerceAtLeast(60)
                if (sys.screenWidthDp > 0) override.screenWidthDp = (sys.screenWidthDp / scale).roundToInt()
                if (sys.screenHeightDp > 0) override.screenHeightDp = (sys.screenHeightDp / scale).roundToInt()
                if (sys.smallestScreenWidthDp > 0) override.smallestScreenWidthDp = (sys.smallestScreenWidthDp / scale).roundToInt()
            }
            override.fontScale = sys.fontScale.coerceAtMost(1f).let { if (it > 0f) it else 1f }
            applyOverrideConfiguration(override)
            configScale = scale
        } catch (e: Exception) {
            // Fall back to the Compose-level density scale
            android.util.Log.w("MainActivity", "UI scale override not applied", e)
            configScale = 0f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()

        // Draw into the notch / punch-hole area on the short edges (landscape left/right)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // A DAW should not dim or lock while the user is working in it
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        applyImmersiveMode()

        val appContext = applicationContext
        setContent {
            // Global UI size (Settings > Display > UI size), persisted in SharedPreferences.
            val uiSizeState = remember {
                UiSizeState(UiSize.load(appContext)) {
                    UiSize.save(appContext, it)
                    // The configuration override is read in attachBaseContext: rebuild the activity
                    // (the DAW session lives in the ViewModel, so nothing is lost)
                    if (configScale > 0f) recreate()
                }
            }
            CompositionLocalProvider(LocalUiSizeState provides uiSizeState) {
                // With the configuration override in place the density is already scaled: 1f here
                // (ProvideUiScale still caps the font scale). Otherwise scale in Compose.
                ProvideUiScale(scale = if (configScale > 0f) 1f else uiSizeState.size.scale) {
                    MyApplicationTheme {
                        // Branded loading screen on cold start, then the DAW (ui/SplashScreens.kt)
                        SanwolfSplashGate { SanwolfDawApp() }
                        // Report from the previous session's crash, if any (Copy / Share / Dismiss)
                        CrashReportPrompt()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Drop decoded samples the open project doesn't use when the system is short on memory
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            com.sanwolf.daw.audio.PcmSampleCache.trim()
            com.sanwolf.daw.audio.MultisampleLibrary.trim()
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs, the permission prompt or the notification shade can bring the bars back
        if (hasFocus) applyImmersiveMode()
    }

    /** Full screen: hide status + navigation bars; a swipe from the edge shows them briefly. */
    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Filter out rapid duplicate key-down events from WebRTC / emulator bridges
        val now = SystemClock.uptimeMillis()
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == lastKeyCode && event.repeatCount == 0 && (now - lastDownTime < 45L)) {
                // Drop rapid duplicate key event
                return true
            }
            lastDownTime = now
            lastKeyCode = event.keyCode
        }
        return super.dispatchKeyEvent(event)
    }
}
