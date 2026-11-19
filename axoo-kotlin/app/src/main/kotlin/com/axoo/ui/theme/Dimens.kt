/*
 * AxooDimens
 *
 * Extension functions per gestire le densità dello schermo.
 * Qui il trucco è evitare costanti magiche sparse nel codice. Invece di allocare oggetti
 * o usare `TypedValue.applyDimension` (che internamente fa un po' di acrobazie), 
 * andiamo diretti sulle `displayMetrics`. Unica nota di performance: `resources.displayMetrics`
 * è veloce, ma farci math ad ogni singolo frame di `onDraw` potrebbe sommare. Usare con 
 * saggezza: calcola e cacha le `dp` nei costruttori o nei `onMeasure`, mai in `onDraw`.
 */
package com.axoo.ui.theme

import android.content.Context
import android.util.TypedValue
import android.view.View
import kotlin.math.roundToInt

fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}

fun Context.dp(value: Float): Float {
    return value * resources.displayMetrics.density
}

fun View.dp(value: Int): Int {
    return context.dp(value)
}

fun View.dp(value: Float): Float {
    return context.dp(value)
}

fun Context.sp(value: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics,
    )
}

fun View.sp(value: Float): Float {
    return context.sp(value)
}
