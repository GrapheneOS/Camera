package app.grapheneos.camera

import android.app.Application
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.DefaultDispatcher
import app.grapheneos.camera.di.preferences.SecureSessionPreferences
import app.grapheneos.camera.domain.capture.usecase.DeleteStalePendingRecordings
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class App : Application() {

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
}
