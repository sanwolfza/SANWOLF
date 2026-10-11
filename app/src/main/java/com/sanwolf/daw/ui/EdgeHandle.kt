package com.sanwolf.daw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sanwolf.daw.ui.theme.SanwolfGold
import com.sanwolf.daw.ui.theme.SanwolfPanelBorder
import com.sanwolf.daw.ui.theme.SanwolfPanelElevated

/**
 * Slim tab (18 dp wide) that sits on top of the workspace at a screen edge and re-opens a
 * collapsed side panel. It is drawn as an overlay, so a collapsed panel reserves no layout width.
 */
@Composable
fun EdgeHandle(
    onStartEdge: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = if (onStartEdge) "edge_handle_start" else "edge_handle_end"
) {
    val shape = if (onStartEdge) {
        RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
    }
    Box(
        modifier = modifier
            .size(width = 18.dp, height = 56.dp)
            .clip(shape)
            .background(SanwolfPanelElevated.copy(alpha = 0.88f))
            .border(1.dp, SanwolfPanelBorder, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (onStartEdge) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = SanwolfGold,
            modifier = Modifier.size(16.dp)
        )
    }
}
