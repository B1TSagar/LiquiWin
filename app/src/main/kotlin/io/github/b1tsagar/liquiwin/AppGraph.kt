package io.github.b1tsagar.liquiwin

import android.content.Context
import android.os.UserManager
import io.github.b1tsagar.liquiwin.core.data.apps.AppCatalog
import io.github.b1tsagar.liquiwin.core.data.apps.AppRepository
import io.github.b1tsagar.liquiwin.core.data.apps.IconCache
import io.github.b1tsagar.liquiwin.core.data.apps.LauncherAppsSource
import io.github.b1tsagar.liquiwin.core.data.db.cache.CacheDatabase
import io.github.b1tsagar.liquiwin.core.data.db.layout.LayoutDatabase
import io.github.b1tsagar.liquiwin.core.tiles.RoomTileStore
import io.github.b1tsagar.liquiwin.core.tiles.TileStore
import io.github.b1tsagar.liquiwin.core.tiles.source.SourceRegistry
import io.github.b1tsagar.liquiwin.feature.notifications.AndroidItemLauncher
import io.github.b1tsagar.liquiwin.feature.notifications.AppNotificationSource
import io.github.b1tsagar.liquiwin.feature.notifications.ImageStore
import io.github.b1tsagar.liquiwin.feature.notifications.LiveActionCache
import io.github.b1tsagar.liquiwin.feature.notifications.NotificationIntake
import io.github.b1tsagar.liquiwin.feature.notifications.Normaliser
import io.github.b1tsagar.liquiwin.home.HomeRole
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The app's singletons, each built on first use. */
class AppGraph(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Shared, because two Room instances must never open the same file.
    private val cacheDb by lazy { CacheDatabase.open(app) }

    val tileStore: TileStore by lazy { RoomTileStore(LayoutDatabase.open(app).tileDao()) }

    val catalog: AppCatalog by lazy {
        val source = LauncherAppsSource(app)
        val icons = IconCache(source, File(app.cacheDir, "icons"), app.resources.displayMetrics.densityDpi)
        AppRepository(source, cacheDb, icons, scope)
    }

    val homeRole: HomeRole by lazy { HomeRole(app) }

    val imageStore: ImageStore by lazy { ImageStore(app, File(app.cacheDir, "notification-images")) }

    val liveActions = LiveActionCache()

    val notificationIntake: NotificationIntake by lazy {
        val users = app.getSystemService(UserManager::class.java)
        val normaliser = Normaliser(app, users::getSerialNumberForUser)
        NotificationIntake(normaliser::normalise, cacheDb.notificationDao(), liveActions, imageStore, io).also { intake ->
            io.launch {
                while (true) {
                    intake.cleanUp()
                    delay(1.hours)
                }
            }
        }
    }

    val sources = SourceRegistry { id ->
        id.appPackageAndSerial()?.let { (packageName, serial) ->
            AppNotificationSource(packageName, serial, cacheDb.notificationDao(), liveActions, imageStore.dir, catalog, AndroidItemLauncher(app))
        }
    }
}
