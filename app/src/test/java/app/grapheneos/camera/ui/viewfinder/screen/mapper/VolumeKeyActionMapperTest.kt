package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.VolumeKey
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeKeyActionMapperTest {

    private val mapper = VolumeKeyActionMapperImpl()

    @Test
    fun shutter_takesTheShotOnReleaseOnly() {
        VolumeKey.entries.forEach { key ->
            assertNull(pressed(key, VolumeKeyAction.SHUTTER))
            assertEquals(CaptureAction.CaptureKeyPressed, released(key, VolumeKeyAction.SHUTTER))
        }
    }

    @Test
    fun zoom_upStepsInAndDownStepsOutWhileHeld() {
        assertEquals(
            CameraAction.ZoomStepKey.Pressed(ZoomDirection.IN),
            pressed(VolumeKey.UP, VolumeKeyAction.ZOOM),
        )
        assertEquals(
            CameraAction.ZoomStepKey.Pressed(ZoomDirection.OUT),
            pressed(VolumeKey.DOWN, VolumeKeyAction.ZOOM),
        )
    }

    @Test
    fun zoom_releaseStopsTheSteps() {
        VolumeKey.entries.forEach { key ->
            assertEquals(CameraAction.ZoomStepKey.Released, released(key, VolumeKeyAction.ZOOM))
        }
    }

    @Test
    fun offAndVolume_doNothingInTheViewfinder() {
        listOf(VolumeKeyAction.OFF, VolumeKeyAction.VOLUME).forEach { action ->
            VolumeKey.entries.forEach { key ->
                assertNull(pressed(key, action))
                assertNull(released(key, action))
            }
        }
    }

    private fun pressed(
        key: VolumeKey,
        action: VolumeKeyAction,
    ): ViewfinderAction? {
        return mapper.map(CaptureAction.VolumeKeyEvent.Pressed(key), action)
    }

    private fun released(
        key: VolumeKey,
        action: VolumeKeyAction,
    ): ViewfinderAction? {
        return mapper.map(CaptureAction.VolumeKeyEvent.Released(key), action)
    }
}
