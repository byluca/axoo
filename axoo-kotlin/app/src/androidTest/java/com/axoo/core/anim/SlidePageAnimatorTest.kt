package com.axoo.core.anim

import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SlidePageAnimatorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private class Fixture(context: android.content.Context) {
        val container = FrameLayout(context)
        val previous = View(context)
        val current = View(context)
        val animator = SlidePageAnimator(container)

        init {
            container.addView(previous)
            container.addView(current)
            container.measure(exact(1000), exact(1600))
            container.layout(0, 0, 1000, 1600)
        }

        fun assertFinished(top: View) {
            assertFalse(animator.isTransitionInProgress)
            assertFalse(animator.isInteractivePopInProgress)
            assertSame(top, animator.currentTopView)
            assertSame(top, container.getChildAt(container.childCount - 1))
            assertEquals(2, container.childCount)
            for (page in listOf(previous, current)) {
                assertSame(container, page.parent)
                assertEquals(0f, page.translationX, 0f)
                assertEquals(1f, page.alpha, 0f)
                assertEquals(View.LAYER_TYPE_NONE, page.layerType)
                assertFalse(page.clipToOutline)
                assertSame(ViewOutlineProvider.BACKGROUND, page.outlineProvider)
            }
        }

        companion object {
            private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        }
    }

    @Test
    fun swipeScrimTracksTheExposedPageAndClampsOutOfBoundsDragging() {
        onMain {
            val f = Fixture(instrumentation.targetContext)
            f.animator.beginInteractivePop(f.current, f.previous)
            f.animator.updateInteractivePop(500f)
            assertEquals(500f, f.current.translationX, 0f)
            assertEquals(-90f, f.previous.translationX, 0.001f)
            val scrim = f.container.getChildAt(1)
            assertSame(f.current, f.container.getChildAt(2))
            val radius = 24f * f.container.resources.displayMetrics.density
            assertEquals(Rect(0, 0, (500 + radius).toInt(), 1600), scrim.clipBounds)
            assertTrue(f.current.clipToOutline)
            assertFalse(f.previous.clipToOutline)
            val outline = Outline()
            f.current.outlineProvider.getOutline(f.current, outline)
            assertEquals(radius, outline.radius, 0f)
            val bounds = Rect()
            assertTrue(outline.getRect(bounds))
            assertEquals(Rect(0, 0, f.current.width, f.current.height), bounds)
            assertEquals(60f / 255f, scrim.alpha, 0.001f)
            assertFalse(scrim.isClickable)

            f.animator.updateInteractivePop(-100f)
            assertEquals(0f, f.current.translationX, 0f)
            assertFalse(f.current.clipToOutline)
            assertEquals(96f / 255f, scrim.alpha, 0.001f)
            f.animator.updateInteractivePop(1200f)
            assertEquals(1000f, f.current.translationX, 0f)
            assertEquals(0f, scrim.alpha, 0f)
            f.animator.replace(f.current, f.previous, ScreenTransition.None, onMounted = {})
            f.assertFinished(f.previous)
        }
    }

    @Test
    fun cancellingSwipeKeepsCurrentPageAndAllowsAnotherSwipe() {
        val done = CountDownLatch(1)
        lateinit var f: Fixture
        onMain {
            f = Fixture(instrumentation.targetContext)
            f.animator.beginInteractivePop(f.current, f.previous)
            f.animator.updateInteractivePop(600f)
            f.animator.cancelInteractivePop { done.countDown() }
        }
        await(done)
        onMain {
            f.assertFinished(f.current)
            f.animator.beginInteractivePop(f.current, f.previous)
            assertEquals(0f, f.current.translationX, 0f)
            f.animator.replace(f.previous, f.current, ScreenTransition.None, onMounted = {})
            f.assertFinished(f.current)
        }
    }

    @Test
    fun completingSwipeRevealsPreviousPageWithoutRemovingNavigationLayers() {
        val done = CountDownLatch(1)
        lateinit var f: Fixture
        onMain {
            f = Fixture(instrumentation.targetContext)
            f.animator.beginInteractivePop(f.current, f.previous)
            f.animator.updateInteractivePop(400f)
            f.animator.finishInteractivePop { done.countDown() }
        }
        await(done)
        onMain { f.assertFinished(f.previous) }
    }

    @Test
    fun automaticTransitionsMountThenFinishOnceAndReleaseTheirLayers() {
        for (kind in listOf(ScreenTransition.None, ScreenTransition.Push, ScreenTransition.Pop, ScreenTransition.Replace)) {
            val done = CountDownLatch(1)
            val callbacks = mutableListOf<String>()
            lateinit var f: Fixture
            onMain {
                f = Fixture(instrumentation.targetContext)
                f.animator.replace(f.current, f.previous, kind,
                    onMounted = { callbacks += "mounted" },
                    onFinished = { callbacks += "finished"; done.countDown() },
                )
            }
            await(done)
            onMain {
                assertEquals(listOf("mounted", "finished"), callbacks)
                f.assertFinished(f.previous)
            }
        }
    }

    @Test
    fun supersededAnimationCannotFinishOverTheNewPage() {
        val done = CountDownLatch(1)
        var staleCompletions = 0
        lateinit var f: Fixture
        onMain {
            f = Fixture(instrumentation.targetContext)
            f.animator.replace(f.current, f.previous, ScreenTransition.Push,
                onMounted = {}, onFinished = { staleCompletions++ },
            )
            f.animator.replace(f.previous, f.current, ScreenTransition.Replace,
                onMounted = {}, onFinished = { done.countDown() },
            )
        }
        await(done)
        onMain {
            assertEquals(0, staleCompletions)
            f.assertFinished(f.current)
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(latch: CountDownLatch) {
        assertTrue("Transition did not finish", latch.await(5, TimeUnit.SECONDS))
    }
}
