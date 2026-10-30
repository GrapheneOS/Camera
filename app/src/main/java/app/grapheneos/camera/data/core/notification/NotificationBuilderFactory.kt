package app.grapheneos.camera.data.core.notification

import android.app.Notification

fun interface NotificationBuilderFactory {
    fun create(channelId: String): Notification.Builder
}
