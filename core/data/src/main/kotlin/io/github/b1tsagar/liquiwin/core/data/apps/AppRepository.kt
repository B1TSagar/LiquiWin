package io.github.b1tsagar.liquiwin.core.data.apps

import android.graphics.Rect
import android.os.Bundle
import io.github.b1tsagar.liquiwin.core.data.db.cache.AppEntryEntity
import io.github.b1tsagar.liquiwin.core.data.db.cache.AppLaunchEntity
import io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase
import java.text.Collator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The [AppCatalog]: shows the cached list at once, then keeps the list fresh from [source]. A burst of changes
 * causes one more refresh, and a change during a refresh is never missed.
 */
class AppRepository(
    private val source: AppSource,
    db: CacheDatabase,
    private val icons: IconCache,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : AppCatalog {
    private val dao = db.appDao()
    private val _apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    private val _profiles = MutableStateFlow<List<AppProfile>>(emptyList())
    private val _isFresh = MutableStateFlow(false)
    private val _lastLaunched = MutableStateFlow<Map<AppKey, Long>>(emptyMap())

    override val apps: StateFlow<List<LauncherApp>> = _apps.asStateFlow()
    override val profiles: StateFlow<List<AppProfile>> = _profiles.asStateFlow()
    override val isFresh: StateFlow<Boolean> = _isFresh.asStateFlow()
    override val lastLaunched: StateFlow<Map<AppKey, Long>> = _lastLaunched.asStateFlow()

    init {
        scope.launch {
            withContext(io) {
                _apps.value = sorted(dao.entries().mapNotNull { it.toApp() })
                val launches = dao.launches().mapNotNull { row -> AppKey.parse(row.appKey)?.let { it to row.lastLaunchedAt } }
                _lastLaunched.update { launches.toMap() + it }
            }
            source.changes().onStart { emit(Unit) }.conflate().collect { refresh() }
        }
    }

    override fun launch(key: AppKey, sourceBounds: Rect?, options: Bundle?) {
        source.launch(key, sourceBounds, options)
        val now = nowMillis()
        _lastLaunched.update { it + (key to now) }
        scope.launch(io) { dao.upsertLaunch(AppLaunchEntity(key.flatten(), now)) }
    }

    override fun openAppInfo(key: AppKey) = source.openAppInfo(key)

    override fun uninstall(key: AppKey) = source.uninstall(key)

    override fun requestUnpause(userSerial: Long) = source.requestUnpause(userSerial)

    override suspend fun icon(app: LauncherApp): AppIcon? = icons.get(app)

    private suspend fun refresh() = withContext(io) {
        val profiles = source.profiles()
        val apps = profiles.flatMap { profile ->
            source.activities(profile).map {
                LauncherApp(it.key, it.label, it.firstInstallTime, profile.isWork, profile.isPaused, it.versionToken)
            }
        }
        _apps.value = sorted(apps)
        _profiles.value = profiles
        _isFresh.value = true
        dao.replaceEntries(apps.map { AppEntryEntity(it.key.flatten(), it.label, it.firstInstallTime, it.isWorkProfile, it.versionToken) })
    }

    private fun sorted(apps: List<LauncherApp>): List<LauncherApp> {
        val collator = Collator.getInstance() // per call: the locale can change, and a Collator isn't thread-safe
        return apps.sortedWith(compareBy<LauncherApp, String>(collator) { it.label }.thenBy { it.key.flatten() })
    }

    // Cached apps show as unpaused until the first refresh says otherwise.
    private fun AppEntryEntity.toApp(): LauncherApp? =
        AppKey.parse(appKey)?.let { LauncherApp(it, label, firstInstallTime, isWork, isPaused = false, versionToken = versionToken) }
}
