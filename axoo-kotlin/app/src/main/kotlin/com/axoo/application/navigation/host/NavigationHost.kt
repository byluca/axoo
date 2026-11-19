/**
 * Gestore delle viste a schermo intero.
 * Esegue fisicamente il cambio tra le schermate e applica le animazioni di transizione (swipe).
 */
package com.axoo.application.navigation.host

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.axoo.BuildConfig
import com.axoo.application.navigation.factory.Navigator
import com.axoo.application.navigation.factory.ScreenFactory
import com.axoo.application.navigation.route.NativeScreen
import com.axoo.application.navigation.route.NavigationSnapshot
import com.axoo.application.navigation.route.Route
import com.axoo.application.navigation.route.Tab
import com.axoo.application.navigation.stack.NavEntry
import com.axoo.application.navigation.stack.NavStore
import com.axoo.core.anim.ScreenTransition
import com.axoo.core.anim.SlidePageAnimator
import kotlin.math.abs

enum class NavigationState {
    Idle,
    Transitioning,
    InteractiveSwipe,
    SettlingSwipe,
    Restoring,
}

/**
 * Sistema di navigazione fisico basato sulle View.
 *
 * Delega al [NavStore] il mantenimento in memoria delle rotte e crea le View 
 * corrispondenti solo quando sono strettamente necessarie (per visibilità o animazioni),
 * ottimizzando il consumo di RAM.
 */
