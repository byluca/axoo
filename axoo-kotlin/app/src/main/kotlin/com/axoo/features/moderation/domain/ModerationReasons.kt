package com.axoo.features.moderation.domain

enum class ModerationReason(
    val apiValue: String,
    val label: String,
) {
    Spam("spam", "Spam"),
    Harassment("harassment", "Offese o molestie"),
    Hate("hate", "Odio o discriminazione"),
    SexualContent("sexual_content", "Contenuto sessuale"),
    Violence("violence", "Violenza"),
    PersonalInformation("personal_information", "Dati personali"),
    Other("other", "Altro");

    companion object {
        fun label(value: String): String = when (value) {
            "other", "" -> "Violazione dei Termini di servizio"
            else -> entries.firstOrNull { it.apiValue == value }?.label
                ?: "Violazione dei Termini di servizio"
        }
    }
}
