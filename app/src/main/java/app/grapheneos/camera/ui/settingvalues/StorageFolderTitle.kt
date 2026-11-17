package app.grapheneos.camera.ui.settingvalues

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.grapheneos.camera.R
import app.grapheneos.camera.data.media.model.StorageFolder

@Composable
internal fun storageFolderTitle(folder: StorageFolder): String {
    return when (folder) {
        is StorageFolder.MainStorage -> "${stringResource(R.string.main_storage)}/${folder.path}"
        is StorageFolder.Volume -> "${folder.name}/${folder.path}"
        is StorageFolder.Document -> folder.name
    }
}
