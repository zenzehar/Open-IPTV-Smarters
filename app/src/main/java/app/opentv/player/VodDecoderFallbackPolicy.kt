package app.opentv.player

/**
 * A slow stream is not a codec failure. We must never change engines just
 * because a provider has been buffering for 25 seconds.
 */
internal object VodDecoderFallbackPolicy {
    fun switchOnBufferingTimeout(): Boolean = false
}
