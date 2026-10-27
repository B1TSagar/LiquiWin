package io.github.b1tsagar.liquiwin.feature.applist

import android.icu.text.AlphabeticIndex
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import java.text.Collator
import java.util.Locale

data class AppIndex(val sections: List<AppSection>, val letters: List<LetterCell>)

/** [label] is lowercase. */
data class AppSection(val label: String, val apps: List<LauncherApp>)

data class LetterCell(val label: String, val hasApps: Boolean)

private const val HASH = "#"

/**
 * Groups apps for the app list: "#" for labels that don't start with a letter, then the locale's letters (plus
 * English), then a group per first letter of any other script, in Collator order.
 */
class AppGrouper(private val locale: Locale) {
    private val index = AlphabeticIndex<Any>(locale).addLabels(Locale.ENGLISH).buildImmutableIndex()
    private val letterLabels = index.filter { it.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL }.map { it.label }

    fun group(apps: List<LauncherApp>): AppIndex {
        val collator = Collator.getInstance(locale)
        val groups = apps.groupBy(::groupOf)
        val otherScripts = groups.keys.filter { it != HASH && it !in letterLabels }.sortedWith(collator)
        val order = listOf(HASH) + letterLabels + otherScripts
        val byLabel = compareBy<LauncherApp, String>(collator) { it.label }.thenBy { it.key.flatten() }
        return AppIndex(
            sections = order.mapNotNull { key -> groups[key]?.let { AppSection(key.lowercase(locale), it.sortedWith(byLabel)) } },
            letters = order.map { LetterCell(it.lowercase(locale), hasApps = it in groups) },
        )
    }

    private fun groupOf(app: LauncherApp): String {
        val first = app.label.takeIf { it.isNotEmpty() }?.codePointAt(0)
        if (first == null || !Character.isLetter(first)) return HASH
        val bucket = index.getBucket(index.getBucketIndex(app.label))
        // Other scripts group by their first letter, case folded so "Я" and "я" make one section.
        return if (bucket.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL) bucket.label else String(Character.toChars(first)).lowercase(locale)
    }
}
