package com.axoo.engine.emoji.animation.time

import kotlin.math.abs

/**
 * Seleziona le emoji da animare in base a visibilità, posizione e limite di concorrenza.
 */
internal object PlaybackPolicy {

    const val DEFAULT_MIN_VISIBLE_PERMILLE = 300

    /** Stessa soglia iOS: 45 FPS fino a 32 dp, 60 FPS oltre; non cambia il loop. */
    fun maximumFrameRate(sizePx: Int, density: Float): Float =
        if (sizePx <= (32 * density).toInt()) 45f else 60f

    data class Configuration(
        val maxConcurrent: Int,
        val minVisiblePermille: Int = DEFAULT_MIN_VISIBLE_PERMILLE,
    ) {
        init {
            require(maxConcurrent >= 0)
            require(minVisiblePermille in 0..1000)
        }
    }

    data class Candidate<T>(
        val id: Long,
        val value: T,
        val ready: Boolean,
        val visibleAreaPx: Int,
        val visibleCenterY: Float,
        val visiblePermille: Int,
        val top: Int,
        val left: Int,
        val stableOrder: Int,
    )

    data class Decision<T>(
        val desiredIds: Set<Long>,
        val toStart: List<Candidate<T>>,
    )

    fun <T> decide(
        candidates: List<Candidate<T>>,
        currentActiveIds: Set<Long>,
        playbackAllowed: Boolean,
        configuration: Configuration,
        viewportCenterY: Float,
    ): Decision<T> {

        val selected = select(
            candidates = candidates,
            playbackAllowed = playbackAllowed,
            maxConcurrent = configuration.maxConcurrent,
            viewportCenterY = viewportCenterY,
            minVisiblePermille = configuration.minVisiblePermille,
        )

        val desiredIds = selected.mapTo(LinkedHashSet(selected.size)) { it.id }

        // Mantiene l'id del binding per scartare le celle riassociate nel frattempo.
        val toStart = selected
            .filter { it.id !in currentActiveIds }
            .sortedWith(
                compareBy<Candidate<T>> { it.top }
                    .thenBy { it.left }
                    .thenBy { it.stableOrder },
            )

        return Decision(
            desiredIds = desiredIds,
            toStart = toStart,
        )
    }

    private fun <T> select(
        candidates: List<Candidate<T>>,
        playbackAllowed: Boolean,
        maxConcurrent: Int,
        viewportCenterY: Float,
        minVisiblePermille: Int,
    ): List<Candidate<T>> {
        if (!playbackAllowed || maxConcurrent <= 0) return emptyList()

        val eligible = candidates.filter { it.ready && it.visibleAreaPx > 0 }
        if (eligible.isEmpty()) return emptyList()

        val byPlaybackPriority =
            compareByDescending<Candidate<T>> { it.visibleAreaPx }
                .thenBy { abs(it.visibleCenterY - viewportCenterY) }
                .thenBy { it.top }
                .thenBy { it.left }
                .thenBy { it.stableOrder }

        // Le celle sotto la soglia di visibilità occupano solo gli slot rimasti.
        val preferred = eligible
            .filter { it.visiblePermille >= minVisiblePermille }
            .sortedWith(byPlaybackPriority)
        if (preferred.size >= maxConcurrent) return preferred.take(maxConcurrent)

        val partiallyVisible = eligible
            .filter { it.visiblePermille < minVisiblePermille }
            .sortedWith(byPlaybackPriority)

        return (preferred + partiallyVisible).take(maxConcurrent)
    }
}
