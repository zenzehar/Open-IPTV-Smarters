/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import app.opentv.data.model.Profile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfileRemovalPolicyTest {
    private val profiles = listOf(Profile(id = 1L, name = "Me"),
        Profile(id = 4L, name = "Kids"), Profile(id = 9L, name = "Guest"))

    @Test fun `default profile can be removed when others exist`() {
        assertThat(replacementProfileId(profiles, 1L)).isEqualTo(4L)
    }

    @Test fun `active non-default profile can fall back to first surviving`() {
        assertThat(replacementProfileId(profiles, 4L)).isEqualTo(1L)
    }

    @Test fun `last remaining profile cannot be removed`() {
        assertThat(replacementProfileId(listOf(Profile(id = 9L, name = "Guest")), 9L)).isNull()
    }

    @Test fun `unknown profile cannot be removed`() {
        assertThat(replacementProfileId(profiles, 999L)).isNull()
    }
}
