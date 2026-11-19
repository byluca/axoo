package com.axoo.features.auth.domain

internal fun isValidLoginEmailFormat(rawEmail: String): Boolean {
    val email = rawEmail.trim().lowercase()
    val atIndex = email.lastIndexOf('@')
    if (atIndex <= 0 || atIndex == email.lastIndex || email.count { it == '@' } != 1) return false
    if (email.any(Char::isWhitespace)) return false
    return email.substring(atIndex + 1).contains('.')
}
