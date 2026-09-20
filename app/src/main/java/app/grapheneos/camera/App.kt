package app.grapheneos.camera

import android.app.Application
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.DefaultDispatcher
import app.grapheneos.camera.di.preferences.SecureSessionPreferences
import app.grapheneos.camera.domain.capture.usecase.DeleteStalePendingRecordings
import app.grapheneos.camera.ui.activities.MainActivity
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    @Inject
    internal lateinit var deleteStalePendingRecordings: DeleteStalePendingRecordings

    @Inject
    @ApplicationScope
    internal lateinit var applicationScope: CoroutineScope

    @Inject
    @DefaultDispatcher
    internal lateinit var defaultDispatcher: CoroutineDispatcher

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

        applicationScope.launch(defaultDispatcher) {
            deleteStalePendingRecordings()
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
