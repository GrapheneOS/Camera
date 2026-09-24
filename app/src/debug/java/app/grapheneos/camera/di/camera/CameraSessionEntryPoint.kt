package app.grapheneos.camera.di.camera

import app.grapheneos.camera.data.camera.session.CameraSession
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@EntryPoint
@InstallIn(ActivityComponent::class)
internal interface CameraSessionEntryPoint {
    fun cameraSession(): CameraSession
}
