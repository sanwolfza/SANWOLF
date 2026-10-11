package com.sanwolf.daw.ui

import android.content.Context
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
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
import org.json.JSONObject

/*
 * Settings › About legal screens: Your music & sounds, Open-source licences, Terms of use and
 * Privacy policy. All text lives in assets/legal/ so it can be updated without code changes; the
 * Terms and Privacy assets are copies of docs/legal/TERMS.md and PRIVACY.md (keep them in sync).
 */

/** Asset paths of the legal documents. */
object LegalAssets {
    const val YOUR_MUSIC = "legal/your_music_and_sounds.md"
    const val TERMS = "legal/terms.md"
    const val PRIVACY = "legal/privacy.md"
    const val OSS_LIST = "legal/oss_licenses.json"
}

private fun readAssetText(context: Context, path: String): String? =
    runCatching { context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } }
        .onFailure { Log.e("LegalDialogs", "Couldn't read asset $path", it) }
        .getOrNull()

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Log.e("LegalDialogs", "Couldn't open $url", it)
        Toast.makeText(context, "No browser available to open $url", Toast.LENGTH_SHORT).show()
    }
}

/** Shared frame: a large scaled dialog with a gold title and a close button. */
@Composable
private fun LegalDialogFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
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
                    title.uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = SanwolfGold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close $title", tint = SanwolfTextSecondary)
                }
            }
            content()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Markdown-ish document viewer
// ---------------------------------------------------------------------------------------------

/** One rendered block of a legal document. */
private sealed class DocBlock {
    data class Heading(val level: Int, val text: String) : DocBlock()
    data class Paragraph(val text: String) : DocBlock()
    data class Bullet(val text: String) : DocBlock()
    data class Plain(val text: String) : DocBlock()
}

/**
 * A deliberately small Markdown subset: `#`/`##`/`###` headings, `-`/`*` bullets, paragraphs
 * separated by blank lines, `---` rules (dropped), `<!-- -->` comments (hidden), and inline
 * `**bold**`, `` `code` `` and `[text](url)` (handled by [inlineMarkdown]).
 */
private fun parseMarkdown(src: String): List<DocBlock> {
    val noComments = src.replace(Regex("<!--[\\s\\S]*?-->"), "")
    val out = mutableListOf<DocBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out.add(DocBlock.Paragraph(para.toString().trim()))
        para.setLength(0)
    }
    for (raw in noComments.lines()) {
        val line = raw.trimEnd()
        val t = line.trimStart()
        when {
            t.isEmpty() -> flush()
            t == "---" || t == "***" -> flush()
            t.startsWith("### ") -> { flush(); out.add(DocBlock.Heading(3, t.removePrefix("### ").trim())) }
            t.startsWith("## ") -> { flush(); out.add(DocBlock.Heading(2, t.removePrefix("## ").trim())) }
            t.startsWith("# ") -> { flush(); out.add(DocBlock.Heading(1, t.removePrefix("# ").trim())) }
            t.startsWith("- ") || t.startsWith("* ") -> { flush(); out.add(DocBlock.Bullet(t.substring(2).trim())) }
            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(t)
            }
        }
    }
    flush()
    return out
}

/** Plain text (licence files): paragraphs split on blank lines, line breaks kept. */
private fun parsePlain(src: String): List<DocBlock> =
    src.split(Regex("\\n\\s*\\n")).map { it.trimEnd() }.filter { it.isNotBlank() }.map { DocBlock.Plain(it) }

private val INLINE_TOKEN = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)""")

/** Renders **bold**, `code` and [text](url) (shown as "text (url)", the url underlined). */
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE_TOKEN.findAll(text)) {
        append(text.substring(last, m.range.first))
        val bold = m.groups[1]?.value
        val code = m.groups[2]?.value
        val linkText = m.groups[3]?.value
        val linkUrl = m.groups[4]?.value
        when {
            bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SanwolfTextPrimary)) { append(bold) }
            code != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            linkText != null && linkUrl != null -> {
                append(linkText)
                append(" (")
                withStyle(SpanStyle(color = SanwolfCyan, textDecoration = TextDecoration.Underline)) { append(linkUrl) }
                append(")")
            }
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

/** First http(s) url in a block, so tapping a paragraph or bullet can open its link. */
private fun firstUrl(text: String): String? = Regex("""https?://[^\s)]+""").find(text)?.value

