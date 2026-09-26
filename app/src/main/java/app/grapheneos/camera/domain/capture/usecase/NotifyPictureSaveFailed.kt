package app.grapheneos.camera.domain.capture.usecase

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.grapheneos.camera.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface NotifyPictureSaveFailed {
    suspend operator fun invoke()
}

internal class NotifyPictureSaveFailedImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) : NotifyPictureSaveFailed {

    override suspend fun invoke() {
        val title = context.getString(R.string.unable_to_save_image)
        val channel = NotificationChannel(
            CHANNEL_ID,
            title,
            NotificationManager.IMPORTANCE_HIGH,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
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
