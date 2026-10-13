package app.opentv.player

/**
 * Shared movie/episode timeline used by Media3 and LibVLC. In particular, a decoder's
 * duration may be TIME_UNSET while the stream is already playing. Treating that as
 * 0..1 millisecond made the seek thumb appear completely stuck for movie containers.
 */
internal object VodSeekTimeline {
    private const val UNKNOWN_WINDOW_MILLIS = 30L * 60L * 1_000L

    fun catalogueMillis(seconds: Int?): Long =
        if (seconds != null && seconds > 0) seconds.toLong() * 1_000L else 0L

    fun durationMillis(decoder: Long, catalogue: Long, savedHistory: Long): Long =
        when {
            decoder > 0L -> decoder
            catalogue > 0L -> catalogue
            savedHistory > 0L -> savedHistory
            else -> 0L
        }

    /**
     * If no reliable duration exists, create a *sliding, explicitly unknown-duration*
     * 30-minute seek window. This is not a claimed movie length; it only makes time-based
     * seeking usable until catalogue or decoder duration arrives.
     */
    fun seekBarMaxMillis(knownDuration: Long, current: Long, pendingTarget: Long): Long =
        if (knownDuration > 0L) knownDuration
        else (maxOf(current.coerceAtLeast(0L), pendingTarget.coerceAtLeast(0L)) +
            UNKNOWN_WINDOW_MILLIS).coerceAtLeast(UNKNOWN_WINDOW_MILLIS)

    fun clampTarget(requested: Long, knownDuration: Long): Long =
        requested.coerceAtLeast(0L).let {
            if (knownDuration > 0L) it.coerceAtMost(knownDuration) else it
        }

    fun reachedTarget(actual: Long, requested: Long): Boolean =
        kotlin.math.abs(actual - requested) <= 4_000L
}
