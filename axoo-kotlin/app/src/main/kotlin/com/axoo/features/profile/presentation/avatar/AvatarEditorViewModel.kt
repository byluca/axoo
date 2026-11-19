package com.axoo.features.profile.presentation.avatar

internal class AvatarEditorViewModel(initial: Map<String, String>) {
    private val initial = initial.toMap()
    var draft: Map<String, String> = initial.toMap(); private set
    var isSaving = false; private set
    val canSave get() = !isSaving && draft != initial
    fun select(key: String, value: String?) {
        if (isSaving) return
        draft = draft.toMutableMap().apply { if (value == null) remove(key) else put(key, value) }
    }
    fun setSaving(value: Boolean) { isSaving = value }
}
