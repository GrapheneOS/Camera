package app.grapheneos.camera.ui.common.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

internal val WindowInsets.Companion.topBar: WindowInsets
    @Composable
    get() {
        return systemBarsAndCutout(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    }

internal val WindowInsets.Companion.bottomBar: WindowInsets
    @Composable
    get() {
        return systemBarsAndCutout(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WindowInsets.Companion.systemBarsAndCutout(
    sides: WindowInsetsSides,
): WindowInsets {
    return systemBarsIgnoringVisibility
        .union(displayCutout)
        .only(sides)
}
