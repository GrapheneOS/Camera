package app.grapheneos.camera.data.camera.mapper

import android.view.Surface
import app.grapheneos.camera.data.core.model.DeviceOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraXConstantsMapperTest {

    private val mapper = CameraXConstantsMapperImpl()

    @Test
    fun deviceOrientation_turnsTheOutputTheOtherWayRound() {
        assertEquals(Surface.ROTATION_0, mapper.map(DeviceOrientation.DEGREES_0))
        assertEquals(Surface.ROTATION_270, mapper.map(DeviceOrientation.DEGREES_90))
        assertEquals(Surface.ROTATION_180, mapper.map(DeviceOrientation.DEGREES_180))
        assertEquals(Surface.ROTATION_90, mapper.map(DeviceOrientation.DEGREES_270))
    }
}
