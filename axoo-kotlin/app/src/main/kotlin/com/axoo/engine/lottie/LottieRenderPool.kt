package com.axoo.engine.lottie

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher

private const val DEFAULT_THREAD_COUNT = 2
private const val MAX_THREAD_COUNT = 4

private val threadId = AtomicInteger()

@Volatile
private var configuredThreadCount = DEFAULT_THREAD_COUNT

// Pool nativo limitato a 1–4 thread; gli adattatori UI non creano altri worker.
private val renderExecutor = ThreadPoolExecutor(
    DEFAULT_THREAD_COUNT,
    DEFAULT_THREAD_COUNT,
    0L, TimeUnit.MILLISECONDS,
    LinkedBlockingQueue(),
    { task ->
        Thread(task, "rlottie-render-${threadId.incrementAndGet()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    },
)

internal val RLottieRenderPool = renderExecutor.asCoroutineDispatcher()

fun configureRLottieRenderPool(threadCount: Int) {
    val target = threadCount.coerceIn(1, MAX_THREAD_COUNT)
    if (target == configuredThreadCount) return

    // Il massimo deve restare >= al minimo durante entrambe le assegnazioni.
    if (target > renderExecutor.corePoolSize) {
        renderExecutor.maximumPoolSize = target
        renderExecutor.corePoolSize = target
    } else {
        renderExecutor.corePoolSize = target
        renderExecutor.maximumPoolSize = target
    }
    configuredThreadCount = target
}
