package app.grapheneos.camera.data.camera.model

import androidx.camera.core.featuregroup.GroupableFeature

sealed interface CameraSessionEvent {

    data object ZoomStateChanged : CameraSessionEvent
    data object CameraProviderUnavailable : CameraSessionEvent
    data object ExtensionsUnavailable : CameraSessionEvent

    data class ProviderReady(
        val forced: Boolean,
    ) : CameraSessionEvent

    data class QrCodeScanned(
        val text: String,
    ) : CameraSessionEvent

    data class FeaturesSelected(
        val boundLensFacing: LensFacing,
        val requested: List<GroupableFeature>,
        val qualityFeature: GroupableFeature?,
        val selected: Set<GroupableFeature>,
    ) : CameraSessionEvent
}
