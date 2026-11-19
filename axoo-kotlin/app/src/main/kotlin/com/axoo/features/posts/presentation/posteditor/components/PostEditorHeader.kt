package com.axoo.features.posts.presentation.posteditor.components

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.axoo.ui.avatar.NativeAnonAvatarView
import com.axoo.ui.avatar.VerifiedBadge
import com.axoo.ui.avatar.buildAvatarUrl
import com.axoo.ui.avatar.isVerifiedAvatar
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

@SuppressLint("ViewConstructor")
internal class NativePostEditorHeaderView(context: Context) : LinearLayout(context) {
    private val avatar = NativeAnonAvatarView(context)
    private val nicknameView = TextView(context).apply {
        textSize = PostEditorStyle.authorTextSize
        includeFontPadding = false
        typeface = context.baloo2Typeface(700)
        setTextColor(NativeColors.textPrimary)
        visibility = View.GONE
    }
    private val flair = NativeFlairSelectorChipView(context)
    private val university = TextView(context).apply {
        textSize = PostEditorStyle.metadataTextSize
        typeface = context.baloo2Typeface(400)
        setTextColor(NativeColors.textSecondary)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }
    private var onFlairTap: () -> Unit = {}

    init {
        orientation = VERTICAL

        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(avatar, LayoutParams(dp(PostEditorStyle.avatarSize), dp(PostEditorStyle.avatarSize)))
            addView(nicknameView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(PostEditorStyle.avatarTextSpacing) })
            addView(university, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(PostEditorStyle.metadataSpacing) })
        }

        addView(topRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(flair, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { 
            topMargin = dp(PostEditorStyle.categorySpacing)
        })

        flair.setOnClickListener {
            onFlairTap()
        }
    }

    fun setOnFlairTap(callback: () -> Unit) {
        onFlairTap = callback
    }

    fun bind(
        seed: String,
        avatarConfig: Map<String, Any>?,
        nickname: String?,
        selectedFlair: String?,
        universityName: String?,
    ) {
        avatar.bind(buildAvatarUrl(seed, avatarConfig))
        flair.bind(selectedFlair)
        nicknameView.text = nickname?.takeIf(String::isNotBlank) ?: "Anonimo"
        VerifiedBadge.bind(nicknameView, isVerifiedAvatar(avatarConfig))
        nicknameView.visibility = View.VISIBLE
        
        if (universityName.isNullOrBlank()) {
            university.visibility = View.GONE
        } else {
            university.text = "· $universityName"
            university.visibility = View.VISIBLE
        }
    }
}
