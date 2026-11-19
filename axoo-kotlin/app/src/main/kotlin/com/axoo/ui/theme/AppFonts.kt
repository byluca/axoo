package com.axoo.ui.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.axoo.R

// Il Guardaroba dei font dell'app. Contiene il motore per recuperare i font dal telefono
// e le scorciatoie per applicarli comodamente ai testi.

// Il menù ufficiale delle famiglie di font disponibili in Axoo.
enum class AppFontFamily {
    BALOO2,
    INTER,
}

// 1. IL MOTORE PRINCIPALE
// Abbiamo "insegnato" al Guardiano (Context) come recuperare un font (Typeface).
fun Context.axooTypeface(family: AppFontFamily, weight: Int): Typeface {
    
    // In base alla Famiglia e allo Spessore (weight), troviamo il "numero di serie" (ID) del file corretto.
    val font = when (family) {
        AppFontFamily.BALOO2 -> when {
            weight <= 450 -> R.font.baloo2_regular
            weight <= 550 -> R.font.baloo2_medium
            weight <= 650 -> R.font.baloo2_semi_bold
            weight <= 750 -> R.font.baloo2_bold
            else -> R.font.baloo2_extra_bold
        }
        AppFontFamily.INTER -> when {
            weight <= 450 -> R.font.inter_regular
            weight <= 550 -> R.font.inter_medium
            weight <= 650 -> R.font.inter_semi_bold
            weight <= 750 -> R.font.inter_bold
            else -> R.font.inter_black
        }
    }
    
    // Il Piano B: se il file è corrotto, usiamo un font di sistema di emergenza per non far crashare l'app.
    val fallback = if (family == AppFontFamily.BALOO2) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    
    // Il Guardiano (che qui si chiama 'this') entra nello sgabuzzino delle risorse, 
    // prende il file e lo spacchetta in un Typeface (l'inchiostro pronto per essere disegnato).
    return ResourcesCompat.getFont(this, font) ?: fallback
}

// 2. I TASTI RAPIDI
// Scorciatoie per i pigri: passano in automatico la famiglia corretta al Motore Principale.
fun Context.baloo2Typeface(weight: Int): Typeface = axooTypeface(AppFontFamily.BALOO2, weight)
fun Context.interTypeface(weight: Int): Typeface = axooTypeface(AppFontFamily.INTER, weight)

// 3. I TRUCCHI DI STILE (Extension Functions)
// Insegniamo alle View (i componenti grafici) come auto-vestirsi.

// La TextView (L'attore) ha già il Guardiano (Context) nella sua tasca. 
// Le diciamo semplicemente di chiamarlo, farsi dare il font, e indossarlo (typeface = ...).
fun TextView.applyAxooTypography(family: AppFontFamily, weight: Int) {
    typeface = context.axooTypeface(family, weight)
}

// Il Paint (Il Pennello virtuale) invece è uno strumento stupido e NON ha il Guardiano in tasca.
// Per questo siamo costretti a passargli 'context' tra le parentesi, così può usarlo per farsi dare il font.
fun Paint.applyAxooTypography(context: Context, family: AppFontFamily, weight: Int) {
    typeface = context.axooTypeface(family, weight)
}
