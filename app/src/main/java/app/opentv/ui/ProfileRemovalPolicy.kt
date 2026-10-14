/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import app.opentv.data.model.Profile

/** Return the surviving profile to activate, or null when deletion is not permitted. */
internal fun replacementProfileId(profiles: List<Profile>, toRemove: Long): Long? {
    if (profiles.size <= 1 || profiles.none { it.id == toRemove }) return null
    return profiles.firstOrNull { it.id != toRemove }?.id
}
