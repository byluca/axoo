package com.axoo.ui.components

import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import com.axoo.R
import com.google.android.material.snackbar.Snackbar

/** A standard message attached to the screen's window, including editor dialogs. */
internal fun View.showMessage(message: String) {
    if (!isAttachedToWindow) return
    Snackbar.make(ContextThemeWrapper(context, R.style.Theme_Axoo_Controls),
        this, message, Snackbar.LENGTH_LONG).show()
}
