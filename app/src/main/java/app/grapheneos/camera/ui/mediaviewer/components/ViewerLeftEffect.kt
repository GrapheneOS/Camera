package app.grapheneos.camera.ui.mediaviewer.components

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
internal fun ViewerLeftEffect(onLeave: () -> Unit) {
    val activity = LocalActivity.current

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) {
            onLeave()
        }
    }
}
