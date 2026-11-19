package com.axoo.features.auth.presentation

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import com.axoo.core.haptics.Haptics
import com.axoo.ui.components.AxooTextInput

/** Simple single-field OTP input with native keyboard and autofill support. */
internal class OtpCodeInputView(context: Context) : AxooTextInput(context) {
    var onCodeChanged: (String) -> Unit = {}

    private var applyingState = false

    init {
        hint = "000000"
        gravity = Gravity.CENTER
        inputType = InputType.TYPE_CLASS_NUMBER
        imeOptions = EditorInfo.IME_ACTION_DONE
        setSingleLine(true)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable) {
                val code = value.toString().filter(Char::isDigit).take(OTP_LENGTH)
                if (code != value.toString()) {
                    setCode(code)
                    if (!applyingState) onCodeChanged(code)
                    return
                }
                if (!applyingState) onCodeChanged(code)
            }
        })
    }

    fun setCode(code: String) {
        val normalized = code.filter(Char::isDigit).take(OTP_LENGTH)
        if (text.toString() == normalized) return
        applyingState = true
        try {
            setText(normalized)
            setSelection(normalized.length)
        } finally {
            applyingState = false
        }
    }

    fun requestCodeFocus() {
        requestFocus()
        setSelection(length())
    }

    fun showError() {
        Haptics.reject(this)
    }

    private companion object {
        const val OTP_LENGTH = 6
    }
}
