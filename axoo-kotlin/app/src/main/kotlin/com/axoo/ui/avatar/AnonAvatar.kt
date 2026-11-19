/*
 * NativeAnonAvatarView e URL Builder
 *
 * Gli avatar anonimi generati proceduralmente sono belli ma hanno un costo: decodificare
 * SVG su Android e trasformarli in Bitmap. Se l'utente scrolla veloce, creare un path
 * e disegnare tutto real-time distrugge il main thread.
 *
 * Qui la soluzione è duplice:
 * 1. Hasher deterministico in puro Kotlin senza allocare oggetti pesanti. Costruiamo l'URL 
 *    per DiceBear calcolando l'hash dei parametri, in modo che per un dato 'seed' avremo
 *    sempre gli stessi tratti facciali. Veloce, nessuna chiamata di rete per scoprire 
 *    i metadata, zero state lato server.
 * 2. L'immagine, una volta arrivata tramite Coil (decodificata asincronamente in un thread IO), 
 *    viene drawata nel Canvas usando un clip oval in hardware. Evitiamo di allocare
 *    una nuova Bitmap circolare per risparmiare memoria (i BitmapHeap on Android < 8
 *    erano un inferno, ora meglio, ma restiamo memory-conscious).
 */
package com.axoo.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.view.View
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.axoo.ui.theme.NativeColors
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DICEBEAR_BASE = "https://api.dicebear.com/10.x/adventurer/svg"

// Unica policy locale per generazione, rendering ed editor dell'avatar.
private val kAvatarHair = listOf(
    "long01","long02","long03","long04","long05","long07","long08","long09",
    "long10","long11","long12","long13","long14","long15","long16",
    "long19","long20","long21","long23","long24","long25","long26",
    "short01","short02","short03","short04","short05","short06","short07","short08",
    "short09","short10","short11","short12","short13","short14","short15","short16",
    "short17","short18","short19",
)
private val kHairColor = listOf("0e0e0e","6a4e35","562306","cb6820","b9a05f","e5d7a3","ac6511","796a45","ab2a18","afafaf","dba3be","85c2c6","3eac2c","592454")
private val kEyes = listOf("variant01","variant02","variant03","variant05","variant12","variant14","variant17","variant20","variant26","variant04","variant06","variant07","variant11","variant13","variant18","variant19","variant21","variant22","variant23","variant24","variant25")
private val kEyebrows = listOf("variant01","variant02","variant06","variant07","variant10","variant12","variant03","variant05","variant08","variant09","variant11","variant13","variant14","variant15")
private val kMouth = listOf("variant01","variant02","variant12","variant22","variant24","variant25","variant30","variant15","variant16","variant17","variant19","variant21","variant23","variant26","variant27","variant28","variant29")
private val kGlasses = listOf("variant01","variant02","variant03","variant04","variant05")
private val kEarrings = listOf("variant01","variant02","variant03","variant04","variant05","variant06")
private val kFeatures = listOf("blush","freckles","birthmark","mustache")
private val kSkinColor = listOf("f2d3b1","ecad80","9e5622","763900")
private val kBgColor = listOf(
    "ff8fab", // rosa
    "ffb703", // ambra
    "4cc9a7", // menta
    "4d96ff", // blu
    "b57bff", // viola
    "ff6b35", // arancione
    "00b4d8", // ciano
    "8ac926", // lime
    "e63946", // rosso
    "6a4c93", // viola scuro
)
private val kOptionalAvatarComponents = listOf("glassesVariant", "earringsVariant", "detailsVariant")
private val kAllowedAvatarValues by lazy {
    mapOf(
        "hairVariant" to kAvatarHair,
        "hairColor" to kHairColor,
        "eyesVariant" to kEyes,
        "eyebrowsVariant" to kEyebrows,
        "mouthVariant" to kMouth,
        "glassesVariant" to kGlasses,
        "earringsVariant" to kEarrings,
        "detailsVariant" to kFeatures,
        "skinColor" to kSkinColor,
        "backgroundColor" to kBgColor,
    )
}

private fun pick(seed: String, param: String, length: Int): Int {
    var hash = 0
    for (c in (seed + param)) {
        hash = (hash * 31 + c.code) and 0x7FFFFFFF
    }
    return hash % length
}

private fun chance(seed: String, param: String, percent: Int): Boolean =
    pick(seed, param, 100) < percent

private fun defaultAvatarParams(seed: String): Map<String, String> = buildMap {
    put("hairVariant", kAvatarHair[pick(seed, "hairVariant", kAvatarHair.size)])
    put("hairColor", kHairColor[pick(seed, "hairColor", kHairColor.size)])
    put("backgroundColor", kBgColor[pick(seed, "backgroundColor", kBgColor.size)])
    put("eyesVariant", kEyes[pick(seed, "eyesVariant", kEyes.size)])
    put("eyebrowsVariant", kEyebrows[pick(seed, "eyebrowsVariant", kEyebrows.size)])
    put("mouthVariant", kMouth[pick(seed, "mouthVariant", kMouth.size)])
    put("skinColor", kSkinColor.first())
    if (chance(seed, "earringsVariant", 8)) put("earringsVariant", kEarrings[pick(seed, "earringsPick", kEarrings.size)])
    if (chance(seed, "glassesVariant", 5)) put("glassesVariant", kGlasses[pick(seed, "glassesPick", kGlasses.size)])
    if (chance(seed, "detailsVariant", 10)) put("detailsVariant", kFeatures[pick(seed, "detailsPick", kFeatures.size)])
}

