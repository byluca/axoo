package com.axoo.features.posts.presentation.report

import com.axoo.features.posts.domain.ReportReason

internal class ReportViewModel {
    var selectedReason: ReportReason? = null; private set
    var isSubmitting = false; private set
    var error: String? = null; private set
    fun select(reason: ReportReason) {
        if (isSubmitting) return
        selectedReason = reason; error = null
    }
    fun beginSubmit(): ReportReason? {
        if (isSubmitting) return null
        val reason = selectedReason ?: return null
        isSubmitting = true; error = null
        return reason
    }
    fun fail(message: String) { isSubmitting = false; error = message }
}
