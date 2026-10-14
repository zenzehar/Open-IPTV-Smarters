package app.opentv.ui.vod

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Single lightweight styling setting, avoiding separate collectors for every poster. */
internal val LocalArtworkWidth = compositionLocalOf { 140.dp }
