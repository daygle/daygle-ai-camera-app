package com.daygle.aicamera.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

/**
 * Keeps a [TextFieldState] in step with a value/onValueChange pair.
 *
 * Compose's secure text fields are state-based, but these screens keep their text in a
 * ViewModel and load saved settings asynchronously, so changes have to travel both ways:
 * user input is forwarded to [onValueChange], and a value that changes elsewhere (a saved
 * credential arriving after first composition) is written back into the field.
 */
@Composable
fun rememberSyncedTextFieldState(
    value: String,
    onValueChange: (String) -> Unit,
): TextFieldState {
    val fieldState = rememberTextFieldState(value)
    val currentValue by rememberUpdatedState(value)

    LaunchedEffect(value) {
        if (value != fieldState.text.toString()) {
            fieldState.setTextAndPlaceCursorAtEnd(value)
        }
    }

    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }
            .collect { text -> if (text != currentValue) onValueChange(text) }
    }

    return fieldState
}
