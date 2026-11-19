package com.axoo.features.auth.presentation

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.drawable.AnimatedLottieView
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.interTypeface

private data class Slide(
    val imageFileName: String,
    val title: String,
    val subtitle: String,
    val useBrandFontForTitle: Boolean = false,
    val customTitleSize: Float? = null,
    val customImageHeight: Float? = null,
)

private val slides = listOf(
    Slide(
        imageFileName = "new-logo-777.png",
        title = "Axoo",
        subtitle = "<b>Connettiti</b> con chi ti sta <b>intorno</b>.",
        useBrandFontForTitle = true,
        customTitleSize = 48f,
    ),
    Slide(
        imageFileName = "axooji/ui/party-icon.tgs",
        title = "Locale",
        subtitle = "Scopri le <b>storie</b> del campus,<br/>raccontate da chi lo <b>vive</b>.",
        customImageHeight = 220f,
    ),
    Slide(
        imageFileName = "axooji/ui/ghost-hamster.tgs",
        title = "Anonimo",
        subtitle = "Condividi i tuoi <b>pensieri</b><br/>in completo <b>anonimato</b>.",
        customImageHeight = 220f,
    ),
    Slide(
        imageFileName = "axooji/ui/popcorn-icon.tgs",
        title = "Divertente",
        subtitle = "<b>Domande</b>, <b>meme</b>, <b>spotted</b><br/>e un sacco di <b>altra roba</b>.",
        customImageHeight = 220f,
    ),
)

class IntroView(
    context: Context,
    private val onNavigateToLogin: () -> Unit,
) : FrameLayout(context) {
    private val colors = NativeColors
    private val pager = ViewPager2(context).apply {
        adapter = SlideAdapter(slides)
        orientation = ViewPager2.ORIENTATION_HORIZONTAL
        offscreenPageLimit = 1
        (getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
    }

    init {
        setBackgroundColor(colors.background)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, context.dp(24f).toInt())
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            column.setPadding(
                0,
                statusBar.top,
                0,
                navigationBar.bottom + context.dp(24f).toInt(),
            )
            insets
        }

        column.addView(pager, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(Space(context), LinearLayout.LayoutParams(1, context.dp(8f).toInt()))
        column.addView(
            TextView(context).apply {
                gravity = Gravity.CENTER
                text = "Inizia"
                setTextColor(colors.white)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18f)
                typeface = context.baloo2Typeface(700)
                background = rounded(colors.accent, context.dp(18f))
                setOnClickListener {
                    onNavigateToLogin()
                }
            },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(52f).toInt()).apply {
                leftMargin = context.dp(24f).toInt()
                rightMargin = context.dp(24f).toInt()
            },
        )

    }
}

private class SlideAdapter(
    private val slides: List<Slide>,
) : RecyclerView.Adapter<SlideHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideHolder =
        SlideHolder(
            SlideView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT,
                )
            },
        )

    override fun onBindViewHolder(holder: SlideHolder, position: Int) {
        holder.page.bind(slides[position], position)
    }

    override fun getItemCount(): Int = slides.size
}

private class SlideHolder(
    val page: SlideView,
) : RecyclerView.ViewHolder(page)

private class SlideView(context: Context) : LinearLayout(context) {
    private val staticImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, context.dp(40f))
            }
        }
    }
    private val animatedImage = com.axoo.engine.emoji.animation.drawable.AnimatedLottieView(context, context.dp(220f).toInt())
    private val image = FrameLayout(context).apply {
        clipChildren = false
        addView(staticImage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(animatedImage, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
    }
    private val title = TextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        setTextColor(NativeColors.textPrimary)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 34f)
        typeface = context.interTypeface(700)
        letterSpacing = 0f
    }
    private val subtitle = TextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 4
        ellipsize = null
        setTextColor(NativeColors.textSecondary)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18f)
        typeface = context.interTypeface(400)
        setLineSpacing(0f, 1f)
    }

    private val pageIndicator = IntroPageIndicator(context, slides.size)

    init {
        clipChildren = false
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(20f).toInt(), context.dp(24f).toInt(), context.dp(20f).toInt(), context.dp(24f).toInt())

        // image bounds adjusted in declarations

        addView(
            image,
            LayoutParams(context.dp(220f).toInt(), context.dp(220f).toInt()).apply {
                bottomMargin = context.dp(16f).toInt()
            },
        )
        addView(
            title,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(12f).toInt()
            }
        )
        addView(
            subtitle,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
        addView(
            pageIndicator,
            LayoutParams(LayoutParams.WRAP_CONTENT, context.dp(44f).toInt()).apply {
                topMargin = context.dp(16f).toInt()
            },
        )
    }

    fun bind(slide: Slide, pageIndex: Int) {
        pageIndicator.select(pageIndex)
        if (slide.imageFileName.endsWith(".tgs")) {
            staticImage.visibility = View.GONE
            animatedImage.visibility = View.VISIBLE
            animatedImage.setAnimation(
                id = "intro-slide-$pageIndex",
                path = slide.imageFileName
            )
            val size = context.dp(slide.customImageHeight ?: 220f).toInt()
            animatedImage.layoutParams.height = size
            animatedImage.layoutParams.width = size
            animatedImage.translationY = 0f
        } else {
            animatedImage.visibility = View.GONE
            staticImage.visibility = View.VISIBLE
            staticImage.load("file:///android_asset/images/${slide.imageFileName}")
        }
        image.layoutParams.height = context.dp(220f).toInt()
        image.requestLayout()
        title.text = slide.title
        subtitle.text = HtmlCompat.fromHtml(slide.subtitle, HtmlCompat.FROM_HTML_MODE_COMPACT)

        if (slide.useBrandFontForTitle) {
            title.typeface = context.baloo2Typeface(800)
            title.setTextColor(NativeColors.accent)
        } else {
            title.typeface = context.baloo2Typeface(700)
            title.setTextColor(NativeColors.textPrimary)
        }

        val size = slide.customTitleSize ?: 34f
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, size)
    }
}

/** Indicatore delle pagine dell’intro, condiviso da tutte le slide. */
private class IntroPageIndicator(context: Context, pageCount: Int) : LinearLayout(context) {
    private val dots = List(pageCount) { View(context) }

    init {
        gravity = Gravity.CENTER
        orientation = HORIZONTAL
        dots.forEach { dot ->
            addView(
                dot,
                LayoutParams(context.dp(8f).toInt(), context.dp(8f).toInt()).apply {
                    leftMargin = context.dp(4f).toInt()
                    rightMargin = context.dp(4f).toInt()
                },
            )
        }
        select(0)
    }

    fun select(pageIndex: Int) {
        dots.forEachIndexed { index, dot ->
            val selected = index == pageIndex
            dot.background = rounded(
                if (selected) NativeColors.accent else NativeColors.border,
                context.dp(4f),
            )
            dot.layoutParams.width = context.dp(if (selected) 26f else 8f).toInt()
        }
        requestLayout()
    }
}

private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

private fun rounded(color: Int, radius: Float) =
    android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }
