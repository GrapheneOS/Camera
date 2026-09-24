package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelLocationTest : ViewfinderViewModelTestBase() {

    @Test
    fun previewStreamingStarted_storedGeoTaggingWithPermission_keepsItOn() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(geoTagging = true))
            }

            viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

            verify(exactly = 1) { settingsDelegate.setGeoTagging(true) }
        }
    }

    @Test
    fun geoTaggingToggledOn_withoutPermission_asksForIt() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(missingPermissions = setOf(AppPermission.LOCATION)) }

            viewModel.onAction(SettingsAction.GeoTaggingToggled(enabled = true))

            verify(exactly = 1) { settingsDelegate.setGeoTagging(true) }
            verify(exactly = 1) {
                permissionDelegate.request(
                    permission = AppPermission.LOCATION,
                    explainsFirst = true,
                )
            }
        }
    }

    @Test
    fun geoTaggingToggledOn_withPermission_asksForNothing() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(SettingsAction.GeoTaggingToggled(enabled = true))

            verify(exactly = 1) { settingsDelegate.setGeoTagging(true) }
            verify(exactly = 0) { permissionDelegate.request(any(), any()) }
        }
    }

    @Test
    fun locationPermissionAnswered_withARefusal_turnsGeoTaggingOff() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(missingPermissions = setOf(AppPermission.LOCATION)) }

            viewModel.onAction(PermissionAction.RequestAnswered(AppPermission.LOCATION))

            verify(exactly = 1) { settingsDelegate.setGeoTagging(false) }
        }
    }

    @Test
    fun locationPermissionAnswered_withAGrant_keepsGeoTaggingOn() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(PermissionAction.RequestAnswered(AppPermission.LOCATION))

            verify(exactly = 0) { settingsDelegate.setGeoTagging(any()) }
        }
    }

    @Test
    fun locationDialogDismissed_turnsGeoTaggingOff() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(permissionDialog = AppPermission.LOCATION) }

            viewModel.onAction(PermissionAction.DialogDismissed(AppPermission.LOCATION))

            verify(exactly = 1) { settingsDelegate.setGeoTagging(false) }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun settingsClicked_onTheLocationDialog_turnsGeoTaggingOff() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(permissionDialog = AppPermission.LOCATION) }

            viewModel.onAction(PermissionAction.SettingsClicked)

            verify(exactly = 1) { settingsDelegate.setGeoTagging(false) }
        }
    }

    @Test
    fun enableLocationClicked_opensTheLocationSettings() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(SettingsAction.EnableLocationClicked)

            assertEquals(listOf(ViewfinderScreenEffect.OpenLocationSettings), effects)
        }
    }
}
