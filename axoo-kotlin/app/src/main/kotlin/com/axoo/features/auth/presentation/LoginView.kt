package com.axoo.features.auth.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.load
import com.axoo.core.haptics.Haptics
import com.axoo.engine.emoji.animation.drawable.*
import com.axoo.engine.emoji.animation.drawable.AnimatedLottieView
import com.axoo.engine.emoji.animation.model.*
import com.axoo.engine.emoji.animation.rendering.*
import com.axoo.engine.emoji.animation.time.*
import com.axoo.features.auth.data.dto.SessionBootstrapResponse
import com.axoo.ui.components.NativeLoadingButton
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.baloo2Typeface
import com.axoo.ui.theme.interTypeface
import kotlin.math.max
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** A deliberately small renderer for [LoginViewModel.uiState]. */
class LoginView(
    context: Context,
    private val viewModel: LoginViewModel,
    lifecycleOwner: LifecycleOwner,
    private val onLoginSuccess: (SessionBootstrapResponse) -> Unit,
) : FrameLayout(context) {
    private val colors = NativeColors
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(24f), context.dp(24f), context.dp(24f), context.dp(24f))
    }
    private val emailInput =
        input("nome@studenti.tuocampus.it", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            .apply { setImeActionLabel("Sblocca l'accesso", EditorInfo.IME_ACTION_DONE) }
    private val codeInput = OtpCodeInputView(context).apply {
        onCodeChanged = { code ->
            viewModel.onOtpChanged(code)
        }
    }
    private val passwordInput = input("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
        }
    }
    private var renderedStep: LoginStep? = null
    private val subtitle = text(14f, 400, colors.textSecondary).apply { gravity = Gravity.CENTER }
    private val consentText = text(12f, 400, colors.textSecondary).apply {
        gravity = Gravity.START
        movementMethod = LinkMovementMethod.getInstance()
        highlightColor = Color.TRANSPARENT
        text = legalConsentText()
    }
    private val termsCheck = android.widget.CheckBox(context).apply {
        buttonTintList = android.content.res.ColorStateList.valueOf(colors.accent)
        gravity = Gravity.CENTER_VERTICAL
        val inset = ((context.dp(44f) - (buttonDrawable?.intrinsicWidth ?: context.dp(24f))) / 2).coerceAtLeast(0)
        setPadding(inset, 0, inset, 0)
        setOnCheckedChangeListener { _, checked -> viewModel.onTermsAcceptedChanged(checked) }
    }
    private val consentRow = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(termsCheck, LinearLayout.LayoutParams(context.dp(44f), context.dp(44f)))
        addView(consentText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = context.dp(8f)
        })
    }
    private val message = text(14f, 400, colors.destructive).apply { gravity = Gravity.CENTER }
    private val activity = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
        isIndeterminate = true
        indeterminateTintList = android.content.res.ColorStateList.valueOf(colors.textSecondary)
    }
    private val primaryButton = NativeLoadingButton(context)
    private val resendButton = NativeLoadingButton(context, primary = false, textSizeSp = 15f)
    private val illustration = AnimatedLottieView(context, context.dp(80f))
    private val backButton = com.axoo.ui.components.ArrowBackButton(context)
    private val scroll = ScrollView(context).apply {
        overScrollMode = View.OVER_SCROLL_ALWAYS
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    init {
        setBackgroundColor(colors.background)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = max(
                navigation.bottom,
                ime.bottom,
            )
            content.setPadding(
                context.dp(24f),
                status.top + context.dp(24f),
                context.dp(24f),
                bottom + context.dp(16f),
            )
            insets
        }
        content.addView(backButton, LinearLayout.LayoutParams(context.dp(48f), context.dp(48f)))
        content.addView(illustration, centeredSize(80f, 80f, top = 4f))
        content.addView(subtitle, matchWidth(top = 12f))
        content.addView(emailInput, matchWidth(height = 54f, top = 16f))
        content.addView(consentRow, matchWidth(top = 10f))
        content.addView(codeInput, matchWidth(height = 54f, top = 16f))
        content.addView(passwordInput, matchWidth(height = 54f, top = 16f))
        content.addView(message, matchWidth(top = 12f))
        content.addView(activity, centeredSize(20f, 20f, top = 12f))
        content.addView(resendButton, matchWidth(height = 52f, top = 8f))
        content.addView(primaryButton, matchWidth(height = 52f, top = 12f))

        emailInput.addTextChangedListener(afterChanged { viewModel.onEmailChanged(it.toString()) })
        passwordInput.addTextChangedListener(afterChanged { viewModel.onPasswordChanged(it.toString()) })
        passwordInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) viewModel.onContinueClicked()
            action == EditorInfo.IME_ACTION_DONE
        }
        emailInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) viewModel.onContinueClicked()
            action == EditorInfo.IME_ACTION_DONE
        }
        backButton.setOnClickListener {
            viewModel.onBackClicked()
        }
        primaryButton.setOnClickListener {
            viewModel.onContinueClicked()
        }
        resendButton.setOnClickListener { viewModel.onResendClicked() }

        viewModel.uiState.onEach(::render).launchIn(lifecycleOwner.lifecycleScope)
        viewModel.events.onEach { event ->
            when (event) {
                is LoginEvent.LoginSucceeded -> {
                    Haptics.confirm(content)
                    hideKeyboard()
                    onLoginSuccess(event.bootstrap)
                }

                LoginEvent.OtpSent -> Haptics.confirm(content)
                is LoginEvent.InputRejected -> {
                    if (event.step == LoginStep.OTP) {
                        codeInput.showError()
                        codeInput.requestCodeFocus()
                    } else if (event.step == LoginStep.PASSWORD) {
                        Haptics.reject(passwordInput)
                        passwordInput.requestFocus()
                    } else {
                        Haptics.reject(emailInput)
                        emailInput.requestFocus()
                        emailInput.setSelection(emailInput.text.length)
                    }
                }
            }
        }.launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { showInputKeyboard() }
    }

    private fun showInputKeyboard() {
        val input = when (renderedStep) {
            LoginStep.OTP -> codeInput
            LoginStep.PASSWORD -> passwordInput
            else -> emailInput
        }
        if (!isAttachedToWindow || !input.isEnabled || viewModel.uiState.value.termsAccount != null) return
        input.requestFocus()
        input.setSelection(input.length())
        ViewCompat.getWindowInsetsController(input)?.show(WindowInsetsCompat.Type.ime())
    }

    fun onBackPressed(): Boolean {
        viewModel.onBackClicked()
        return true
    }

    private fun render(state: LoginUiState) {
        setTextIfChanged(emailInput, state.email)
        codeInput.setCode(state.code)
        setTextIfChanged(passwordInput, state.password)
        val stepChanged = renderedStep != state.step
        renderedStep = state.step
        val otp = state.step == LoginStep.OTP
        val password = state.step == LoginStep.PASSWORD
        val termsOnly = state.step == LoginStep.EMAIL && state.termsAccount != null
        illustration.setAnimation(
            id = if (otp) "login-otp" else "login-email",
            path = if (otp) OTP_ILLUSTRATION else EMAIL_ILLUSTRATION,
        )
        subtitle.text = if (password) {
            "${if (state.accountPassword) "Accesso account" else "Accesso alpha"}\n${state.email.trim()}\nInserisci la password."
        } else if (otp) {
            androidx.core.text.HtmlCompat.fromHtml(
                "Abbiamo inviato il codice a:<br><b>${state.email.trim()}</b><br><br><b>Controlla anche la Posta indesiderata (Spam).</b>",
                androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
            )
        } else if (termsOnly) {
            "Accetta i Termini d’uso per continuare."
        } else {
            "L'accesso è riservato agli studenti.\nSblocca il pass con email del tuo campus."
        }
        subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (otp) 13f else 14f)
        emailInput.visibility = if (otp || password || termsOnly) GONE else VISIBLE
        consentRow.visibility = if (otp || password) GONE else VISIBLE
        termsCheck.isChecked = state.termsAccepted
        termsCheck.isEnabled = !state.isLoading
        codeInput.visibility = if (otp) VISIBLE else GONE
        passwordInput.visibility = if (password) VISIBLE else GONE
        passwordInput.isEnabled = !state.isLoading
        primaryButton.visibility = if (otp) GONE else VISIBLE
        val primaryTitle = if (termsOnly) {
            "Accetto e continuo"
        } else if (otp || password) {
            "Accedi"
        } else {
            when {
                state.resendSeconds > 0 -> "Riprova tra ${state.resendSeconds}s"
                else -> "Sblocca l'accesso"
            }
        }
        val primaryEnabled = state.canSubmit
        primaryButton.bind(primaryTitle, primaryEnabled, !otp && state.isLoading)
        codeInput.isEnabled = !state.isLoading
        activity.visibility = if (otp && state.isVerifying) VISIBLE else GONE
        message.text = state.error ?: state.info.orEmpty()
        message.visibility = if (state.error != null || state.info != null) VISIBLE else GONE
        message.setTextColor(if (state.error != null) colors.destructive else colors.textSecondary)
        resendButton.visibility = if (otp) VISIBLE else GONE
        val resendTitle = when {
            state.resendSeconds > 0 -> "Nuovo codice tra ${state.resendSeconds}s"
            else -> "Invia un nuovo codice"
        }
        resendButton.bind(resendTitle, state.canResend,
            otp && state.isSending)
        emailInput.isEnabled = !state.isLoading
        backButton.visibility = if (otp || password) VISIBLE else INVISIBLE
        backButton.isEnabled = !state.isLoading
        if (stepChanged) post { showInputKeyboard() }
    }

    private fun input(hint: String, type: Int): EditText = com.axoo.ui.components.AxooTextInput(context).apply {
        this.hint = hint
        setSingleLine(true)
        inputType = type
        imeOptions = EditorInfo.IME_ACTION_DONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        // Colori, background (pill) e padding sono ora gestiti automaticamente dal componente ufficiale!
    }

    private fun text(size: Float, weight: Int, color: Int): TextView = TextView(context).apply {
        setTextColor(color)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size)
        typeface = context.interTypeface(weight)
        includeFontPadding = false
    }

    private fun legalConsentText(): CharSequence {
        val terms = "Termini d'uso"
        val privacy = "Informativa sulla privacy"
        val value = "Ho almeno 18 anni e accetto i $terms. Ho letto l'$privacy."
        return SpannableString(value).apply {
            setSpan(
                WebsiteLinkSpan(TERMS_URL),
                value.indexOf(terms),
                value.indexOf(terms) + terms.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                WebsiteLinkSpan(PRIVACY_URL),
                value.indexOf(privacy),
                value.indexOf(privacy) + privacy.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private inner class WebsiteLinkSpan(private val url: String) : ClickableSpan() {
        override fun onClick(widget: View) {
            runCatching {
                widget.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        override fun updateDrawState(textPaint: TextPaint) {
            super.updateDrawState(textPaint)
            textPaint.color = colors.accent
            textPaint.isUnderlineText = true
        }
    }

    private fun setTextIfChanged(input: EditText, value: String) {
        if (input.text.toString() != value) {
            input.setText(value)
            input.setSelection(value.length)
        }
    }

    private fun matchWidth(height: Float? = null, top: Float = 0f) =
        LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            if (height == null) LayoutParams.WRAP_CONTENT else context.dp(height),
        ).apply { topMargin = context.dp(top) }

    private fun centeredSize(width: Float, height: Float, top: Float = 0f) =
        LinearLayout.LayoutParams(context.dp(width), context.dp(height)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = context.dp(top)
        }

    private fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)
    }

    private fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    private fun afterChanged(block: (Editable) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable) = block(s)
    }

    private companion object {
        const val TERMS_URL = "https://axoocampus.it/terms.html"
        const val PRIVACY_URL = "https://axoocampus.it/privacy.html"
    }
}

private val EMAIL_ILLUSTRATION = "axooji/ui/locked.tgs"
private val OTP_ILLUSTRATION = "axooji/ui/mail-icon.tgs"
