package com.sanwolf.daw.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Global UI size. The whole app is rendered with a scaled [Density], so every dp and sp
 * shrinks (or grows) together; [Dimens] holds the base sizes of the main chrome.
 *
 * Compact is the default: on a phone in landscape the old 1.0 scale looked "zoomed in".
 */
enum class UiSize(val label: String, val scale: Float) {
    COMPACT("Compact", 0.85f),
    NORMAL("Normal", 1.0f),
    LARGE("Large", 1.15f);

    companion object {
        private const val PREFS = "sanwolf_daw_prefs"
        private const val KEY = "ui_size"

        fun load(context: Context): UiSize {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return values().firstOrNull { it.name == name } ?: COMPACT
        }

        fun save(context: Context, size: UiSize) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, size.name).apply()
        }
    }
}

/** Holds the current [UiSize]; Settings changes it, the root re-renders with the new density. */
@Stable
class UiSizeState(initial: UiSize, private val onChanged: (UiSize) -> Unit = {}) {
    var size: UiSize by mutableStateOf(initial)
        private set

    fun update(newSize: UiSize) {
        if (newSize == size) return
        size = newSize
        onChanged(newSize)
    }
}

val LocalUiSizeState = staticCompositionLocalOf { UiSizeState(UiSize.COMPACT) }

/** Scale factor that [ProvideUiScale] applies. Dialog windows re-apply it (they reset LocalDensity). */
val LocalUiScale = compositionLocalOf { 1f }

/**
 * Applies the UI scale to [content]. Call once at the root, and again inside every Dialog /
 * Popup window, because a new window gets a fresh LocalDensity from its view.
 */
@Composable
fun ProvideUiScale(scale: Float = LocalUiScale.current, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    // Always scale from the platform density (densityDpi / 160 == displayMetrics.density),
    // so calling this again in the same window never compounds the scale.
    val platformDensity = LocalConfiguration.current.densityDpi / 160f
    val baseDensity = if (platformDensity > 0f) platformDensity else base.density
    CompositionLocalProvider(
        LocalUiScale provides scale,
        LocalDensity provides Density(density = baseDensity * scale, fontScale = base.fontScale)
    ) {
        content()
    }
}

/** Base dimensions for the app chrome (before the UI scale is applied). */
object Dimens {
    // Toolbars / headers
    val toolbarHeight: Dp = 44.dp
    /** Fixed piano transport strip (always visible; collapsing the piano hides only the keys). */
    val pianoHeaderHeight: Dp = 40.dp
    val toolbarButton: Dp = 38.dp
    val toolbarIcon: Dp = 20.dp

    // Side rails and slide-in panels
    val railWidth: Dp = 50.dp
    val railButton: Dp = 42.dp
    val railIcon: Dp = 20.dp
    val sidePanelMin: Dp = 220.dp
    val sidePanelMax: Dp = 300.dp
    const val sidePanelFraction: Float = 0.34f

    // Arranger
    val trackRowHeight: Dp = 54.dp
    val trackHeaderWidth: Dp = 150.dp
    val rulerHeight: Dp = 22.dp

    // Generic controls
    val buttonMin: Dp = 40.dp
    val iconButton: Dp = 40.dp
    val icon: Dp = 18.dp
    val smallIcon: Dp = 14.dp
    val padding: Dp = 8.dp
    val paddingSmall: Dp = 4.dp

    // Text
    val textTitle = 13.sp
    val textBody = 12.sp
    val textLabel = 11.sp
    val textSmall = 10.sp
}

/**
 * Width for a slide-in side panel: a fraction of the screen, clamped to
 * [Dimens.sidePanelMin]..[Dimens.sidePanelMax] (in the current, scaled dp).
 */
@Composable
fun sidePanelWidth(): Dp {
    val scale = LocalUiScale.current.coerceAtLeast(0.1f)
    val screenDp = LocalConfiguration.current.screenWidthDp / scale
    return (screenDp * Dimens.sidePanelFraction).dp.coerceIn(Dimens.sidePanelMin, Dimens.sidePanelMax)
}
