package com.example.drum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfOrange

class DrumActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(color = SanwolfBlack) {
                Text(text = "SANWOLF Drum Machine", color = SanwolfOrange)
            }
        }
    }
}
