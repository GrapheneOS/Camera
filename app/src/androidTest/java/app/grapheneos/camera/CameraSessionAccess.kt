package app.grapheneos.camera

import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.di.camera.CameraSessionEntryPoint
import app.grapheneos.camera.ui.activities.MainActivity
import dagger.hilt.android.EntryPointAccessors

internal fun cameraSession(activity: MainActivity): CameraSession {
    return EntryPointAccessors
        .fromActivity(activity, CameraSessionEntryPoint::class.java)
        .cameraSession()
}
