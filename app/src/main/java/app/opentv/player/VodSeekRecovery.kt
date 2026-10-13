package app.opentv.player

/** Recovery from an interruption must honour the latest VOD seek, not an old bookmark. */
internal object VodSeekRecovery {
    fun retryStart(openingMs: Long, requestedMs: Long, isLive: Boolean): Long =
        if (isLive) openingMs else requestedMs.coerceAtLeast(0L)
}