private fun normalizedAvatarParams(seed: String, config: Map<String, Any>?): MutableMap<String, String> {
    val params = defaultAvatarParams(seed).toMutableMap()
    if (config.isNullOrEmpty()) return params

    // In una configurazione salvata, un componente opzionale assente significa "nessuno".
    kOptionalAvatarComponents.forEach(params::remove)
    config.forEach { (key, rawValue) ->
        val value = rawValue.toString().takeIf { it.isNotBlank() && it != "null" }
            ?: return@forEach
        val allowedValues = kAllowedAvatarValues[key] ?: return@forEach
        if (value in allowedValues) params[key] = value
    }
    return params
}

internal data class AdventurerAvatarOptions(
    val hair: List<String>,
    val hairColor: List<String>,
    val eyes: List<String>,
    val eyebrows: List<String>,
    val mouth: List<String>,
    val glasses: List<String>,
    val earrings: List<String>,
    val features: List<String>,
    val skinColor: List<String>,
    val backgroundColor: List<String>,
)

internal fun adventurerAvatarOptions(): AdventurerAvatarOptions = AdventurerAvatarOptions(
    hair = kAvatarHair,
    hairColor = kHairColor,
    eyes = kEyes,
    eyebrows = kEyebrows,
    mouth = kMouth,
    glasses = kGlasses,
    earrings = kEarrings,
    features = kFeatures,
    skinColor = kSkinColor,
    backgroundColor = kBgColor,
)

internal fun avatarConfigForEditing(seed: String, config: Map<String, Any>?): MutableMap<String, String> {
    return normalizedAvatarParams(seed, config)
}

fun buildAvatarUrl(seed: String, config: Map<String, Any>?): String {
    officialAvatarUrl(config)?.let { return it }
    val params = normalizedAvatarParams(seed, config)
    val uri = Uri.parse(DICEBEAR_BASE).buildUpon().apply {
        appendQueryParameter("seed", seed)
        params.forEach { (k, v) ->
            if (!k.endsWith("Probability") && v.isNotEmpty() && v != "null") {
                appendQueryParameter(k, v)
            }
        }
        kOptionalAvatarComponents.forEach { component ->
            val selected = params[component]
                ?.takeIf { it.isNotEmpty() && it != "null" }
            appendQueryParameter(
                "${component.removeSuffix("Variant")}Probability",
                if (selected != null) "100" else "0",
            )
        }
    }.build()
    return uri.toString()
}

internal class NativeAnonAvatarView(context: Context) : View(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var modelUrl: String = ""
    private var bitmap: Bitmap? = null
    private var decodedSizePx = 0
    private var requestedSizePx = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clipPath = Path()
    private val src = Rect()
    private val dst = RectF()

    init {
        setWillNotDraw(false)
    }

    fun bind(url: String) {
        if (url != modelUrl) {
            modelUrl = url
            bitmap = null
            decodedSizePx = 0
            requestedSizePx = 0
        }
        ensureAvatarLoaded()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureAvatarLoaded()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureAvatarLoaded()
    }

    private fun ensureAvatarLoaded() {
        if (modelUrl.isEmpty()) return
        val targetSizePx = targetSizePx()
        if (bitmap != null && decodedSizePx >= targetSizePx) return
        if (requestedSizePx >= targetSizePx) return
        loadAvatar(modelUrl, targetSizePx)
    }

    private fun targetSizePx(): Int = max(max(width, height), 96)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val fallback = suggestedMinimumWidth.coerceAtLeast(suggestedMinimumHeight)
        val size = when {
            width > 0 && height > 0 -> minOf(width, height)
            width > 0 -> width
            height > 0 -> height
            else -> fallback
        }
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = width.coerceAtMost(height) / 2f
        val cx = width / 2f
        val cy = height / 2f

        paint.style = Paint.Style.FILL
        paint.color = NativeColors.surface
        canvas.drawCircle(cx, cy, radius, paint)

        val avatar = bitmap ?: return
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.reset()
        clipPath.addOval(dst, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clipPath)

        val scale = max(width / avatar.width.toFloat(), height / avatar.height.toFloat())
        val scaledW = avatar.width * scale
        val scaledH = avatar.height * scale
        val left = (width - scaledW) / 2f
        val top = (height - scaledH) / 2f

        src.set(0, 0, avatar.width, avatar.height)
        dst.set(left, top, left + scaledW, top + scaledH)
        canvas.drawBitmap(avatar, src, dst, bitmapPaint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        loadJob?.cancel()
        loadJob = null
        if (bitmap == null) {
            requestedSizePx = 0
        }
        super.onDetachedFromWindow()
    }

    private fun loadAvatar(url: String, targetSizePx: Int) {
        requestedSizePx = targetSizePx
        loadJob?.cancel()
        loadJob = scope.launch {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(targetSizePx, targetSizePx)
                .build()
            val result = context.imageLoader.execute(request)
            val drawable = result.drawable ?: return@launch
            val nextBitmap = withContext(Dispatchers.IO) {
                drawable.toBitmap(targetSizePx, targetSizePx)
            }
            if (modelUrl == url && requestedSizePx == targetSizePx) {
                bitmap = nextBitmap
                decodedSizePx = targetSizePx
                invalidate()
            }
        }
    }

}
