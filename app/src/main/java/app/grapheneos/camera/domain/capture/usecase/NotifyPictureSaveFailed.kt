package app.grapheneos.camera.domain.capture.usecase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Resources
import app.grapheneos.camera.R
import app.grapheneos.camera.data.core.notification.NotificationBuilderFactory
import javax.inject.Inject

interface NotifyPictureSaveFailed {
    suspend operator fun invoke()
}

internal class NotifyPictureSaveFailedImpl @Inject constructor(
    private val resources: Resources,
    private val notificationManager: NotificationManager,
    private val notificationBuilderFactory: NotificationBuilderFactory,
) : NotifyPictureSaveFailed {

    override suspend fun invoke() {
        val title = resources.getString(R.string.unable_to_save_image)
        val channel = NotificationChannel(
            CHANNEL_ID,
            title,
            NotificationManager.IMPORTANCE_HIGH,
        )

        val notification = notificationBuilderFactory.create(CHANNEL_ID)
            .setSmallIcon(R.drawable.info)
            .setContentTitle(title)
            .build()

        notificationManager.createNotificationChannel(channel)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        private const val CHANNEL_ID = "image_saver_error"
        private const val NOTIFICATION_ID = 1
    }
}
