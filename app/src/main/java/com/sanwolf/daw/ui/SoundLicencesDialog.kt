package com.sanwolf.daw.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.sanwolf.daw.audio.FactoryLibrary
import com.sanwolf.daw.audio.SoundLicenceGroup
import com.sanwolf.daw.ui.theme.SanwolfCyan
import com.sanwolf.daw.ui.theme.SanwolfGold
import com.sanwolf.daw.ui.theme.SanwolfPanel
import com.sanwolf.daw.ui.theme.SanwolfPanelBorder
import com.sanwolf.daw.ui.theme.SanwolfPanelElevated
import com.sanwolf.daw.ui.theme.SanwolfTextMuted
import com.sanwolf.daw.ui.theme.SanwolfTextPrimary
import com.sanwolf.daw.ui.theme.SanwolfTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A third-party sound library the app uses (or is prepared to bundle). */
data class ThirdPartySoundSource(
    /** Matched (case-insensitive substring) against FactorySound.source to count bundled sounds. */
    val key: String,
    val name: String,
    val author: String,
    val licence: String,
    val url: String,
    val notes: String
)

/**
 * Third-party sample libraries. Bundled sounds whose source contains [ThirdPartySoundSource.key]
 * are counted automatically: the VCSL multisample instruments (assets/instruments/vcsl) arrive
 * through FactoryLibrary.licenceGroups as their own group.
 */
val THIRD_PARTY_SOUND_SOURCES = listOf(
    ThirdPartySoundSource(
        key = "VCSL",
        name = "Versilian Community Sample Library (VCSL)",
        author = "Versilian Studios / Samuel Gossner",
        licence = "CC0 1.0 Universal (public domain dedication)",
        url = "https://github.com/sgossner/VCSL",
        notes = "Sampled instruments: Steinway Grand Piano, FM Electric Piano, Marimba, Vibraphone, " +
            "Kalimba, Balafon, Concert Harp. The pipe organ is excluded from SANWOLF (separate licence). " +
            "CC0 needs no attribution; it is credited here as a courtesy."
    )
)

/** Settings > About > Sound licences: every source of the bundled sounds and its licence. */
@Composable
fun SoundLicencesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<SoundLicenceGroup>?>(null) }
    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.IO) { FactoryLibrary.licenceGroups(context.applicationContext) }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Log.e("SoundLicences", "Couldn't open $url", it)
            Toast.makeText(context, "No browser available to open $url", Toast.LENGTH_SHORT).show()
        }
    }

    ScaledDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(10.dp))
                .background(SanwolfPanel)
                .border(1.dp, SanwolfPanelBorder, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SOUND LICENCES",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfGold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close sound licences", tint = SanwolfTextSecondary)
                }
            }
            Text(
                "Where the bundled factory sounds come from and how you may use them. Sounds you export in your own songs are yours.",
                fontSize = 11.sp,
                color = SanwolfTextSecondary
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "hdr_bundled") { SectionTitle("BUNDLED SOUNDS") }
                val loaded = groups
                if (loaded == null) {
                    item(key = "loading") { Text("Loading…", fontSize = 12.sp, color = SanwolfTextMuted) }
                } else if (loaded.isEmpty()) {
                    item(key = "none") { Text("The factory library could not be read.", fontSize = 12.sp, color = SanwolfTextMuted) }
                } else {
                    items(loaded, key = { "${it.source}|${it.licence}|${it.author}|${it.url}" }) { g ->
                        LicenceCard(
                            title = g.source,
                            lines = listOf(
                                "Licence: ${g.licence}",
                                "Author: ${g.author}",
                                "${g.soundCount} sounds · ${g.genres.joinToString(", ")}"
                            ),
                            url = g.url,
                            onOpenUrl = { u -> openUrl(u) }
                        )
                    }
                }

                item(key = "hdr_third") { SectionTitle("THIRD-PARTY LIBRARIES") }
                items(THIRD_PARTY_SOUND_SOURCES, key = { "tp_${it.key}" }) { src ->
                    val bundled = loaded?.filter { it.source.contains(src.key, ignoreCase = true) }?.sumOf { it.soundCount } ?: 0
                    LicenceCard(
                        title = src.name,
                        lines = listOf(
                            "Licence: ${src.licence}",
                            "Author: ${src.author}",
                            if (bundled > 0) "$bundled sounds bundled" else "Not bundled in this version yet",
                            src.notes
                        ),
                        url = src.url,
                        onOpenUrl = { u -> openUrl(u) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfTextMuted, letterSpacing = 0.8.sp)
}

@Composable
private fun LicenceCard(title: String, lines: List<String>, url: String?, onOpenUrl: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SanwolfTextPrimary)
        lines.forEach { Text(it, fontSize = 11.sp, color = SanwolfTextSecondary) }
        if (!url.isNullOrBlank()) {
            Text(
                url,
                fontSize = 11.sp,
                color = SanwolfCyan,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onOpenUrl(url) }
            )
        }
    }
}
