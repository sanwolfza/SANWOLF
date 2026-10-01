package com.example

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.SanwolfDawApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var lastDownTime = 0L
    private var lastKeyCode = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SanwolfDawApp()
            }
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
