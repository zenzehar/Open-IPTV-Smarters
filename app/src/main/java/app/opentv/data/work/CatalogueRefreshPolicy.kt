package app.opentv.data.work

/** Shared catalogue interval rule used on launch, periodic work and the VOD UI. */
object CatalogueRefreshPolicy {
    fun isDue(lastSuccess: Long, hours: Int, now: Long): Boolean {
        if (hours <= 0) return false
        if (lastSuccess <= 0L) return true
        if (now < lastSuccess) return false
        return now - lastSuccess >= hours.toLong() * 3_600_000L
    }
}
