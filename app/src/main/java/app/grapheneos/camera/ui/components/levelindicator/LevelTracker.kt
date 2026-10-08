package app.grapheneos.camera.ui.components.levelindicator

internal class LevelTracker(
    initialZone: LevelZone,
) {

    private var isArmed = initialZone != LevelZone.Level

    fun reachesLevel(zone: LevelZone): Boolean {
        val reaches = isArmed && zone == LevelZone.Level

        when {
            reaches -> isArmed = false
            zone == LevelZone.Away -> isArmed = true
        }

        return reaches
    }
}
