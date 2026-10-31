// SPDX-License-Identifier: MIT
package app.grapheneos.camera.ui.activities

/**
 * Identities for asynchronous WindowArea requests. Every cancellation advances the
 * generation; callbacks from any older request must be ignored (and their sessions
 * closed) rather than acting on the currently selected camera mode.
 *
 * Confined to the main thread by the WindowArea controller's main executor.
 */
internal class FoldRequestGeneration {
    private var generation = 0L

    fun begin(): Long = ++generation

    fun cancel() {
        generation++
    }

    fun isCurrent(request: Long): Boolean = request == generation
}
