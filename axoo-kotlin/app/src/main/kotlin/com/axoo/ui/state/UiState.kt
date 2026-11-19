package com.axoo.ui.state

/**
 * Modella lo stato di un'operazione per l'interfaccia utente.
 */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val throwable: Throwable, val message: String? = null) : UiState<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.data
}
