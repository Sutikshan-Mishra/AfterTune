package dev.aftertune.artwork

import android.service.notification.NotificationListenerService

/**
 * Intentionally empty. Android only lets an app read other apps' media sessions when it holds an enabled
 * notification listener; this component is that hook. Notification content is never read.
 */
class ExternalMediaListener : NotificationListenerService()
