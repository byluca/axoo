/**
 * Gestore degli elementi in sovrimpressione.
 * Mantiene Bottom Sheet e popup sempre in primo piano e li chiude quando si preme Indietro.
 */
package com.axoo.application.navigation.host

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.axoo.ui.sheets.DismissibleOverlay
import com.axoo.ui.sheets.BottomSheetContainer

@SuppressLint("ViewConstructor")
class OverlayHost(context: Context) : FrameLayout(context) {

    private val overlayStack = ArrayDeque<DismissibleOverlay>()

    init {
        isClickable = false
    }

    fun <T> presentOverlay(overlay: T) where T : View, T : DismissibleOverlay {
        if (overlay.parent === this) return
        overlayStack.addLast(overlay)
        overlay.addOnDismissedListener {
            overlayStack.remove(overlay)
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.show()
    }

    fun dismissTopSheet(): Boolean {
        val top = overlayStack.lastOrNull() ?: return false
        top.dismiss()
        return true
    }

    companion object {
        /**
         * Le bottom sheet usano una finestra Material, che gestisce anche Indietro.
         * L'OverlayHost resta riservato agli overlay non-dialog.
         */
        fun presentSheetOn(anchor: View, sheet: BottomSheetContainer) {
            sheet.presentOn(anchor)
        }

        fun <T> presentOverlayOn(anchor: View, overlay: T): Boolean
            where T : View, T : DismissibleOverlay {
            val host = findAxooOverlayHost(anchor)
            if (host != null) {
                host.presentOverlay(overlay)
                return true
            }
            val fallback = anchor.context.asActivity()?.findViewById<ViewGroup>(android.R.id.content)
                ?: return false
            overlay.addOnDismissedListener {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }
            fallback.addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            overlay.show()
            return true
        }

        private fun findAxooOverlayHost(view: View): OverlayHost? {
            var current: View? = view
            while (current != null) {
                if (current is OverlayHost) return current
                if (current is AppRootView) return current.overlayHost
                current = current.parent as? View
            }
            return null
        }

        private fun Context.asActivity(): Activity? {
            var ctx: Context? = this
            while (ctx is ContextWrapper) {
                if (ctx is Activity) return ctx
                ctx = ctx.baseContext
            }
            return null
        }
    }
}
