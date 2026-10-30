package app.grapheneos.camera.data.camera.model

import androidx.camera.core.featuregroup.GroupableFeature

sealed interface FeatureGroupRequest {

    /** Highest priority first, without stabilization, which is given up first. */
    fun chosenFeatures(): List<GroupableFeature>

    data object Unused : FeatureGroupRequest {

        override fun chosenFeatures(): List<GroupableFeature> {
            return emptyList()
        }
    }

    data class Requested(
        val videoQualityFeature: GroupableFeature?,
        val stabilization: Boolean,
        val hdr: Boolean,
        val highFrameRate: Boolean,
    ) : FeatureGroupRequest {

        override fun chosenFeatures(): List<GroupableFeature> {
            return listOfNotNull(
                videoQualityFeature,
                GroupableFeature.HDR_HLG10.takeIf { hdr },
                GroupableFeature.FPS_60.takeIf { highFrameRate },
            )
        }
    }
}
