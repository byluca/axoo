/*
 * BaseRecyclerView
 *
 * Lista ottimizzata per massimizzare gli FPS:
 * 1. clipToPadding = false: scrolling fluido anche fuori dai bordi.
 * 2. Usa l'overscroll nativo anche quando il contenuto entra nello schermo.
 * 3. itemAnimator = null: spegne animazioni inutili per risparmiare memoria.
 */
package com.axoo.engine.emoji.list

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView

open class BaseRecyclerView(context: Context) : RecyclerView(context, null, 0) {
    init {
        clipToPadding = false
        overScrollMode = View.OVER_SCROLL_ALWAYS
        itemAnimator = null
        setHasFixedSize(false)
    }

    fun setAxooPadding(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ) {
        setPadding(left, top, right, bottom)
    }
}
