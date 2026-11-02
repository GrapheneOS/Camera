package app.grapheneos.camera.di.core

import javax.inject.Qualifier

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainImmediateDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainLooperDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DurablePreferences

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class VideoFrameDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class QrAnalysisDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class CameraSessionScope
