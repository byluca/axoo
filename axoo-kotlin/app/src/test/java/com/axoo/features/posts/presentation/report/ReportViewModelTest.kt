package com.axoo.features.posts.presentation.report

import com.axoo.features.posts.domain.ReportReason
import org.junit.Assert.*
import org.junit.Test

class ReportViewModelTest {
    @Test fun failurePreservesSelectionAndAllowsRetry() {
        val vm = ReportViewModel()
        assertNull(vm.beginSubmit())
        vm.select(ReportReason.Spam)
        assertEquals(ReportReason.Spam, vm.beginSubmit())
        assertNull(vm.beginSubmit())
        vm.select(ReportReason.Other)
        assertEquals(ReportReason.Spam, vm.selectedReason)
        vm.fail("offline")
        assertEquals("offline", vm.error)
        assertEquals(ReportReason.Spam, vm.beginSubmit())
        assertNull(vm.error)
    }
}
