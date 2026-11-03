package app.grapheneos.camera.ui.camerascreen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.di.core.cameraEntryPointOf
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.camerasettings.CameraSettingsActivity
import app.grapheneos.camera.ui.camerasettings.LicensesActivity
import app.grapheneos.camera.ui.camerasettings.SecureCameraSettingsActivity
import app.grapheneos.camera.ui.camerasettings.navigation.CameraSettingsIntentsImpl
import app.grapheneos.camera.ui.camerasettings.screen.cameraCapabilitiesOf
import app.grapheneos.camera.ui.core.activity.SecureActivity
import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.gallery.GalleryActivity
import app.grapheneos.camera.ui.gallery.SecureGalleryActivity
import app.grapheneos.camera.ui.gallery.navigation.GalleryIntentsImpl
import app.grapheneos.camera.ui.gallery.screen.lastCapturedItemOf
import app.grapheneos.camera.ui.gallery.screen.sessionItemsOf
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsNavEvent
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarNavEvent
import app.grapheneos.camera.ui.videoplayer.SecureVideoPlayerActivity
import app.grapheneos.camera.ui.videoplayer.VideoPlayerActivity
import app.grapheneos.camera.ui.videoplayer.navigation.VideoPlayerLauncherImpl
import app.grapheneos.camera.ui.videoplayer.screen.videoUriOf
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class CameraScreenNavigationHandlerTest {

    private val hosts = mutableListOf<ActivityController<out Activity>>()
    private val entryPoint = cameraEntryPoint(
        isCaptureSession = true,
        requiresVideoModeOnly = true,
    )
    private val capabilities = CameraCapabilities(lensFacing = LensFacing.FRONT)
    private val item = CapturedItem(
        uri = Uri.parse("content://media/external/images/media/42"),
        type = CapturedItemType.IMAGE,
        dateString = "20261103_120000",
    )

    @After
    fun tearDown() {
        hosts.forEach { it.pause().stop().destroy() }
    }

    @Test
    fun viewfinderSettings_preserveTheHostsSessionAndCapabilities() {
        val activity = createActivity(ComponentActivity::class.java)

        handler(activity).handle(ViewfinderNavEvent.OpenCameraSettings(capabilities))

        assertSettings(
            intent = shadowOf(activity).nextStartedActivity,
            screen = CameraSettingsActivity::class.java,
            expectedEntryPoint = entryPoint,
        )
    }

    @Test
    fun quickSettings_preserveTheirSessionAndCapabilities() {
        val activity = createActivity(ComponentActivity::class.java)
        val session = cameraEntryPoint(isVideoOnlySession = true)

        handler(activity).handle(
            QuickSettingsNavEvent.OpenCameraSettings(
                entryPoint = session,
                capabilities = capabilities,
            ),
        )

        assertSettings(
            intent = shadowOf(activity).nextStartedActivity,
            screen = CameraSettingsActivity::class.java,
            expectedEntryPoint = session,
        )
    }

    @Test
    fun statusBar_preservesItsSessionAndCapabilities() {
        val activity = createActivity(ComponentActivity::class.java)
        val session = cameraEntryPoint(showsCameraModeTabs = false)

        handler(activity).handle(
            StatusBarNavEvent.OpenCameraSettings(
                entryPoint = session,
                capabilities = capabilities,
            ),
        )

        assertSettings(
            intent = shadowOf(activity).nextStartedActivity,
            screen = CameraSettingsActivity::class.java,
            expectedEntryPoint = session,
        )
    }

    @Test
    fun allSettingsEntrances_chooseTheSecureScreenForASecureHost() {
        val activity = createActivity(SecureHost::class.java)
        val session = cameraEntryPoint(isSecureSession = true)
        val navigation = handler(
            activity = activity,
            session = session,
        )

        navigation.handle(ViewfinderNavEvent.OpenCameraSettings(capabilities))
        navigation.handle(
            QuickSettingsNavEvent.OpenCameraSettings(
                entryPoint = session,
                capabilities = capabilities,
            ),
        )
        navigation.handle(
            StatusBarNavEvent.OpenCameraSettings(
                entryPoint = session,
                capabilities = capabilities,
            ),
        )

        repeat(3) {
            assertSettings(
                intent = shadowOf(activity).nextStartedActivity,
                screen = SecureCameraSettingsActivity::class.java,
                expectedEntryPoint = session,
            )
        }
    }

    @Test
    fun gallery_preservesTheEntryPointAndLastItemWithoutASecureList() {
        val activity = createActivity(ComponentActivity::class.java)

        handler(activity).handle(ViewfinderNavEvent.OpenGallery(lastCapturedItem = item))

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(GalleryActivity::class.java.name, intent.component?.className)
        val arguments = arguments(intent)
        assertEquals(entryPoint, cameraEntryPointOf(arguments))
        assertEquals(item, lastCapturedItemOf(arguments))
        assertNull(sessionItemsOf(arguments))
    }

    @Test
    fun secureGallery_receivesOnlyTheSessionItemsAndTheLastItem() {
        val activity = createActivity(SecureHost::class.java)
        val session = cameraEntryPoint(isSecureSession = true)

        handler(
            activity = activity,
            session = session,
        ).handle(
            ViewfinderNavEvent.OpenSecureGallery(
                capturedItems = listOf(item),
                lastCapturedItem = item,
            ),
        )

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(SecureGalleryActivity::class.java.name, intent.component?.className)
        val arguments = arguments(intent)
        assertEquals(session, cameraEntryPointOf(arguments))
        assertEquals(listOf(item), sessionItemsOf(arguments))
        assertEquals(item, lastCapturedItemOf(arguments))
    }

    @Test
    fun licenses_useTheHostsActivityAndTheInternalLicensesScreen() {
        val activity = createActivity(ComponentActivity::class.java)

        CameraSettingsIntentsImpl().openLicenses(activity)

        assertEquals(
            LicensesActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
    }

    @Test
    fun settingsPanel_usesTheHostsCallbacks() {
        val activity = createActivity(ComponentActivity::class.java)
        val opened = mutableListOf<QuickSetting?>()
        var closed = false
        val navigation = handler(
            activity = activity,
            onOpenSettingsPanel = { opened += it },
            onCloseSettingsPanel = { closed = true },
        )

        navigation.handle(ViewfinderNavEvent.OpenSettingsPanel(QuickSetting.FLASH))
        navigation.handle(ViewfinderNavEvent.CloseSettingsPanel)

        assertEquals(listOf(QuickSetting.FLASH), opened)
        assertTrue(closed)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun closingTheScreen_usesTheHostsCloseAction() {
        val activity = createActivity(ComponentActivity::class.java)

        var closed = false
        handler(
            activity = activity,
            onCloseScreen = { closed = true },
        ).handle(ViewfinderNavEvent.CloseScreen)

        assertTrue(closed)
        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun navigation_keepsTheHostingActivitysLaunchInterceptor() {
        val activity = createActivity(InterceptingHost::class.java)
        val navigation = handler(activity)

        navigation.handle(ViewfinderNavEvent.OpenGallery(lastCapturedItem = null))
        assertEquals(GalleryActivity::class.java.name, activity.intercepted?.component?.className)
        navigation.handle(ViewfinderNavEvent.OpenCameraSettings(capabilities))
        assertEquals(
            SecureCameraSettingsActivity::class.java.name,
            activity.intercepted?.component?.className,
        )
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun videoPlayer_preservesTheUriAndChoosesTheRegularScreen() {
        val activity = createActivity(ComponentActivity::class.java)

        handler(activity).handle(ViewfinderNavEvent.OpenVideo(item.uri))

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(VideoPlayerActivity::class.java.name, intent.component?.className)
        assertEquals(item.uri, videoUriOf(arguments(intent)))
    }

    @Test
    fun videoPlayer_preservesTheUriAndChoosesTheSecureScreen() {
        val activity = createActivity(SecureHost::class.java)

        handler(activity).handle(ViewfinderNavEvent.OpenVideo(item.uri))

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(SecureVideoPlayerActivity::class.java.name, intent.component?.className)
        assertEquals(item.uri, videoUriOf(arguments(intent)))
    }

    @Test
    fun videoPlayer_keepsTheHostingActivitysLaunchInterceptor() {
        val activity = createActivity(InterceptingHost::class.java)

        handler(activity).handle(ViewfinderNavEvent.OpenVideo(item.uri))

        val intent = requireNotNull(activity.intercepted)
        assertEquals(SecureVideoPlayerActivity::class.java.name, intent.component?.className)
        assertEquals(item.uri, videoUriOf(arguments(intent)))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    private fun handler(
        activity: Activity,
        session: CameraEntryPoint = entryPoint,
        onOpenSettingsPanel: (QuickSetting?) -> Unit = {},
        onCloseSettingsPanel: () -> Unit = {},
        onCloseScreen: () -> Unit = activity::finish,
    ): CameraScreenNavigationHandler {
        return CameraScreenNavigationHandlerImpl(
            activity = activity,
            entryPoint = session,
            videoPlayerLauncher = VideoPlayerLauncherImpl(),
            cameraSettingsIntents = CameraSettingsIntentsImpl(),
            galleryIntents = GalleryIntentsImpl(),
            onOpenSettingsPanel = onOpenSettingsPanel,
            onCloseSettingsPanel = onCloseSettingsPanel,
            onCloseScreen = onCloseScreen,
        )
    }

    private fun assertSettings(
        intent: Intent,
        screen: Class<out Activity>,
        expectedEntryPoint: CameraEntryPoint,
    ) {
        assertEquals(screen.name, intent.component?.className)
        val arguments = arguments(intent)
        assertEquals(expectedEntryPoint, cameraEntryPointOf(arguments))
        assertEquals(capabilities, cameraCapabilitiesOf(arguments))
    }

    private fun arguments(intent: Intent): SavedStateHandle {
        return SavedStateHandle.createHandle(
            restoredState = null,
            defaultState = intent.extras,
        )
    }

    private fun <T : Activity> createActivity(type: Class<T>): T {
        val host = Robolectric.buildActivity(type).setup()
        hosts += host
        return host.get()
    }

    class SecureHost :
        ComponentActivity(),
        SecureActivity

    class InterceptingHost :
        ComponentActivity(),
        SecureActivity {
        var intercepted: Intent? = null

        override fun startActivity(
            intent: Intent,
            options: Bundle?,
        ) {
            intercepted = intent
        }
    }
}
