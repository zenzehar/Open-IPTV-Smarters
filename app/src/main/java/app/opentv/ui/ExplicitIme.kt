/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Open Android's selected IME only after OK/Enter or a deliberate touch/mouse click. */
@Composable
fun Modifier.explicitImeActivation(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    return this
        .onPointerEvent(PointerEventType.Release) { keyboard?.show() }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter)
            ) {
                keyboard?.show()
                true
            } else false
        }
}
