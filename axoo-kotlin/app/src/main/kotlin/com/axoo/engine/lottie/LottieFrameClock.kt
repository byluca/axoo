package com.axoo.engine.lottie

import android.os.Looper
import android.view.Choreographer

/** Distribuisce i tick VSync ai listener e si sospende in background. */
internal object LottieFrameClock {

    fun interface Listener {
        fun onFrame(frameTimeNanos: Long)
    }

    private val listeners = LinkedHashSet<Listener>()
    // I callback possono rimuovere listener durante la notifica.
    private val dispatchSnapshot = ArrayList<Listener>()

    private var callbackPosted = false
    private var appInForeground = true

    internal val isInForeground: Boolean
        get() = appInForeground

    internal fun diagnosticState(listener: Listener): String =
        "foreground=$appInForeground registered=${listener in listeners} callbackPosted=$callbackPosted"

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        callbackPosted = false
        if (listeners.isEmpty()) return@FrameCallback

        dispatchSnapshot.clear()
        dispatchSnapshot.addAll(listeners)

        for (index in dispatchSnapshot.indices) {
            val listener = dispatchSnapshot[index]
            if (listener in listeners) {
                listener.onFrame(frameTimeNanos)
            }
        }

        dispatchSnapshot.clear()

        postIfNeeded()
    }

    fun register(listener: Listener) {
        checkMainThread()
        if (!listeners.add(listener)) return
        postIfNeeded()
    }

    fun unregister(listener: Listener) {
        checkMainThread()
        listeners.remove(listener)

        if (listeners.isEmpty() && callbackPosted) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            callbackPosted = false
        }
    }

    fun setAppInForeground(value: Boolean) {
        checkMainThread()
        if (appInForeground == value) return
        appInForeground = value

        if (!value && callbackPosted) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            callbackPosted = false
        }
        if (value) postIfNeeded()
    }

    private fun postIfNeeded() {
        if (!appInForeground || listeners.isEmpty() || callbackPosted) return
        callbackPosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "LottieFrameClock deve essere modificato dal thread principale"
        }
    }
}
