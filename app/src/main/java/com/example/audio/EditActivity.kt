package com.example.audio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.theme.SanwolfBlack
import com.example.ui.theme.SanwolfGold

class EditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(color = SanwolfBlack) {
                Text(text = "SANWOLF Audio Editor", color = SanwolfGold)
            }
        }
    }
}
