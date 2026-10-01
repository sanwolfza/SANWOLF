package com.example.mixer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfLime

class MixerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(color = SanwolfBlack) {
                Text(text = "SANWOLF Master Mixer Console", color = SanwolfLime)
            }
        }
    }
}
