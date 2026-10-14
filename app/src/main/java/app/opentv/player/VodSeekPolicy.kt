/*
 * This file is part of OpenTV (GPL-3.0-or-later).
 * Shared, decoder-independent VOD seeking rules. Neither decoder must trust duration
 * metadata after real decoded playback has moved beyond that claimed end.
 */
package app.opentv.player

internal object VodSeekPolicy {
    const val SHORT_STEP_MS = 10_000L
    const val HELD_STEP_MS = 120_000L
    private const val DURATION_TOLERANCE_MS = 2_000L
    private const val MOVING_WINDOW_MS = 15L * 60_000L
    private const val TARGET_TOLERANCE_MS = 3_000L

    fun contradictsDuration(durationMs: Long, decodedPositionMs: Long): Boolean =
        durationMs > 0L && decodedPositionMs > durationMs &&
            decodedPositionMs - durationMs > DURATION_TOLERANCE_MS

    /** 0 indicates an unknown/untrustworthy upper bound; never clamp to zero. */
    fun target(requestMs: Long, durationMs: Long, metadataUntrusted: Boolean): Long {
        val safe = requestMs.coerceAtLeast(0L)
        return if (durationMs > 0L && !metadataUntrusted) safe.coerceAtMost(durationMs) else safe
    }

    fun relative(positionMs: Long, deltaMs: Long, durationMs: Long, metadataUntrusted: Boolean): Long =
        target(positionMs.coerceAtLeast(0L) + deltaMs, durationMs, metadataUntrusted)

    fun displayMaximum(durationMs: Long, positionMs: Long, metadataUntrusted: Boolean): Long =
        if (durationMs > 0L && !metadataUntrusted) maxOf(durationMs, positionMs.coerceAtLeast(0L))
        else maxOf(MOVING_WINDOW_MS, positionMs.coerceAtLeast(0L) + MOVING_WINDOW_MS)

    fun acknowledged(decodedPositionMs: Long, targetMs: Long): Boolean =
        kotlin.math.abs(decodedPositionMs - targetMs) <= TARGET_TOLERANCE_MS

    /** Persist unknown duration rather than falsely declaring playback complete. */
    fun safeDuration(reportedMs: Long, positionMs: Long, metadataUntrusted: Boolean): Long =
        if (metadataUntrusted || contradictsDuration(reportedMs, positionMs)) 0L
        else reportedMs.coerceAtLeast(0L)

    fun remoteStep(repeatCount: Int): Long =
        if (repeatCount > 0) HELD_STEP_MS else SHORT_STEP_MS
}
