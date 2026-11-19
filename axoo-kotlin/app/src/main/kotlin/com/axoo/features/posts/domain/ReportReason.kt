package com.axoo.features.posts.domain

enum class ReportReason(val apiValue: String, val label: String) {
    Spam("spam", "Spam o pubblicità"),
    Harassment("harassment", "Bullismo o molestie"),
    Hate("hate", "Odio o discriminazione"),
    SexualContent("sexual_content", "Contenuto sessuale"),
    Violence("violence", "Violenza o minacce"),
    PersonalInformation("personal_information", "Informazioni personali"),
    Other("other", "Altro"),
}

