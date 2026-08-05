package app.grapheneos.camera

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryPointContractTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager: PackageManager = context.packageManager

    private fun activityInfoFor(action: String): ActivityInfo {
        val intent = Intent(action).setPackage(context.packageName)
        val matches = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        assertEquals(
            "Exactly one component in this app must answer $action, but got" +
                " ${matches.map { it.activityInfo.name }}",
            1,
            matches.size,
        )
        return matches.single().activityInfo
    }

    private fun assertHandledBy(
        action: String,
        expectedComponent: String,
    ) {
        assertEquals(
            "$action must be handled by $expectedComponent",
            "$PACKAGE.$expectedComponent",
            activityInfoFor(action).name,
        )
    }

    private fun assertIsLockscreenEntryPoint(
        action: String,
        expectedAffinity: String,
    ) {
        val info = activityInfoFor(action)

        assertTrue(
            "${info.name} must show over the keyguard, or $action does nothing on a locked" +
                " phone",
            info.flags and FLAG_SHOW_WHEN_LOCKED != 0,
        )
        assertTrue(
            "${info.name} must be excluded from recents, or what a locked session captured is" +
                " listed to whoever picks the phone up next",
            info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0,
        )

        assertTrue(
            "${info.name} must keep its own taskAffinity ending in" +
                " .ui.activities.$expectedAffinity, or the locked session can surface the" +
                " unlocked task, but it is ${info.taskAffinity}",
            info.taskAffinity.orEmpty().endsWith(".ui.activities.$expectedAffinity"),
        )
    }

    @Test
    fun stillImageCameraIsAnAliasOntoTheMainActivity() {
        val info = activityInfoFor("android.media.action.STILL_IMAGE_CAMERA")

        assertEquals("$PACKAGE.ui.activities.CameraLauncher", info.name)
        assertEquals("$PACKAGE.ui.activities.MainActivity", info.targetActivity)
    }

    @Test
    fun videoCameraLaunchesTheVideoOnlyActivity() {
        assertHandledBy("android.media.action.VIDEO_CAMERA", "ui.activities.VideoOnlyActivity")
    }

    @Test
    fun imageCaptureLaunchesTheCaptureActivity() {
        assertHandledBy("android.media.action.IMAGE_CAPTURE", "ui.activities.CaptureActivity")
    }

    @Test
    fun videoCaptureLaunchesTheVideoCaptureActivity() {
        assertHandledBy(
            "android.media.action.VIDEO_CAPTURE",
            "ui.activities.VideoCaptureActivity",
        )
    }

    @Test
    fun secureStillImageCameraLaunchesTheSecureMainActivity() {
        assertHandledBy(
            "android.media.action.STILL_IMAGE_CAMERA_SECURE",
            "ui.activities.SecureMainActivity",
        )
    }

    @Test
    fun secureImageCaptureLaunchesTheSecureCaptureActivity() {
        assertHandledBy(
            "android.media.action.IMAGE_CAPTURE_SECURE",
            "ui.activities.SecureCaptureActivity",
        )
    }

    @Test
    fun secureStillImageCameraIsALockscreenEntryPoint() {
        assertIsLockscreenEntryPoint(
            action = "android.media.action.STILL_IMAGE_CAMERA_SECURE",
            expectedAffinity = "SecureMainActivity",
        )
    }

    @Test
    fun secureImageCaptureIsALockscreenEntryPoint() {
        assertIsLockscreenEntryPoint(
            action = "android.media.action.IMAGE_CAPTURE_SECURE",
            expectedAffinity = "SecureCaptureActivity",
        )
    }

    @Test
    fun theUnlockedEntryPointsDoNotShowOverTheKeyguard() {
        // The mirror of the assertions above: were every activity showWhenLocked, they would
        // pass while the distinction they exist to protect had been erased.
        listOf(
            "android.media.action.VIDEO_CAMERA",
            "android.media.action.IMAGE_CAPTURE",
            "android.media.action.VIDEO_CAPTURE",
        ).forEach { action ->
            val info = activityInfoFor(action)

            assertEquals(
                "${info.name} answers the non-secure $action and must not show over the" +
                    " keyguard",
                0,
                info.flags and FLAG_SHOW_WHEN_LOCKED,
            )
        }
    }

    @Test
    fun qrTileKeepsTheNameAndFlagsSystemUiDependsOn() {
        val info = packageManager.getActivityInfo(
            ComponentName(context.packageName, "$PACKAGE.ui.activities.QrTile"),
            PackageManager.MATCH_ALL,
        )

        assertTrue(
            "QrTile must stay exported — SystemUI starts it from outside the app",
            info.exported,
        )
        assertTrue(
            "QrTile must show over the keyguard; it is a lockscreen shortcut target",
            info.flags and FLAG_SHOW_WHEN_LOCKED != 0,
        )
        assertTrue(
            "QrTile must be excluded from recents",
            info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0,
        )
        assertNull("QrTile is a real activity, not an alias", info.targetActivity)
    }

    private companion object {
        const val PACKAGE = "app.grapheneos.camera"

        // ActivityInfo.FLAG_SHOW_WHEN_LOCKED is @hide
        const val FLAG_SHOW_WHEN_LOCKED = 0x800000
    }
}
