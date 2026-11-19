package com.axoo.features.posts.presentation.components

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axoo.features.posts.domain.VoteState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoteScoreAnimationTest {
    @Test fun rebindingAnotherItemResetsAnimationState() = onMain {
        val score = VoteScoreAnimation { }
        score.update(10, VoteState.None, false)
        score.update(11, VoteState.Upvote, true)
        score.update(50, VoteState.Downvote, false)
        assertEquals(50, score.oldScore)
        assertEquals(50, score.currentScore)
        assertEquals(VoteState.Downvote, score.currentVoteState)
        assertEquals(1f, score.progress, 0f)
        score.cancel()
    }
    @Test fun consecutiveVotesStartFromTheLatestScore() = onMain {
        val score = VoteScoreAnimation { }
        score.update(10, VoteState.None, false)
        score.update(11, VoteState.Upvote, true)
        score.update(9, VoteState.Downvote, true)
        assertEquals(11, score.oldScore)
        assertEquals(9, score.currentScore)
        assertEquals(VoteState.Upvote, score.oldVoteState)
        assertEquals(VoteState.Downvote, score.currentVoteState)
        score.cancel()
    }
    private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
}
