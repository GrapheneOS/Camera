package app.grapheneos.camera.ui.gallery.components.pager

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable

internal const val CAMERA_PAGE = 0
internal const val FIRST_ITEM_PAGE = 1

internal val PagerState.settledItemIndex: Int
    get() = settledPage - FIRST_ITEM_PAGE

internal val PagerState.cameraPageProgress: Float
    get() = when (pageCount) {
        0 -> 0f
        else -> (FIRST_ITEM_PAGE - currentPage - currentPageOffsetFraction).coerceIn(0f, 1f)
    }

@Composable
internal fun rememberGalleryPagerState(
    currentPage: Int,
    itemCount: () -> Int,
): PagerState {
    return rememberPagerState(
        initialPage = currentPage + FIRST_ITEM_PAGE,
        pageCount = {
            when (val count = itemCount()) {
                0 -> 0
                else -> count + FIRST_ITEM_PAGE
            }
        },
    )
}
