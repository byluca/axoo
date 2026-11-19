package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.axoo.core.haptics.Haptics
import com.axoo.ui.components.ArrowBackButton
import com.axoo.ui.components.NativeLoadingButton
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp

@SuppressLint("ViewConstructor")
internal class NativePostEditorTopBarView(
    context: Context,
) : FrameLayout(context) {

    private var onBack: () -> Unit = {}
    private var onPublish: () -> Unit = {}
    private var statusTopInset = 0

    private val row = FrameLayout(context).apply {
        setPadding(0, dp(4), 0, dp(4))
    }

    private val backButton = ArrowBackButton(context)
    private val title = TextView(context).apply {
        text = ""
        textSize = PostEditorStyle.titleTextSize
        typeface = context.baloo2Typeface(800)
        setTextColor(NativeColors.textPrimary)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val publishButton = NativeLoadingButton(context,
        textSizeSp = PostEditorStyle.publishTextSize,
        radiusDp = PostEditorStyle.publishRadius,
        horizontalInsetDp = PostEditorStyle.publishHorizontalInset,
        appearance = NativeLoadingButton.Appearance.POST_EDITOR,
    )

    init {
        setBackgroundColor(NativeColors.card)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
        row.addView(backButton, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(4)
        })
        row.addView(title, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(60)
            rightMargin = dp(116)
        })
        row.addView(publishButton, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(PostEditorStyle.publishHeight), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(12)
        })

        backButton.setOnClickListener {
            onBack()
        }
        publishButton.setOnClickListener {
            if (!publishButton.isEnabled) return@setOnClickListener
            Haptics.confirm(this)
            onPublish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            statusTopInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            applyInsets()
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    fun setCallbacks(
        onBack: () -> Unit,
        onPublish: () -> Unit,
    ) {
        this.onBack = onBack
        this.onPublish = onPublish
    }

    fun bind(
        canPublish: Boolean,
        isPublishing: Boolean,
        isEditing: Boolean,
    ) {
        backButton.isEnabled = !isPublishing
        title.text = ""
        publishButton.bind(
            title = if (isEditing) "Salva" else "Pubblica",
            enabled = canPublish,
            loading = isPublishing,
        )
        applyInsets()
    }

    private fun applyInsets() {
        row.setPadding(0, statusTopInset + dp(4), 0, dp(4))
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val titleParams = title.layoutParams as FrameLayout.LayoutParams
        val rightMargin = publishButton.measuredWidth + dp(24)
        if (titleParams.rightMargin != rightMargin) {
            titleParams.rightMargin = rightMargin
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
