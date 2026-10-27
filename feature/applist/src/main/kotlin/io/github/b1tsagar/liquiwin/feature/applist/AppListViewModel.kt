package io.github.b1tsagar.liquiwin.feature.applist

import android.graphics.Rect
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.data.apps.AppKey
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherApp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppListState(val index: AppIndex, val pinned: Set<AppKey>, val newApps: Set<AppKey>)

private const val NEW_FOR_MILLIS = 3 * 24 * 60 * 60 * 1000L

class AppListViewModel(
    private val catalog: AppCatalog,
    pinnedApps: Flow<Set<AppKey>>,
    private val onPinToStart: (AppKey) -> Unit,
    nowMillis: () -> Long = System::currentTimeMillis,
    locale: Locale = Locale.getDefault(),
) : ViewModel() {
    // Built off the main thread with the first grouping.
    private val grouper by lazy { AppGrouper(locale) }

    val state: StateFlow<AppListState> = combine(
        catalog.apps.map { apps ->
            val newSince = nowMillis() - NEW_FOR_MILLIS
            grouper.group(apps) to apps.filter { it.firstInstallTime >= newSince }.mapTo(HashSet()) { it.key }
        }.flowOn(Dispatchers.Default),
        pinnedApps,
    ) { (index, newApps), pinned -> AppListState(index, pinned, newApps) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppListState(AppIndex(emptyList(), emptyList()), emptySet(), emptySet()))

    /** A paused work app asks to unpause its profile instead of launching. */
    fun open(app: LauncherApp, sourceBounds: Rect?, options: Bundle?) {
        if (app.isPaused) catalog.requestUnpause(app.key.userSerial) else catalog.launch(app.key, sourceBounds, options)
    }

    fun pin(app: LauncherApp) = onPinToStart(app.key)

    fun appInfo(app: LauncherApp) = catalog.openAppInfo(app.key)

    fun uninstall(app: LauncherApp) = catalog.uninstall(app.key)
}
