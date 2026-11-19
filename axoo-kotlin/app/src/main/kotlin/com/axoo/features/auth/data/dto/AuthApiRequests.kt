package com.axoo.features.auth.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EmailEligibilityRequest(val email: String)

@JsonClass(generateAdapter = true)
data class EmailEligibilityResponse(
    val eligible: Boolean,
    @Json(name = "login_method") val loginMethod: String = "otp",
)

@JsonClass(generateAdapter = true)
data class AlphaLoginRequest(val email: String, val password: String)

/**
 * 💡 COSA SONO I "REQUEST MODELS" (o DTO - Data Transfer Objects)
 *
 * Questo file contiene i "SCATOLONI DI DATI CHE VIAGGIANO da CLIENT -----> SERVER"
 * (i dati esatti da spedire al server per eseguire quelle azioni).
 *
 * - Data Class: Sono semplici "scatole" fatte apposta per contenere dati. Tu le riempi
 *   nel codice (es. quando l'utente preme "Salva"), Retrofit le prende e le spedisce.
 *
 * - @JsonClass(generateAdapter = true): Dice alla libreria Moshi di creare in automatico
 *   il codice noioso che trasforma questa scatola Kotlin in testo universale (JSON)
 *   che il server (nel nostro caso scritto in Golang) può capire.
 *
 * - @Json(name = "nome_server"): È un traduttore. In Kotlin usiamo il camelCase (es. clientId),
 *   ma se il server vuole lo snake_case (es. client_id), questa annotazione si occupa
 *   di fare la traduzione in automatico prima della spedizione.
 */

@JsonClass(generateAdapter = true)
data class AcceptTermsRequest(val version: String, val adult: Boolean = true)
