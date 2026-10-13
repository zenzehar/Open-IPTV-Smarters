/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

/** Pick a real Home action that exists in the current provider configuration. */
internal enum class DashboardFocusTarget { LIVE, MOVIES, SERIES, RECORDINGS }

internal fun firstDashboardFocusTarget(
    liveEnabled: Boolean,
    moviesEnabled: Boolean,
    seriesEnabled: Boolean,
): DashboardFocusTarget = when {
    liveEnabled -> DashboardFocusTarget.LIVE
    moviesEnabled -> DashboardFocusTarget.MOVIES
    seriesEnabled -> DashboardFocusTarget.SERIES
    else -> DashboardFocusTarget.RECORDINGS
}
