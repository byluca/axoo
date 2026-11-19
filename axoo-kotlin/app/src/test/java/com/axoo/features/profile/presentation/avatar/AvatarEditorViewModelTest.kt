package com.axoo.features.profile.presentation.avatar

import org.junit.Assert.*
import org.junit.Test

class AvatarEditorViewModelTest {
    @Test fun selectionRemovalAndSavingKeepTheOriginalConfiguration() {
        val initial = mutableMapOf("hairVariant" to "short", "glassesVariant" to "round")
        val vm = AvatarEditorViewModel(initial)
        assertFalse(vm.canSave)
        vm.select("glassesVariant", null)
        assertTrue(vm.canSave)
        assertEquals("round", initial["glassesVariant"])
        vm.setSaving(true)
        vm.select("hairVariant", "long")
        assertEquals("short", vm.draft["hairVariant"])
        assertFalse(vm.canSave)
    }
}
