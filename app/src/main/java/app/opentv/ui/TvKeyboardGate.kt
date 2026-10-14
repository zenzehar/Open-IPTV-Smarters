package app.opentv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Do not put BasicTextField in the TV D-pad focus tree until editing is explicitly requested.
 * Unlike repeated IME.hide() hacks, this cannot flash open a keyboard on ordinary focus movement.
 * The native field is retained on phones, and all input remains in caller-owned state.
 */
@Composable
fun TvKeyboardGate(
    isTelevision: Boolean,
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    field: @Composable (Modifier) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val inputFocus = remember { FocusRequester() }
    val gateFocus = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var hadInputFocus by remember { mutableStateOf(false) }
    var gateFocused by remember { mutableStateOf(false) }
    var restoreGate by remember { mutableStateOf(false) }

    BackHandler(enabled = isTelevision && editing) {
        keyboard?.hide()
        editing = false
        restoreGate = true
    }
    LaunchedEffect(isTelevision, editing, restoreGate) {
        if (!isTelevision) return@LaunchedEffect
        if (editing) {
            // Wait for the editable field to enter composition before requesting native IME.
            delay(65L)
            runCatching { inputFocus.requestFocus() }
            keyboard?.show()
        } else if (restoreGate) {
            delay(45L)
            runCatching { gateFocus.requestFocus() }
            restoreGate = false
        }
    }

    if (!isTelevision || editing) {
        val fieldModifier = modifier.then(if (isTelevision) {
            Modifier.focusRequester(inputFocus).onFocusChanged { focus ->
                if (focus.isFocused) hadInputFocus = true
                else if (hadInputFocus) {
                    hadInputFocus = false
                    editing = false
                    restoreGate = true
                }
            }
        } else Modifier)
        field(fieldModifier)
    } else {
        Box(
            modifier = modifier
                .defaultMinSize(minHeight = 56.dp)
                .focusRequester(gateFocus)
                .onFocusChanged { gateFocused = it.isFocused }
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF0CDDB))
                .border(
                    if (gateFocused) 3.dp else 1.dp,
                    if (gateFocused) Color(0xFF00BCD4) else Color(0xFF715166),
                    RoundedCornerShape(8.dp),
                )
                .clickable { editing = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                when {
                    value.isEmpty() -> placeholder
                    masked -> "•".repeat(value.length.coerceAtMost(24))
                    else -> value
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty()) Color(0xFF6D5264) else Color(0xFF271B2A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
