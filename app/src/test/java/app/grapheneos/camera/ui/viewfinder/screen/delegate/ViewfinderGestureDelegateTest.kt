package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SwipeOutcomeMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderGestureDelegateTest {

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.CAMERA)

    private val delegate = ViewfinderGestureDelegateImpl(
        swipeOutcomeMapper = SwipeOutcomeMapperImpl(),
    ).also { it.bind(stateHolder) }

    @Test
    fun sidewaysSwipes_moveAcrossTheModeTabs() {
        runTest {
            stateHolder.update { it.copy(showsCameraModeTabs = true) }
            val effects = collectEffects(stateHolder)

            delegate.onPreviewSwiped(SwipeDirection.LEFT)
            delegate.onPreviewSwiped(SwipeDirection.RIGHT)

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ModeTab.SelectAdjacent(offset = 1),
                    ViewfinderScreenEffect.ModeTab.SelectAdjacent(offset = -1),
                ),
                effects,
            )
        }
    }
}
