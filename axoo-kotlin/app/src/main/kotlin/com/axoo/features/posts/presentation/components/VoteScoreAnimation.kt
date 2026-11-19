package com.axoo.features.posts.presentation.components

import android.animation.ValueAnimator
import com.axoo.core.anim.AxooInterpolators
import com.axoo.features.posts.domain.VoteState

internal class VoteScoreAnimation(private val invalidate: () -> Unit) {
    var oldScore = 0; private set
    var currentScore = 0; private set
    var oldVoteState = VoteState.None; private set
    var currentVoteState = VoteState.None; private set
    var progress = 1f; private set
    private var animator: ValueAnimator? = null

    fun update(nextScore: Int, nextVote: VoteState, sameItem: Boolean) {
        if (!sameItem) {
            cancel()
            oldScore = nextScore; currentScore = nextScore
            oldVoteState = nextVote; currentVoteState = nextVote
            progress = 1f
            return
        }
        if (nextScore == currentScore && nextVote == currentVoteState) return
        oldScore = currentScore; currentScore = nextScore
        oldVoteState = currentVoteState; currentVoteState = nextVote
        cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = AxooInterpolators.Decelerate
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    fun cancel() { animator?.cancel(); animator = null }
}
