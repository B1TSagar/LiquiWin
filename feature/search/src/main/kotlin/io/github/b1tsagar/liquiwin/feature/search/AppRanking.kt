package io.github.b1tsagar.liquiwin.feature.search

import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WORD_BREAKS = Regex("[^\\p{L}\\p{N}]+")

/**
 * The apps whose label matches [query], best first. Tiers: the label starts with it, a later word starts with it, the
 * initials start with it, the label contains it. Ties go to the most recently launched, then the Collator order.
 */
fun rankApps(
    query: String,
    apps: List<LauncherApp>,
    lastLaunched: Map<AppKey, Long>,
    collator: Collator = Collator.getInstance(),
): List<LauncherApp> {
    val q = normalize(query.trim())
    if (q.isEmpty()) return emptyList()
    return apps.mapNotNull { app -> tierOf(q, app.label)?.let { it to app } }
        .sortedWith(
            compareBy<Pair<Int, LauncherApp>> { it.first }
                .thenByDescending { lastLaunched[it.second.key] ?: Long.MIN_VALUE }
                .thenBy(collator) { it.second.label }
                .thenBy { it.second.key.flatten() },
        )
        .map { it.second }
}

private fun tierOf(q: String, label: String): Int? {
    val normalized = normalize(label)
    return when {
        normalized.startsWith(q) -> 0
        normalized.split(WORD_BREAKS).drop(1).any { it.startsWith(q) } -> 1
        initials(label).startsWith(q) -> 2
        q in normalized -> 3
        else -> null
    }
}

// The first letter of each word plus each uppercase letter after a lowercase one, from the original label: "WhatsApp" → "wa".
private fun initials(label: String): String {
    val out = StringBuilder()
    var previous = -1
    var i = 0
    while (i < label.length) {
        val c = label.codePointAt(i)
        val startsWord = Character.isLetterOrDigit(c) && (previous == -1 || !Character.isLetterOrDigit(previous))
        val camelHump = Character.isUpperCase(c) && previous != -1 && Character.isLowerCase(previous)
        if (startsWord || camelHump) out.appendCodePoint(c)
        previous = c
        i += Character.charCount(c)
    }
    return normalize(out.toString())
}

private fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)
