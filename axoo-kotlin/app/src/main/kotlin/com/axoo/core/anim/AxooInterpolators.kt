package com.axoo.core.anim

import android.animation.TimeInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.exp
import kotlin.math.sqrt

object AxooInterpolators {
    val Linear: TimeInterpolator = LinearInterpolator()

    val Decelerate: TimeInterpolator = PathInterpolator(0f, 0f, 0.2f, 1f)

    val Enter: TimeInterpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)

    val Exit: TimeInterpolator = PathInterpolator(0.4f, 0f, 1f, 1f)

    /** Spring criticamente smorzata: veloce, naturale e senza rimbalzo. */
    val NoBounceSpring: TimeInterpolator = TimeInterpolator { progress ->
        val omega = sqrt(NO_BOUNCE_SPRING_STIFFNESS)
        val durationSeconds = NO_BOUNCE_SPRING_DURATION_MS / 1_000f
        val time = progress * durationSeconds
        val value = 1f - (1f + omega * time) * exp(-omega * time)
        val endValue = 1f -
            (1f + omega * durationSeconds) * exp(-omega * durationSeconds)
        (value / endValue).coerceIn(0f, 1f)
    }

    const val NoBounceSpringDurationMs = 320L

    private const val NO_BOUNCE_SPRING_STIFFNESS = 550f
    private const val NO_BOUNCE_SPRING_DURATION_MS = 320L
}
