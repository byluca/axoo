package com.axoo.ui.formatting

import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Trasforma una data ISO in un tempo breve, per esempio "5m" o "2g". */

fun formatTimeAgo(isoDate: String): String {
    return try {
        val date = OffsetDateTime.parse(isoDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val now = OffsetDateTime.now()
        val duration = Duration.between(date, now)
        when {
            duration.seconds < 60 -> "adesso"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m"
            duration.toHours() < 24 -> "${duration.toHours()}h"
            duration.toDays() < 30 -> "${duration.toDays()}g"
            duration.toDays() < 365 -> "${duration.toDays() / 30}mo"
            else -> "${duration.toDays() / 365}a"
        }
    } catch (e: Exception) {
        ""
    }
}

fun isContentEdited(createdAt: String, updatedAt: String?): Boolean {
    if (updatedAt.isNullOrBlank()) return false
    return runCatching {
        val created = OffsetDateTime.parse(createdAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val updated = OffsetDateTime.parse(updatedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        updated.isAfter(created)
    }.getOrDefault(false)
}
