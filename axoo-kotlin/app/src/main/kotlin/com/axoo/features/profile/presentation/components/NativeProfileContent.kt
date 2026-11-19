package com.axoo.features.profile.presentation.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.content.res.ColorStateList
import coil.load
import com.axoo.features.profile.presentation.ProfileUiState
import com.axoo.engine.emoji.text.AnimatedEmojiTextView
import com.axoo.engine.emoji.text.MessageEntity
import com.axoo.ui.avatar.NativeAnonAvatarView
import com.axoo.ui.avatar.VerifiedBadge
import com.axoo.ui.avatar.isVerifiedAvatar
import com.axoo.ui.avatar.buildAvatarUrl
import com.axoo.ui.components.applyNativePillSurface
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.interTypeface

private const val PROFILE_AVATAR_SIZE_DP = 132

internal fun renderProfileContent(
    content: LinearLayout,
    uiState: ProfileUiState,
    onAvatarTapped: () -> Unit,
    onNicknameTapped: () -> Unit,
    onMyPostsTapped: () -> Unit = {},
    onBioTapped: () -> Unit = {},
) {
    content.removeAllViews()
    content.gravity = Gravity.TOP
    content.setPadding(0, 0, 0, content.dp(40))

    when {
        uiState.isLoading -> listOf(false, true).forEach { bio -> content.addView(
            ProfileSkeletonHeaderView(content.context, bio),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = content.dp(20)
                rightMargin = content.dp(20)
                topMargin = content.dp(16)
            },
        ) }

        uiState.isError -> content.addView(
            ProfileStatusCard(content.context, "Non è stato possibile caricare il profilo."),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = content.dp(20)
                rightMargin = content.dp(20)
                topMargin = content.dp(16)
            },
        )

        else -> {
            val user = checkNotNull(uiState.user) { "Successful profile state requires a user" }
            content.addView(
                ProfileHeaderView(
                    context = content.context,
                    onAvatarTapped = onAvatarTapped.takeUnless { isVerifiedAvatar(user.avatarConfig) },
                    onNicknameTapped = onNicknameTapped.takeUnless { isVerifiedAvatar(user.avatarConfig) },
                ).apply {
                    bind(user.nickname, user.universityName.orEmpty(), buildAvatarUrl(user.id, user.avatarConfig), isVerifiedAvatar(user.avatarConfig))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = content.dp(20)
                    rightMargin = content.dp(20)
                    topMargin = content.dp(16)
                },
            )
            content.addView(ProfileBioView(content.context, onBioTapped).apply {
                bind(user.bio, user.bioEntities)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = content.dp(20)
                rightMargin = content.dp(20)
                topMargin = content.dp(16)
            })
            val row = LinearLayout(content.context).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf(
                    Triple("I miei post", "document", onMyPostsTapped),
                ).forEachIndexed { index, (title, icon, action) ->
                    addView(LinearLayout(context).apply {
                        gravity = Gravity.CENTER
                        setPadding(dp(12), dp(14), dp(12), dp(14))
                        applyNativePillSurface(cornerRadiusDp = 28f)
                        addView(ImageView(context).apply {
                            load("file:///android_asset/icons/$icon.svg")
                            imageTintList = ColorStateList.valueOf(NativeColors.textPrimary)
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(8) })
                        addView(TextView(context).apply {
                            text = title
                            textSize = 17f
                            typeface = context.baloo2Typeface(700)
                            setTextColor(NativeColors.textPrimary)
                        })
                        isFocusable = true
                        contentDescription = title
                        setOnClickListener { action() }
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index > 0) leftMargin = dp(12)
                    })
                }
            }
            content.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = content.dp(20)
                rightMargin = content.dp(20)
                topMargin = content.dp(16)
            })
        }
    }
}