class NavigationHost(
    context: Context,
    private val screenFactory: ScreenFactory,
    initialSnapshot: NavigationSnapshot? = null,
    initialTab: Tab = Tab.Home,
) : FrameLayout(context), Navigator {
    private val store = NavStore(initialTab = initialTab, snapshot = initialSnapshot)

    private var frontLayer = FrameLayout(context)
    private var backLayer = FrameLayout(context)
    private val transitionAnimator = SlidePageAnimator(this)

    private var maybeStartSwipeBack = false
    private var startedSwipeBack = false
    private var swipePointerId = -1
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipePreviousCreated = false
    private var swipePreviousWasParkedInBackLayer = false
    private var swipePreviousEntry: NavEntry? = null
    private var velocityTracker: VelocityTracker? = null

    private var navigationState: NavigationState = NavigationState.Restoring

    val currentNavigationState: NavigationState
        get() = navigationState

    init {
        addView(frontLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(backLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        showInitialRoute()
    }

    override fun navigate(route: Route) {
        changeScreen(ScreenTransition.Push) { store.push(route) }
    }

    override fun replace(route: Route) {
        changeScreen(ScreenTransition.Replace) { store.replace(route) }
    }

    override fun pop(): Boolean {
        if (!store.canPop()) return false
        return changeScreen(ScreenTransition.Pop) {
            store.pop()
            store.currentEntry
        }
    }

    private fun changeScreen(transition: ScreenTransition, updateStack: () -> NavEntry): Boolean {
        if (!canStartTransition(transition.name)) return false
        navigationState = NavigationState.Transitioning
        try {
            val previous = currentTopView()
            val mounted = materialize(updateStack())
            mountScreen(backLayer, mounted)
            animateLayerTransition(previous, mounted, transition)
            return true
        } catch (error: Throwable) {
            failTransition(transition.name, error)
        }
    }

    override fun navigateTab(tab: Tab, target: Route?) {
        if (!canStartTransition("navigateTab($tab,$target)")) return
        navigationState = NavigationState.Transitioning
        try {
            val previous = currentTopView()
            val previousTab = store.currentTab
            store.switchTab(tab, target)
            val entry = store.currentEntry
            val mounted = materialize(entry)
            val previousScreen = previous as? NativeScreen
            val mountedScreen = mounted as? NativeScreen
            val sameScreen = previous === mounted

            if (!sameScreen && (tab == Tab.Create || previousTab == Tab.Create)) {
                mountScreen(backLayer, mounted)
                animateLayerTransition(previous, mounted,
                    if (tab == Tab.Create) ScreenTransition.Present else ScreenTransition.Dismiss)
                return
            }

            if (!sameScreen) {
                previousScreen?.onScreenTransitionStart(isOpening = false, isBackward = false)
                mountedScreen?.onScreenTransitionStart(isOpening = true, isBackward = false)
            }

            mountScreen(frontLayer, mounted)
            bringChildToFront(frontLayer)
            requestInsetsWhenMounted(mounted)

            if (!sameScreen) {
                previousScreen?.onScreenHidden()
            }
            mountedScreen?.onScreenFullyVisible()
            navigationState = NavigationState.Idle
            assertEntriesValid("after navigateTab")
        } catch (error: Throwable) {
            failTransition("navigateTab($tab,$target)", error)
        }
    }

    fun onBackPressed(): Boolean {
        val top = currentTopView()
        if (top is BackHandler && top.onBackPressed()) return true
        return pop()
    }

    fun saveState(): NavigationSnapshot = store.snapshot()

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        handleSwipeBackTouch(event, interceptOnly = true) || super.onInterceptTouchEvent(event)

    override fun onTouchEvent(event: MotionEvent): Boolean =
        handleSwipeBackTouch(event, interceptOnly = false) || super.onTouchEvent(event)

    private fun showInitialRoute() {
        val mounted = materialize(store.currentEntry)
        mountScreen(frontLayer, mounted)
        bringChildToFront(frontLayer)
        requestInsetsWhenMounted(mounted)
        (mounted as? NativeScreen)?.onScreenFullyVisible()
        navigationState = NavigationState.Idle
        assertEntriesValid("after showInitialRoute")
    }

    private fun animateLayerTransition(
        previousScreen: View?,
        mountedScreen: View,
        transition: ScreenTransition,
    ) {
        val oldFront = frontLayer
        val newFront = backLayer
        val previousNativeScreen = previousScreen as? NativeScreen
        val mountedNativeScreen = mountedScreen as? NativeScreen
        val isBackward = transition == ScreenTransition.Pop || transition == ScreenTransition.Dismiss
        val differentScreen = previousScreen !== mountedScreen

        if (differentScreen) {
            previousNativeScreen?.onScreenTransitionStart(isOpening = false, isBackward)
        }
        transitionAnimator.replace(
            oldView = oldFront,
            newView = newFront,
            transition = transition,
            onMounted = {
                if (differentScreen) {
                    mountedNativeScreen?.onScreenTransitionStart(isOpening = true, isBackward)
                }
                requestInsetsWhenMounted(mountedScreen)
            },
            onFinished = {
                try {
                    if (differentScreen) {
                        previousNativeScreen?.onScreenHidden()
                    }
                    if (transition == ScreenTransition.Pop || transition == ScreenTransition.Replace) {
                        previousNativeScreen?.onScreenDestroyed()
                        oldFront.removeAllViews()
                    }
                    frontLayer = newFront
                    backLayer = oldFront
                    bringChildToFront(frontLayer)
                    mountedNativeScreen?.onScreenFullyVisible()
                } finally {
                    navigationState = NavigationState.Idle
                    assertEntriesValid("after $transition")
                }
            },
        )
    }

    private fun materialize(entry: NavEntry): View {
        entry.view?.let { return it }
        return screenFactory.create(context, entry.route, this).also { entry.view = it }
    }

    private fun mountScreen(layer: FrameLayout, view: View) {
        if (view.parent === layer) return
        layer.removeAllViews()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        layer.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ViewCompat.getRootWindowInsets(this)?.let { ViewCompat.dispatchApplyWindowInsets(view, it) }
        ViewCompat.requestApplyInsets(view)
    }

    private fun requestInsetsWhenMounted(view: View) {
        view.post {
            ViewCompat.requestApplyInsets(view)
            ViewCompat.requestApplyInsets(this)
        }
    }

    private fun currentTopView(): View? =
        store.currentEntry.view ?: transitionAnimator.currentTopView

    private fun canStartTransition(reason: String): Boolean {
        if (navigationState == NavigationState.Idle) return true
        Log.d(NAV_TAG, "skip $reason while state=$navigationState")
        return false
    }

    private fun failTransition(operation: String, error: Throwable): Nothing {
        Log.e(NAV_TAG, "$operation failed; resetting state", error)
        navigationState = NavigationState.Idle
        throw error
    }

    private fun assertEntriesValid(reason: String) {
        if (!BuildConfig.DEBUG) return
        Tab.entries.forEach { tab ->
            val entries = store.entries(tab)
            check(entries.isNotEmpty()) { "$reason: empty stack for $tab" }
            entries.forEachIndexed { index, entry ->
                val screen = entry.view as? NativeScreen ?: return@forEachIndexed
                check(screen.route == entry.route) {
                    "$reason: route mismatch $tab[$index] entry=${entry.route} view=${screen.route}"
                }
            }
        }
    }

    private fun handleSwipeBackTouch(event: MotionEvent, interceptOnly: Boolean): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetSwipeTracker()
                if (!canBeginSwipeBack() || event.x > swipeEdgeWidthPx()) return false
                maybeStartSwipeBack = true
                swipePointerId = event.getPointerId(0)
                swipeStartX = event.x
                swipeStartY = event.y
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(swipePointerId)
                if (pointerIndex < 0) return false
                velocityTracker?.addMovement(event)
                val deltaX = (event.getX(pointerIndex) - swipeStartX).coerceAtLeast(0f)
                val deltaY = abs(event.getY(pointerIndex) - swipeStartY)
                if (maybeStartSwipeBack && !startedSwipeBack &&
                    deltaX >= swipeStartSlopPx() &&
                    deltaX / 3f > deltaY
                ) {
                    if (!prepareSwipeBack()) {
                        resetSwipeTracker()
                        return false
                    }
                    maybeStartSwipeBack = false
                    startedSwipeBack = true
                }
                if (startedSwipeBack) {
                    transitionAnimator.updateInteractivePop(deltaX)
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP,
            -> {
                if (!startedSwipeBack) {
                    resetSwipeTracker()
                    return false
                }
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityX = velocityTracker?.xVelocity ?: 0f
                val velocityY = velocityTracker?.yVelocity ?: 0f
                val deltaX = frontLayer.translationX.coerceAtLeast(0f)
                val shouldFinish = event.actionMasked != MotionEvent.ACTION_CANCEL &&
                    (deltaX >= width / 3f || (velocityX >= 1400f && velocityX > abs(velocityY)))
                if (shouldFinish) finishSwipeBack() else cancelSwipeBack()
                return true
            }
        }
        return startedSwipeBack && !interceptOnly
    }

    private fun canBeginSwipeBack(): Boolean =
        navigationState == NavigationState.Idle &&
            store.canPop() &&
            currentTopView() != null

    private fun prepareSwipeBack(): Boolean {
        if (navigationState != NavigationState.Idle) return false
        val current = currentTopView() ?: return false
        val previousEntry = store.previousEntry() ?: return false
        navigationState = NavigationState.InteractiveSwipe
        try {
            swipePreviousCreated = previousEntry.view == null
            val previous = materialize(previousEntry)
            swipePreviousEntry = previousEntry
            swipePreviousWasParkedInBackLayer = previous.parent === backLayer
            mountScreen(backLayer, previous)
            bringChildToFront(frontLayer)
            (current as? NativeScreen)?.onInteractiveTransitionStart(isOpening = false)
            (previous as? NativeScreen)?.onInteractiveTransitionStart(isOpening = true)
            transitionAnimator.beginInteractivePop(
                currentView = frontLayer,
                previousView = backLayer,
            )
            return true
        } catch (error: Throwable) {
            resetSwipeTracker()
            failTransition("prepareSwipeBack", error)
        }
    }

    private fun finishSwipeBack() {
        val current = currentTopView()
        val previous = swipePreviousEntry?.view
        navigationState = NavigationState.SettlingSwipe
        transitionAnimator.finishInteractivePop {
            try {
                store.pop()
                (current as? NativeScreen)?.onScreenHidden()
                (current as? NativeScreen)?.onScreenDestroyed()
                val oldFront = frontLayer
                frontLayer = backLayer
                backLayer = oldFront
                backLayer.removeAllViews()
                bringChildToFront(frontLayer)
                (previous as? NativeScreen)?.onScreenFullyVisible()
            } finally {
                resetSwipeTracker()
                navigationState = NavigationState.Idle
                assertEntriesValid("after finishSwipeBack")
            }
        }
    }

    private fun cancelSwipeBack() {
        val previousEntry = swipePreviousEntry
        val previous = previousEntry?.view
        val previousWasCreated = swipePreviousCreated
        val previousWasParked = swipePreviousWasParkedInBackLayer
        val current = currentTopView()
        navigationState = NavigationState.SettlingSwipe
        transitionAnimator.cancelInteractivePop {
            try {
                (previous as? NativeScreen)?.onScreenHidden()
                if (previousWasCreated && previous != null) {
                    (previous as? NativeScreen)?.onScreenDestroyed()
                    previousEntry.view = null
                    backLayer.removeAllViews()
                } else if (!previousWasParked) {
                    backLayer.removeAllViews()
                }
                bringChildToFront(frontLayer)
                (current as? NativeScreen)?.onScreenFullyVisible()
            } finally {
                resetSwipeTracker()
                navigationState = NavigationState.Idle
                assertEntriesValid("after cancelSwipeBack")
            }
        }
    }

    private fun resetSwipeTracker() {
        maybeStartSwipeBack = false
        startedSwipeBack = false
        swipePointerId = -1
        swipeStartX = 0f
        swipeStartY = 0f
        swipePreviousCreated = false
        swipePreviousWasParkedInBackLayer = false
        swipePreviousEntry = null
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun swipeEdgeWidthPx(): Float = 24f * resources.displayMetrics.density

    private fun swipeStartSlopPx(): Float = 10f * resources.displayMetrics.density

    private companion object {
        const val NAV_TAG = "AxooNavHost"
    }
}
