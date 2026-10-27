package io.github.b1tsagar.liquiwin

import android.content.Context
import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.data.apps.AppRepository
import io.github.b1tsagar.liquiwin.core.data.apps.IconCache
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherAppsSource
import io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase
import io.github.b1tsagar.liquiwin.core.data.db.layout.LayoutDatabase
import io.github.b1tsagar.liquiwin.core.tiles.RoomTileStore
import io.github.b1tsagar.liquiwin.core.tiles.TileStore
import io.github.b1tsagar.liquiwin.home.HomeRole
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The app's singletons, each built on first use. */
class AppGraph(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tileStore: TileStore by lazy { RoomTileStore(LayoutDatabase.open(app).tileDao()) }

    val catalog: AppCatalog by lazy {
        val source = LauncherAppsSource(app)
        val icons = IconCache(source, File(app.cacheDir, "icons"), app.resources.displayMetrics.densityDpi)
        AppRepository(source, CacheDatabase.open(app), icons, scope)
    }

    val homeRole: HomeRole by lazy { HomeRole(app) }
}
