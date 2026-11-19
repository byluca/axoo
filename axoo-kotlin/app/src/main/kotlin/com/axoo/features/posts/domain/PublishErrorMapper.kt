package com.axoo.features.posts.domain

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import retrofit2.HttpException

@JsonClass(generateAdapter = true)
internal data class PublishErrorPayload(
    val error: String? = null,
    val message: String? = null,
)

private val publishErrorAdapter = Moshi.Builder()
    .build()
    .adapter(PublishErrorPayload::class.java)

internal fun Exception.publishErrorMessage(): String {
    val httpError = this as? HttpException
    val backendError = httpError?.response()?.errorBody()?.string()?.let { body ->
        runCatching { publishErrorAdapter.fromJson(body) }
            .getOrNull()
            ?.let { it.message?.takeIf(String::isNotBlank) ?: it.error }
    }
    return publishErrorMessage(httpError?.code(), backendError)
}

internal fun publishErrorMessage(statusCode: Int?, backendError: String?): String = when (statusCode) {
    400 -> backendError.toPublishValidationMessage()
    413 -> "Il media è troppo grande."
    409 -> "Questo contenuto è già stato pubblicato."
    429 -> "Troppe richieste ravvicinate. Attendi un momento e riprova."
    else -> "Errore durante la pubblicazione. Riprova."
}

private fun String?.toPublishValidationMessage(): String = when {
    this?.startsWith("foto scaduta") == true -> "La foto non è più disponibile. Selezionala nuovamente dalla galleria."
    this == "foto non appartenente all'utente" || this == "foto non valida" -> "Seleziona nuovamente la foto dalla galleria."
    this == "dimensioni foto non valide" -> "Le dimensioni della foto non sono valide. Scegline un'altra."
    this == "Il testo contiene parole non consentite. Modificalo e riprova." -> this
    this == "flair non valido" -> "La categoria selezionata non è valida."
    this == "tipo media non valido" -> "Questo tipo di media non è supportato."
    this == "dimensioni media non valide" -> "Le dimensioni del media non sono valide."
    this == "URL media non valido" ||
        this == "URL thumbnail non valido" ||
        this == "URL fallback non valido" ||
        this == "URL media troppo lungo" ||
        this == "Il link non è valido" ||
        this == "Il link è troppo lungo" ->
        "La GIF o il meme selezionato non è più disponibile. Scegline un altro."
    this == "client_id non valido" -> "Impossibile preparare il post. Riprova."
    this == "testo obbligatorio" ||
        this == "il testo del post è obbligatorio" ||
        this == "il post non può essere vuoto" ->
        "Scrivi qualcosa per pubblicare."
    this?.startsWith("il post non può superare") == true ->
        replaceFirstChar { it.uppercase() }
    this?.contains("entità") == true -> "Una delle emoji inserite non è valida."
    else -> "Il post o il media non sono validi."
}
