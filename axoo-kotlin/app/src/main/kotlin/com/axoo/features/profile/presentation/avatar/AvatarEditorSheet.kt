package com.axoo.features.profile.presentation.avatar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import coil.load
import com.axoo.application.navigation.host.OverlayHost
import com.axoo.features.profile.domain.User
import com.axoo.ui.avatar.NativeAnonAvatarView
import com.axoo.ui.avatar.adventurerAvatarOptions
import com.axoo.ui.avatar.avatarConfigForEditing
import com.axoo.ui.avatar.buildAvatarUrl
import com.axoo.ui.components.SlidingCategoryIndicatorLayout
import com.axoo.ui.sheets.BottomSheetConfig
import com.axoo.ui.sheets.BottomSheetContainer
import com.axoo.ui.sheets.BottomSheetTypography
import com.axoo.ui.sheets.OverlayCopy
import com.axoo.ui.sheets.applySheetTypography
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

internal object AvatarEditorSheet {

    class Handle internal constructor(private val view: NativeAvatarEditorSheetView) {
        fun setSaving(saving: Boolean) = view.setSaving(saving)

        fun showError(message: String) = view.showError(message)

        fun dismiss() = view.dismiss()

        fun dismissImmediately() = view.dismissImmediately()
    }

    fun present(
        anchor: View,
        user: User,
        onSave: (Map<String, Any>) -> Unit,
        onDismissed: () -> Unit = {},
    ): Handle {
        val sheet = NativeAvatarEditorSheetView(
            context = anchor.context,
            user = user,
            onSave = onSave,
            onDismissed = onDismissed,
        )
        OverlayHost.presentSheetOn(anchor, sheet)
        return Handle(sheet)
    }
}

private enum class AvatarCategory(
    val label: String,
    val iconAsset: String,
) {
    Hair("Capelli", "hair.svg"),
    Eyes("Occhi", "avatar-category-eyes.svg"),
    Brows("Sopracciglia", "eyebrows.svg"),
    Mouth("Bocca", "mouth.svg"),
    Colors("Colori", "avatar-category-colors.svg"),
    Extras(OverlayCopy.avatarAccessories, "avatar-category-extras.svg"),
}

