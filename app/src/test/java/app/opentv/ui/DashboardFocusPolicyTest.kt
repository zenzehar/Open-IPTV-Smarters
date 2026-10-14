/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DashboardFocusPolicyTest {
    @Test fun `live is the first focus target when available`() {
        assertThat(firstDashboardFocusTarget(true, true, true))
            .isEqualTo(DashboardFocusTarget.LIVE)
    }

    @Test fun `movies are first if live is disabled`() {
        assertThat(firstDashboardFocusTarget(false, true, true))
            .isEqualTo(DashboardFocusTarget.MOVIES)
    }

    @Test fun `series are first if live and movies are disabled`() {
        assertThat(firstDashboardFocusTarget(false, false, true))
            .isEqualTo(DashboardFocusTarget.SERIES)
    }

    @Test fun `recordings is the fallback with no live or vod categories`() {
        assertThat(firstDashboardFocusTarget(false, false, false))
            .isEqualTo(DashboardFocusTarget.RECORDINGS)
    }
}
