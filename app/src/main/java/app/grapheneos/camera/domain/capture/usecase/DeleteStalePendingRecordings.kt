package app.grapheneos.camera.domain.capture.usecase

import android.util.Log
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

interface DeleteStalePendingRecordings {
    suspend operator fun invoke()
}

// A recording that dies with its process (swipe-away from Recents, OOM kill, crash) never reaches
// the Finalize callback that clears IS_PENDING, so it leaves a half-written file that is invisible
// to the user until MediaProvider expires it a week later, and unplayable in the meantime since it
// has no moov atom. Deleting is the honest outcome. Pending rows are only visible to the app that
// owns them, so this can never reach another app's in-flight write, and the age cutoff keeps it
// clear of a recording that is still being muxed.
internal class DeleteStalePendingRecordingsImpl @Inject constructor(
    private val captureOutputRepository: CaptureOutputRepository,
) : DeleteStalePendingRecordings {

    override suspend fun invoke() {
        try {
            captureOutputRepository.deleteStalePendingVideos(olderThan = STALE_RECORDING_AGE)
        } catch (e: IOException) {
            Log.w(TAG, "unable to delete the stale pending recordings", e)
        }
    }

    private companion object {
        private const val TAG = "DeleteStalePendingRecordings"

        private val STALE_RECORDING_AGE = 1.hours
    }
}
