package io.github.b1tsagar.liquiwin.feature.hub

/** A scrollbar's thumb, [topPx] down its track, which is as long as the viewport. */
internal data class ScrollThumb(val topPx: Float, val lengthPx: Float)

/**
 * The thumb for a [viewportPx] view onto [contentPx] of content scrolled [fromTopPx] from its top: as long as the share
 * in view, but at least [minLengthPx], and always inside the track. Null when everything fits.
 */
internal fun scrollThumb(viewportPx: Float, contentPx: Float, fromTopPx: Float, minLengthPx: Float): ScrollThumb? {
    if (viewportPx <= 0f || contentPx <= viewportPx) return null
    val length = (viewportPx * viewportPx / contentPx).coerceIn(minOf(minLengthPx, viewportPx), viewportPx)
    val scrolled = (fromTopPx / (contentPx - viewportPx)).coerceIn(0f, 1f)
    return ScrollThumb(scrolled * (viewportPx - length), length)
}
