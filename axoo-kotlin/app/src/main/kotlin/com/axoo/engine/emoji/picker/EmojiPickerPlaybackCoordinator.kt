package com.axoo.engine.emoji.picker

import com.axoo.engine.emoji.animation.time.PlaybackPolicy
import com.axoo.engine.emoji.list.EmojiRecyclerView

/**
 * Applica PlaybackPolicy alle celle visibili e gestisce play/pausa col ciclo di vita del picker.
 * Scagliona gli avvii per distribuire il lavoro su più frame.
 */
internal class EmojiPickerPlaybackCoordinator(
    private val recyclerView: EmojiRecyclerView,
) {
    companion object {
        private const val START_BATCH = 3
        private const val START_INTERVAL_MS = 48L
        private const val MAX_CONCURRENT_ANIMATIONS = 24
    }

    private var adapter: EmojiAdapter? = null
    private val activeIds = LinkedHashSet<Long>()
    private val configuration = PlaybackPolicy.Configuration(
        maxConcurrent = MAX_CONCURRENT_ANIMATIONS,
    )
    private var attached = false
    private var pickerOpen = false
    private var reconcilePosted = false
    private var cascadePosted = false

    private val reconcileRunnable = Runnable {
        reconcilePosted = false
        reconcile()
    }

    private val cascadeRunnable = Runnable {
        cascadePosted = false
        reconcile()
    }

    fun bindAdapter(value: EmojiAdapter?) {
        if (adapter === value) return
        adapter?.pauseExcept(emptySet())
        activeIds.clear()
        adapter = value
        markDirty()
    }

    fun attach() {
        if (attached) return
        attached = true
    }

    fun detach() {
        if (!attached) return
        attached = false
        pickerOpen = false
        stopCallbacks()
        pauseAll()
    }

    fun onPickerOpen() {
        if (!attached || pickerOpen) return
        pickerOpen = true
        markDirty()
    }

    fun onPickerClose() {
        if (!pickerOpen) return
        pickerOpen = false
        stopCallbacks()
        pauseAll()
    }

    fun onScrollStateChanged() {
        if (recyclerView.playbackAllowed) {
            markDirty()
        } else {
            onPlaybackDisallowed()
        }
    }

    fun onPlaybackDisallowed() {
        stopCascade()
        pauseAll()
    }

    fun markDirty() {
        if (!attached || !pickerOpen || !recyclerView.playbackAllowed || reconcilePosted) return
        reconcilePosted = true
        recyclerView.postOnAnimation(reconcileRunnable)
    }

    private fun reconcile() {
        val adapter = adapter ?: return
        if (!attached || !pickerOpen || !recyclerView.playbackAllowed) {
            pauseAll()
            return
        }

        val decision = PlaybackPolicy.decide(
            candidates = adapter.collectPlaybackCandidates(),
            currentActiveIds = activeIds,
            playbackAllowed = recyclerView.playbackAllowed,
            configuration = configuration,
            viewportCenterY = viewportCenterY(),
        )

        adapter.pauseExcept(decision.desiredIds)
        activeIds.retainAll(decision.desiredIds)

        var started = 0
        for (snapshot in decision.toStart) {
            if (started >= START_BATCH) break
            if (adapter.startCandidate(snapshot)) {
                activeIds.add(snapshot.id)
                started += 1
            }
        }

        val pending = decision.desiredIds.any { it !in activeIds }
        if (pending && started > 0) postCascade()
    }

    private fun pauseAll() {
        adapter?.pauseExcept(emptySet())
        activeIds.clear()
    }

    private fun postCascade() {
        if (cascadePosted) return
        cascadePosted = true
        recyclerView.postDelayed(cascadeRunnable, START_INTERVAL_MS)
    }

    private fun stopCascade() {
        if (!cascadePosted) return
        cascadePosted = false
        recyclerView.removeCallbacks(cascadeRunnable)
    }

    private fun stopCallbacks() {
        if (reconcilePosted) {
            reconcilePosted = false
            recyclerView.removeCallbacks(reconcileRunnable)
        }
        stopCascade()
    }

    private fun viewportCenterY(): Float {
        val top = recyclerView.paddingTop
        val bottom = recyclerView.height - recyclerView.paddingBottom
        return (top + bottom) / 2f
    }
}
