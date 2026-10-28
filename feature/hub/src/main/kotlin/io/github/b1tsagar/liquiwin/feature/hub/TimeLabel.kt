package io.github.b1tsagar.liquiwin.feature.hub

import java.text.DateFormat
import java.time.Instant
import java.util.Date

/**
 * The time in [format], after "yesterday " if it's from an earlier day in [format]'s time zone. Messages are kept for
 * 24 hours (§3.3), so an earlier day is yesterday.
 */
internal fun timeLabel(time: Instant, now: Instant, format: DateFormat): String {
    val zone = format.timeZone.toZoneId()
    val text = format.format(Date.from(time))
    return if (time.atZone(zone).toLocalDate() < now.atZone(zone).toLocalDate()) "yesterday $text" else text
}
