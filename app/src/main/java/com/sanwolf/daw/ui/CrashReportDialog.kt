package com.sanwolf.daw.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanwolf.daw.CrashReporter
import com.sanwolf.daw.ui.theme.SanwolfBlack
import com.sanwolf.daw.ui.theme.SanwolfGold
import com.sanwolf.daw.ui.theme.SanwolfPanel
import com.sanwolf.daw.ui.theme.SanwolfTextPrimary
import com.sanwolf.daw.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shown on launch when the previous session crashed (CrashReporter wrote last_crash.txt):
 * Copy / Share the report as text, or Dismiss (deletes it).
 */
@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) { CrashReporter.readPendingCrash(context.applicationContext) }
    }
    val text = report ?: return

    val dismiss: () -> Unit = {
        CrashReporter.clearPendingCrash(context.applicationContext)
        report = null
    }

    AlertDialog(
        onDismissRequest = { /* explicit choice only: keep the report until Dismiss */ },
        containerColor = SanwolfBlack,
        title = {
            Text("SANWOLF crashed last time", color = SanwolfGold, fontWeight = FontWeight.Black, fontSize = 16.sp)
        },
        text = {
            Column {
                Text(
                    "Sorry about that. Copy or share this report so it can be fixed. It stays on your phone unless you share it.",
                    color = SanwolfTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SanwolfPanel)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(text.take(12_000), color = SanwolfTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, softWrap = false)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { shareCrashReport(context, text) }) {
                Text("SHARE", color = SanwolfGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = { copyCrashReport(context, text) }) {
                    Text("COPY", color = SanwolfGold, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = dismiss) {
                    Text("DISMISS", color = SanwolfTextSecondary)
                }
            }
        }
    )
}

private fun copyCrashReport(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SANWOLF crash report", text.take(100_000)))
        Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Couldn't copy: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareCrashReport(context: Context, text: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SANWOLF DAW crash report")
            // Binder transactions are limited (~1 MB); the report is far smaller, but cap it anyway
            putExtra(Intent.EXTRA_TEXT, text.take(100_000))
        }
        val chooser = Intent.createChooser(send, "Share crash report").apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, "Couldn't share: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}
