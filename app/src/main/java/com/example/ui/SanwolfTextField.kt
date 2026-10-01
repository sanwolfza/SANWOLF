package com.example.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.ui.theme.SanwolfCyan
import com.example.ui.theme.SanwolfGold
import com.example.ui.theme.SanwolfPanelBorder
import com.example.ui.theme.SanwolfTextPrimary
import com.example.ui.theme.SanwolfTextSecondary

@Composable
fun SanwolfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = TextStyle(color = SanwolfTextPrimary),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusedBorderColor: Color = SanwolfGold
) {
    val deduplicator = remember { TextInputDeduplicator() }

    OutlinedTextField(
        value = value,
        onValueChange = { incoming ->
            val sanitized = deduplicator.processChange(value, incoming)
            onValueChange(sanitized)
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = focusedBorderColor,
            unfocusedBorderColor = SanwolfPanelBorder,
            focusedTextColor = SanwolfTextPrimary,
            unfocusedTextColor = SanwolfTextPrimary,
            cursorColor = SanwolfGold
        )
    )
}
