package com.axoo.application.navigation.components

/** Converts small scroll deltas into stable show/hide decisions for the bottom navigation. */
internal class BottomNavigationScrollTracker(
    private val thresholdPx: Int,
) {
    var isVisible: Boolean = true
        private set

    private var accumulatedDelta = 0

    /** Returns the new visibility only when the threshold changes it. */
    fun onScrolled(deltaY: Int): Boolean? {
        if (deltaY == 0) return null

        accumulatedDelta = when {
            deltaY > 0 && accumulatedDelta < 0 -> deltaY
            deltaY < 0 && accumulatedDelta > 0 -> deltaY
            else -> accumulatedDelta + deltaY
        }

        val nextVisibility = when {
            isVisible && accumulatedDelta >= thresholdPx -> false
            !isVisible && accumulatedDelta <= -thresholdPx -> true
            else -> return null
        }
        isVisible = nextVisibility
        accumulatedDelta = 0
        return nextVisibility
    }
}