@SuppressLint("ViewConstructor")
internal class NativeAvatarEditorSheetView(
    context: Context,
    private val user: User,
    private val onSave: (Map<String, Any>) -> Unit,
    onDismissed: () -> Unit,
) : BottomSheetContainer(
    context = context,
    config = BottomSheetConfig(
        sheetBackgroundColor = NativeColors.background,
        dismissOnScrimClick = false,
        dragToDismiss = false,
        showsDragHandle = false,
    ),
) {
    private val options = adventurerAvatarOptions()
    private val viewModel = AvatarEditorViewModel(avatarConfigForEditing(user.id, user.avatarConfig))
    private val draft get() = viewModel.draft
    private var category = AvatarCategory.Hair
    private val saving get() = viewModel.isSaving

    private val preview = NativeAnonAvatarView(context)
    private val categories = SlidingCategoryIndicatorLayout(
        context = context,
        indicatorWidthPx = 0,
        indicatorHeightPx = dp(36),
        indicatorCornerRadiusPx = dp(8).toFloat(),
        indicatorHorizontalOutsetPx = -dp(2),
    ).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val choices = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(16), 0, dp(16))
    }
    private val choicesScroll = NestedScrollView(context).apply {
        overScrollMode = View.OVER_SCROLL_ALWAYS
        clipToPadding = false
        addView(
            choices,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
    private val error = TextView(context).apply {
        visibility = View.GONE
        applySheetTypography(BottomSheetTypography.avatarError)
        setTextColor(NativeColors.destructive)
    }
    private val closeButton = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageTintList = ColorStateList.valueOf(NativeColors.textPrimary)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = closeButtonRipple()
        isClickable = true
        isFocusable = true
        load("file:///android_asset/icons/close.svg") {
            crossfade(false)
        }
    }
    private val save = TextView(context).apply {
        text = OverlayCopy.save
        gravity = Gravity.CENTER
        applySheetTypography(BottomSheetTypography.avatarSave)
        minimumWidth = dp(72)
        minimumHeight = dp(36)
        setPadding(dp(14), dp(4), dp(14), dp(4))
    }

    init {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        content.addView(
            FrameLayout(context).apply {
                minimumHeight = dp(44)
                addView(
                    closeButton,
                    FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.CENTER_VERTICAL),
                )
                addView(
                    save,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.END or Gravity.CENTER_VERTICAL,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(
            preview,
            LinearLayout.LayoutParams(dp(176), dp(176)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(4)
            },
        )
        content.addView(
            categories,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).apply { topMargin = dp(8) },
        )

        content.addView(
            error,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        content.addView(
            choicesScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                .apply { topMargin = dp(8) },
        )
        addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        AvatarCategory.entries.forEach { entry ->
            categories.addView(
                categoryButton(entry),
                LinearLayout.LayoutParams(0, dp(44), 1f),
            )
        }
        closeButton.setOnClickListener {
            if (!saving) dismiss()
        }
        save.setOnClickListener {
            if (viewModel.canSave) {
                setSaving(true)
                onSave(draft.mapValues { it.value })
            }
        }
        addOnDismissedListener(onDismissed)
        refresh()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Come il detent iOS: 85% dello spazio disponibile, con scorrimento delle sole opzioni.
        val sheetHeight = (MeasureSpec.getSize(heightMeasureSpec) * 0.85f).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(sheetHeight, MeasureSpec.EXACTLY))
    }

    fun setSaving(value: Boolean) {
        viewModel.setSaving(value)
        allowsDismissal = !value
        refreshCategoryState()
        refreshSaveState()
    }

    fun showError(message: String) {
        setSaving(false)
        error.text = message
        error.visibility = View.VISIBLE
        refreshCategoryState()
        refreshSaveState()
    }

    private fun categoryButton(entry: AvatarCategory): FrameLayout = FrameLayout(context).apply {
        isClickable = true
        isFocusable = true
        addView(
            FrameLayout(context).apply {
                addView(
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        load("file:///android_asset/icons/${entry.iconAsset}") {
                            crossfade(false)
                        }
                    },
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
            FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER),
        )
        setOnClickListener {
            if (saving || category == entry) return@setOnClickListener
            category = entry
            this@NativeAvatarEditorSheetView.error.visibility = View.GONE
            refresh()
        }
    }

    private fun refresh() {
        preview.bind(buildAvatarUrl(user.id, draft))
        refreshCategoryState()
        choices.removeAllViews()
        when (category) {
            AvatarCategory.Hair -> addAssetGrid("hairVariant", options.hair, "hair")
            AvatarCategory.Eyes -> addAssetGrid("eyesVariant", options.eyes, "eyes")
            AvatarCategory.Brows -> addAssetGrid("eyebrowsVariant", options.eyebrows, "eyebrows")
            AvatarCategory.Mouth -> addAssetGrid("mouthVariant", options.mouth, "mouth")
            AvatarCategory.Colors -> addColors()
            AvatarCategory.Extras -> addExtras()
        }
        refreshSaveState()
    }

    private fun refreshCategoryState() {
        categories.children.forEachIndexed { index, child ->
            val selected = AvatarCategory.entries[index] == category
            (child as FrameLayout).apply {
                isEnabled = !saving
                alpha = if (saving) 0.55f else 1f
                (getChildAt(0) as FrameLayout).apply {
                    (getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(
                        if (selected) NativeColors.accent else NativeColors.textPrimary,
                    )
                }
            }
        }
        categories.setSelectedIndex(category.ordinal)
    }

    private fun addAssetGrid(key: String, values: List<String>, assetFolder: String) {
        choices.addView(choiceGrid(values) { value ->
            assetChoice(
                selected = draft[key] == value,
                description = value,
                assetPath = "presets/$assetFolder/$value.svg",
            ) {
                select(key, value)
            }
        })
    }

    private fun addColors() {
        addColorGrid("Colore dei capelli", "hairColor", options.hairColor)
        addColorGrid("Tonalità della pelle", "skinColor", options.skinColor)
        addColorGrid("Sfondo", "backgroundColor", options.backgroundColor)
    }

    private fun addColorGrid(label: String, key: String, values: List<String>) {
        choices.addView(sectionLabel(label))
        choices.addView(choiceGrid(values) { value ->
            colorChoice(
                value = value,
                selected = draft[key] == value,
            ) {
                select(key, value)
            }
        })
    }

    private fun addExtras() {
        addOptionalAssetGrid("Occhiali", "glassesVariant", options.glasses, "glasses")
        addOptionalAssetGrid("Orecchini", "earringsVariant", options.earrings, "earrings")
        addOptionalAssetGrid("Dettagli", "detailsVariant", options.features, "features")
    }

    private fun addOptionalAssetGrid(label: String, key: String, values: List<String>, assetFolder: String) {
        choices.addView(sectionLabel(label))
        choices.addView(choiceGrid(listOf<String?>(null) + values) { value ->
            if (value == null) {
                textChoice(
                    text = "Nessuno",
                    selected = draft[key].isNullOrEmpty(),
                ) {
                    selectOptional(key, null)
                }
            } else {
                assetChoice(
                    selected = draft[key] == value,
                    description = value,
                    assetPath = "presets/$assetFolder/$value.svg",
                ) {
                    selectOptional(key, value)
                }
            }
        })
    }

    private fun <T> choiceGrid(values: List<T>, item: (T) -> View): GridLayout {
        return object : GridLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val side = ((MeasureSpec.getSize(widthMeasureSpec) - 2 * dp(8)) / 3).coerceAtLeast(1)
                children.forEach { child ->
                    child.layoutParams.width = side
                    child.layoutParams.height = side
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            columnCount = 3
            values.forEachIndexed { index, value ->
                addView(
                    item(value),
                    GridLayout.LayoutParams().apply {
                        columnSpec = GridLayout.spec(index % 3)
                        rowSpec = GridLayout.spec(index / 3)
                        setMargins(0, if (index >= 3) dp(8) else 0, if (index % 3 < 2) dp(8) else 0, 0)
                    },
                )
            }
        }
    }

    private fun assetChoice(
        selected: Boolean,
        description: String,
        assetPath: String,
        onClick: () -> Unit,
    ): View = FrameLayout(context).apply {
        background = choiceBackground(selected)
        isClickable = true
        isFocusable = true
        setPadding(dp(3), dp(3), dp(3), dp(3))
        addView(
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                load("file:///android_asset/$assetPath")
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        setOnClickListener { if (!saving) onClick() }
    }

    private fun colorChoice(value: String, selected: Boolean, onClick: () -> Unit): View {
        return FrameLayout(context).apply {
            background = choiceBackground(selected)
            isClickable = true
            isFocusable = true
            setPadding(dp(9), dp(9), dp(9), dp(9))
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#$value"))
                }
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            setOnClickListener { if (!saving) onClick() }
        }
    }

    private fun textChoice(text: String, selected: Boolean, onClick: () -> Unit): View {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            applySheetTypography(BottomSheetTypography.avatarChoice)
            setTextColor(NativeColors.textSecondary)
            background = choiceBackground(selected)
            isClickable = true
            isFocusable = true
            setOnClickListener { if (!saving) onClick() }
        }
    }

    private fun sectionLabel(text: String): TextView = TextView(context).apply {
        this.text = text
        applySheetTypography(BottomSheetTypography.avatarSection)
        setTextColor(NativeColors.textSecondary)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        setPadding(0, dp(10), 0, dp(5))
    }

    private fun select(key: String, value: String) {
        if (draft[key] == value) return
        viewModel.select(key, value)
        error.visibility = View.GONE
        refresh()
    }

    private fun selectOptional(key: String, value: String?) {
        val current = draft[key]
        if (current == value || (value == null && current.isNullOrEmpty())) return
        viewModel.select(key, value)
        error.visibility = View.GONE
        refresh()
    }

    private fun refreshSaveState() {
        val enabled = viewModel.canSave
        closeButton.isEnabled = !saving
        closeButton.alpha = if (saving) 0.45f else 1f
        save.isEnabled = enabled
        save.alpha = 1f
        save.setTextColor(if (enabled) NativeColors.onAccent else NativeColors.textSecondary)
        save.background = actionBackground(enabled)
    }

    private fun closeButtonRipple(): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(NativeColors.pressedOverlay),
        ColorDrawable(Color.TRANSPARENT),
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        },
    )

    private fun choiceBackground(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(8).toFloat()
        setColor(NativeColors.pickerControl)
        setStroke(dp(if (selected) 2 else 1), if (selected) NativeColors.accent else NativeColors.border)
    }

    private fun actionBackground(enabled: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(8).toFloat()
        setColor(if (enabled) NativeColors.accent else NativeColors.pickerControl)
    }
}
