package com.axoo.engine.emoji.picker

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import com.axoo.ui.theme.dp
import com.axoo.ui.theme.NativeColors

internal class EmojiCategoryButton(context: Context) : FrameLayout(context) {
    private val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    init {
        isClickable = true
        isFocusable = true
        addView(icon, LayoutParams(context.dp(22), context.dp(22), Gravity.CENTER))
    }
    fun bind(categoryId: String) {
        icon.imageTintList = if (categoryId == "recent") ColorStateList.valueOf(NativeColors.textPrimary) else null
        if (categoryId == "recent") {
            context.assets.open("axooji/category-icons/recents.webp").use {
                icon.setImageBitmap(BitmapFactory.decodeStream(it))
            }
            return
        }
        context.assets.open("axooji/categories/$categoryId.png").use {
            icon.setImageBitmap(BitmapFactory.decodeStream(it))
        }
    }
    fun setSelectedState(selected: Boolean) { alpha = if (selected) 1f else 0.74f; isSelected = selected }
}
