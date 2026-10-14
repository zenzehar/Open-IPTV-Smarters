/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/**
 * Focus != text editing on a television. Do not summon the Android IME merely because
 * D-pad movement highlighted an input. Some TV keyboard implementations ignore
 * KeyboardOptions(showKeyboardOnFocus=false); also explicitly suppress the native IME on focus.
 * Pressing OK/Enter or tapping the field intentionally unlocks keyboard display.
 */
@Composable
fun Modifier.explicitImeActivation(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    var hasFocus by remember { mutableStateOf(false) }
    var activated by remember { mutableStateOf(false) }

    fun suppressIme() {
        keyboard?.hide()
        val manager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        manager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    LaunchedEffect(hasFocus, activated) {
        if (hasFocus && !activated) {
            suppressIme()
            delay(80L)
            suppressIme()
            delay(160L)
            suppressIme()
        }
    }

    return this
        .onFocusChanged { state ->
            if (state.isFocused && !hasFocus) {
                activated = false
                suppressIme()
            }
            if (!state.isFocused) activated = false
            hasFocus = state.isFocused
        }
        .pointerInput(keyboard) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (waitForUpOrCancellation() != null) {
                    activated = true
                    keyboard?.show()
                }
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionCenter ||
                    event.key == Key.Enter || event.key == Key.NumPadEnter)
            ) {
                activated = true
                keyboard?.show()
                true
            } else false
        }
}
