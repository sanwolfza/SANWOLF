package com.sanwolf.daw.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sanwolf.daw.ui.theme.ProvideUiScale

/**
 * Drop-in replacement for [Dialog] that keeps the app's UI size (Compact / Normal / Large).
 * A dialog is a separate window whose LocalDensity is reset to the platform value, so the
 * scale has to be applied again inside it.
 */
@Composable
fun ScaledDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        ProvideUiScale(content = content)
    }
}