/**
 * Scrollable viewer for a legal document in assets. [plainText] = true shows the file as
 * monospace paragraphs (licence texts); otherwise it is rendered as simple Markdown.
 */
@Composable
fun LegalDocumentDialog(title: String, assetPath: String, onDismiss: () -> Unit, plainText: Boolean = false) {
    val context = LocalContext.current
    var blocks by remember(assetPath) { mutableStateOf<List<DocBlock>?>(null) }
    var failed by remember(assetPath) { mutableStateOf(false) }
    LaunchedEffect(assetPath) {
        val text = withContext(Dispatchers.IO) { readAssetText(context.applicationContext, assetPath) }
        if (text == null) failed = true
        else blocks = if (plainText) parsePlain(text) else parseMarkdown(text)
    }

    LegalDialogFrame(title = title, onDismiss = onDismiss) {
        val loaded = blocks
        when {
            failed -> Text("This document could not be opened.", fontSize = 12.sp, color = SanwolfTextMuted)
            loaded == null -> Text("Loading…", fontSize = 12.sp, color = SanwolfTextMuted)
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(loaded, key = { i, _ -> i }) { _, block -> DocBlockView(block) { openExternalUrl(context, it) } }
            }
        }
    }
}

@Composable
private fun DocBlockView(block: DocBlock, onOpenUrl: (String) -> Unit) {
    when (block) {
        is DocBlock.Heading -> Text(
            inlineMarkdown(block.text),
            fontSize = when (block.level) { 1 -> 16.sp; 2 -> 14.sp; else -> 13.sp },
            fontWeight = if (block.level == 1) FontWeight.Black else FontWeight.Bold,
            color = if (block.level == 1) SanwolfGold else SanwolfTextPrimary,
            modifier = Modifier.padding(top = if (block.level == 1) 0.dp else 6.dp)
        )
        is DocBlock.Paragraph -> LinkableText(block.text, onOpenUrl) { text, mod ->
            Text(text, fontSize = 12.sp, color = SanwolfTextSecondary, lineHeight = 17.sp, modifier = mod)
        }
        is DocBlock.Bullet -> Row(modifier = Modifier.padding(start = 6.dp)) {
            Text("•  ", fontSize = 12.sp, color = SanwolfGold)
            LinkableText(block.text, onOpenUrl) { text, mod ->
                Text(text, fontSize = 12.sp, color = SanwolfTextSecondary, lineHeight = 17.sp, modifier = mod)
            }
        }
        is DocBlock.Plain -> Text(
            block.text,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = SanwolfTextSecondary,
            lineHeight = 14.sp
        )
    }
}

/** Wraps a block so a tap opens the first link in it (if any). */
@Composable
private fun LinkableText(
    raw: String,
    onOpenUrl: (String) -> Unit,
    render: @Composable (AnnotatedString, Modifier) -> Unit
) {
    val url = firstUrl(raw)
    val mod = if (url != null) Modifier.clickable { onOpenUrl(url) } else Modifier
    render(inlineMarkdown(raw), mod)
}

// ---------------------------------------------------------------------------------------------
// Open-source licences
// ---------------------------------------------------------------------------------------------

private data class OssLibrary(
    val name: String,
    val artifact: String,
    val version: String,
    val licence: String,
    val licenceUrl: String,
    val url: String,
    val notes: String?,
    val licenceTextId: String?
)

private data class OssLicenceText(val id: String, val name: String, val asset: String)

private data class OssData(val libraries: List<OssLibrary>, val texts: List<OssLicenceText>)

