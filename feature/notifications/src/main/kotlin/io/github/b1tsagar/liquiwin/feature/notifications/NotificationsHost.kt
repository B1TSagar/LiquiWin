package io.github.b1tsagar.liquiwin.feature.notifications

/** What the listener needs from the app. In safe mode the app hands out nothing. */
interface NotificationsHost {
    val notificationIntake: NotificationIntake?
}
