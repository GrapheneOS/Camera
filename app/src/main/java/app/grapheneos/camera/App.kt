package app.grapheneos.camera

import android.app.Application
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.capturer.deleteStalePendingRecordings
import app.grapheneos.camera.di.preferences.SecureSessionPreferences
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.concurrent.thread

@HiltAndroidApp
class App : Application() {

    private var activity: MainActivity? = null

    private val autoSleepDuration: Long = 5 * 60 * 1000 // 5 minutes
    private val autoSleepTimer = object : CountDownTimer(
        autoSleepDuration,
        autoSleepDuration / 2
    ) {
        override fun onTick(milliLeft: Long) {}

        override fun onFinish() {
            activity?.enableAutoSleep()
        }
    }

    @Inject
    internal lateinit var secureSessionPreferences: SecureSessionPreferences

    private val activityLifeCycleHelper by lazy {
        ActivityLifeCycleHelper(
            onResumedActivityChanged = { activity ->
                when {
                    activity != null -> activity.disableAutoSleep()
                    else -> this.activity?.enableAutoSleep()
                }
                this.activity = activity
            },
            onSecureActivityCreated = {
                secureSessionPreferences.onSecureActivityCreated()
            },
            onSecureActivityDestroyed = { isChangingConfigurations ->
                secureSessionPreferences.onSecureActivityDestroyed(isChangingConfigurations)
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityLifeCycleHelper)
        DynamicColors.applyToActivitiesIfAvailable(this)

        thread {
            deleteStalePendingRecordings(this)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterActivityLifecycleCallbacks(activityLifeCycleHelper)
    }

    private fun AppCompatActivity.disableAutoSleep() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resetPreventScreenFromSleeping()
    }

    fun resetPreventScreenFromSleeping() {
        autoSleepTimer.cancel()
        autoSleepTimer.start()
    }

    private fun AppCompatActivity.enableAutoSleep() {
        autoSleepTimer.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