private fun parseOss(json: String): OssData {
    val root = JSONObject(json)
    val texts = mutableListOf<OssLicenceText>()
    root.optJSONArray("licenceTexts")?.let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            texts.add(OssLicenceText(o.optString("id"), o.optString("name"), o.optString("asset")))
        }
    }
    val libs = mutableListOf<OssLibrary>()
    root.optJSONArray("libraries")?.let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            libs.add(
                OssLibrary(
                    name = o.optString("name"),
                    artifact = o.optString("artifact"),
                    version = o.optString("version"),
                    licence = o.optString("licence"),
                    licenceUrl = o.optString("licenceUrl"),
                    url = o.optString("url"),
                    notes = o.optString("notes").takeIf { it.isNotBlank() },
                    licenceTextId = o.optString("licenceText").takeIf { it.isNotBlank() }
                )
            )
        }
    }
    return OssData(libs, texts)
}

/** Settings › About › Open-source licences: the libraries in the app and their licences. */
@Composable
fun OpenSourceLicencesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<OssData?>(null) }
    var failed by remember { mutableStateOf(false) }
    var openText by remember { mutableStateOf<OssLicenceText?>(null) }
    LaunchedEffect(Unit) {
        val parsed = withContext(Dispatchers.IO) {
            readAssetText(context.applicationContext, LegalAssets.OSS_LIST)?.let { json ->
                runCatching { parseOss(json) }.onFailure { Log.e("LegalDialogs", "Bad oss_licenses.json", it) }.getOrNull()
            }
        }
        if (parsed == null) failed = true else data = parsed
    }

    LegalDialogFrame(title = "Open-source licences", onDismiss = onDismiss) {
        Text(
            "SANWOLF DAW is built with these open-source and Google libraries. Tap a licence to read it in full.",
            fontSize = 11.sp,
            color = SanwolfTextSecondary
        )
        val loaded = data
        when {
            failed -> Text("The licence list could not be read.", fontSize = 12.sp, color = SanwolfTextMuted)
            loaded == null -> Text("Loading…", fontSize = 12.sp, color = SanwolfTextMuted)
            else -> {
                val textsById = loaded.texts.associateBy { it.id }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "hdr_texts") { OssSectionTitle("FULL LICENCE TEXTS") }
                    itemsIndexed(loaded.texts, key = { _, t -> "txt_${t.id}" }) { _, t ->
                        Text(
                            t.name,
                            fontSize = 12.sp,
                            color = SanwolfCyan,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SanwolfPanelElevated)
                                .clickable { openText = t }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                    item(key = "hdr_libs") { OssSectionTitle("LIBRARIES") }
                    itemsIndexed(loaded.libraries, key = { i, lib -> "lib_${i}_${lib.name}" }) { _, lib ->
                        val text = lib.licenceTextId?.let { textsById[it] }
                        OssLibraryCard(
                            lib = lib,
                            onOpenLicence = {
                                if (text != null) openText = text
                                else if (lib.licenceUrl.isNotBlank()) openExternalUrl(context, lib.licenceUrl)
                            },
                            onOpenUrl = { openExternalUrl(context, it) }
                        )
                    }
                }
            }
        }
    }

    openText?.let { t ->
        LegalDocumentDialog(title = t.name, assetPath = t.asset, onDismiss = { openText = null }, plainText = true)
    }
}

@Composable
private fun OssSectionTitle(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SanwolfTextMuted, letterSpacing = 0.8.sp)
}

@Composable
private fun OssLibraryCard(lib: OssLibrary, onOpenLicence: () -> Unit, onOpenUrl: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SanwolfPanelElevated)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(lib.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SanwolfTextPrimary)
        Text("${lib.artifact} · ${lib.version}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SanwolfTextMuted)
        Text(
            "Licence: ${lib.licence}",
            fontSize = 11.sp,
            color = SanwolfCyan,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { onOpenLicence() }
        )
        lib.notes?.let { Text(it, fontSize = 11.sp, color = SanwolfTextSecondary) }
        if (lib.url.isNotBlank()) {
            Text(
                lib.url,
                fontSize = 11.sp,
                color = SanwolfCyan,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onOpenUrl(lib.url) }
            )
        }
    }
}