internal class ProfileHeaderView(
    context: Context,
    onAvatarTapped: (() -> Unit)? = null,
    onNicknameTapped: (() -> Unit)? = null,
) : LinearLayout(context) {
    private val avatar = NativeAnonAvatarView(context)
    private val name = TextView(context).apply {
        textSize = 24f
        typeface = context.baloo2Typeface(700)
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(NativeColors.textPrimary)
    }
    private val university = TextView(context).apply {
        textSize = 18f
        typeface = context.baloo2Typeface(400)
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(NativeColors.textSecondary)
    }
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(20), dp(28), dp(20), dp(20))
        applyNativePillSurface(cornerRadiusDp = 24f)

        addView(
            FrameLayout(context).apply {
                if (onAvatarTapped != null) {
                    background = profilePressedBackground(dp(PROFILE_AVATAR_SIZE_DP / 2f))
                    setOnClickListener { onAvatarTapped() }
                    isFocusable = true
                    contentDescription = "Modifica avatar"
                }
                addView(
                    avatar,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
            LayoutParams(dp(PROFILE_AVATAR_SIZE_DP), dp(PROFILE_AVATAR_SIZE_DP)),
        )
        addView(name, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        name.setPadding(0, 0, 0, 0)
        if (onNicknameTapped != null) {
            name.isFocusable = true
            name.setOnClickListener { onNicknameTapped() }
        }
        addView(university, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
    }

    fun bind(nickname: String, universityName: String, avatarUrl: String, verified: Boolean = false) {
        name.text = nickname
        VerifiedBadge.bind(name, verified)
        avatar.bind(avatarUrl)
        university.text = universityName
        university.visibility = if (universityName.isBlank()) View.GONE else View.VISIBLE
    }
}

internal class ProfileBioView(context: Context, onEdit: (() -> Unit)? = null) : LinearLayout(context) {
    private val editable = onEdit != null
    private val title = TextView(context).apply {
        text = "Bio"
        textSize = 15f
        typeface = context.baloo2Typeface(700)
        setTextColor(NativeColors.textPrimary)
    }
    private val bio = AnimatedEmojiTextView(context).apply {
        textSize = 17f
        typeface = context.interTypeface(400)
        includeFontPadding = false
        setPlaybackAllowed(false)
    }

    init {
        orientation = VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(20))
        applyNativePillSurface(cornerRadiusDp = 24f)
        if (onEdit != null) {
            contentDescription = "Modifica bio"
            isFocusable = true
            setOnClickListener { onEdit() }
        }
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bio, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    fun bind(text: String, entities: List<MessageEntity>) {
        visibility = if (text.isBlank() && !editable) GONE else VISIBLE
        title.visibility = if (text.isBlank()) GONE else VISIBLE
        (bio.layoutParams as LayoutParams).topMargin = if (text.isBlank()) 0 else dp(10)
        bio.setTextColor(if (text.isBlank()) NativeColors.textSecondary else NativeColors.textPrimary)
        bio.setEmojiText(text.ifBlank { "Bio" }, entities)
    }

    fun setActive(active: Boolean) = bio.setPlaybackAllowed(active)
}

internal class ProfileSkeletonHeaderView(context: Context, private val bio: Boolean = false) : View(context) {
    private val shape = RectF()
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NativeColors.skeleton
    }

    init {
        applyNativePillSurface(cornerRadiusDp = 24f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(if (bio) 58 else 130 + PROFILE_AVATAR_SIZE_DP))
    }

    override fun onDraw(canvas: Canvas) {

        if (bio) {
            shape.set(dp(20f), dp(22f), width * 0.65f, dp(36f))
            canvas.drawRoundRect(shape, dp(7f), dp(7f), shimmerPaint)
            return
        }
        val centerX = width / 2f
        val avatarRadius = dp(PROFILE_AVATAR_SIZE_DP / 2f)
        val avatarBottom = dp(28f + PROFILE_AVATAR_SIZE_DP)
        shape.set(centerX - avatarRadius, dp(28f), centerX + avatarRadius, avatarBottom)
        canvas.drawOval(shape, shimmerPaint)
        shape.set(centerX - dp(90f), avatarBottom + dp(12f), centerX + dp(90f), avatarBottom + dp(34f))
        canvas.drawRoundRect(shape, dp(10f), dp(10f), shimmerPaint)
        shape.set(centerX - dp(65f), avatarBottom + dp(42f), centerX + dp(65f), avatarBottom + dp(56f))
        canvas.drawRoundRect(shape, dp(7f), dp(7f), shimmerPaint)
    }
}

private class ProfileStatusCard(context: Context, message: String) : TextView(context) {
    init {
        text = message
        gravity = Gravity.CENTER
        textSize = 15f
        typeface = context.baloo2Typeface(700)
        setTextColor(NativeColors.textSecondary)
        setPadding(dp(24), dp(40), dp(24), dp(40))
        applyNativePillSurface(cornerRadiusDp = 24f)
    }
}

internal fun profileRounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
}

private fun profilePressedBackground(radius: Float): StateListDrawable {
    val pressed = profileRounded(NativeColors.navigationSelection, radius)
    val normal = profileRounded(NativeColors.card, radius).apply {
        setStroke(1, NativeColors.border)
    }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(), normal)
    }
}
