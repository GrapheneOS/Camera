package app.grapheneos.camera.portrait

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import app.grapheneos.camera.R

class PortraitRenderService : Service() {
    private data class QueueProgress(val position: Int, val total: Int, val percent: Int)

    companion object {
        private const val CHANNEL = "fold_camera_portrait_processing"
        private const val NOTIFICATION_ID = 91033
        private const val LEGACY_PROGRESS_ID = 91032
        private const val ACTION_START = "app.foldcamera.portrait.START"
        private const val ACTION_FINISH = "app.foldcamera.portrait.FINISH"
        private const val TAG = "FoldPortraitAI"
        @Volatile private var currentProgress = QueueProgress(1, 1, 0)
        @Volatile private var foregroundActive = false

        fun begin(context: Context) {
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, PortraitRenderService::class.java).setAction(ACTION_START))
            } catch (error: Exception) {
                Log.w(TAG, "Unable to start portrait processing service", error)
            }
        }

        fun updateProgress(context: Context, position: Int, total: Int, percent: Int) {
            currentProgress = QueueProgress(position.coerceAtLeast(1),
                total.coerceAtLeast(1), percent.coerceIn(0, 100))
            if (!foregroundActive) return
            try {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification(context, currentProgress))
            } catch (error: Exception) {
                Log.w(TAG, "Unable to update portrait processing notification", error)
            }
        }

        fun finishIfIdle(context: Context) {
            if (PortraitEngine.hasPendingPortraits()) return
            try {
                context.startService(Intent(context, PortraitRenderService::class.java)
                    .setAction(ACTION_FINISH))
            } catch (error: Exception) {
                Log.w(TAG, "Unable to finish portrait processing service", error)
            }
        }

        private fun notification(context: Context, progress: QueueProgress): Notification {
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.info)
                .setContentTitle("Fold Camera — Processing portrait")
                .setContentText("Portrait ${progress.position} of ${progress.total} — ${progress.percent}%")
                .setProgress(100, progress.percent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FINISH) {
            if (!PortraitEngine.hasPendingPortraits()) {
                foregroundActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "Portrait processing", NotificationManager.IMPORTANCE_LOW))
            manager.cancel(LEGACY_PROGRESS_ID)
            val activeNotification = notification(this, currentProgress)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, activeNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, activeNotification)
            }
            foregroundActive = true
            manager.notify(NOTIFICATION_ID, notification(this, currentProgress))
        } catch (error: Exception) {
            foregroundActive = false
            Log.e(TAG, "Unable to display portrait processing notification", error)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        foregroundActive = false
        super.onDestroy()
    }
}
