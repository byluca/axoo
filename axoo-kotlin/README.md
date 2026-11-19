# Axoo Android

Client nativo in Kotlin con Android Views e motore C++ Telegram rlottie.

## Build

JDK 21, Android SDK 36, NDK `27.0.12077973` e CMake 3.22.1 o successivo.

```sh
cp local.properties.example local.properties
# Impostare il percorso SDK e le proprie chiavi client.
./gradlew :app:assembleDebug
```

Debug compila anche senza chiavi. Supabase serve per il login; KLIPY per GIF e
meme. Per le push aggiungere `app/google-services.json`. Questi file restano locali.
Per usare un ambiente HTTPS di sviluppo, imposta `axoo.api.url` e
`supabase.url` in `local.properties` insieme alla chiave client del tuo progetto.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

## Codice

Dentro `app/src/main/kotlin/com/axoo/`:

- `application/`: avvio, dipendenze e navigazione.
- `core/`: rete, sessione e servizi di piattaforma.
- `ui/`: componenti grafici e tema condivisi.
- `engine/`: emoji e rendering Lottie.
- `features/`: codice diviso per funzionalità.

Risorse in `app/src/main/res` e `assets`, C++ in `app/src/main/cpp`.
Test in `app/src/test` e `app/src/androidTest`; anteprime in `app/src/debug`.
Le funzionalità raggruppano dati, dominio e presentazione dove servono.

Il codice originale del client Axoo è disponibile sotto la [GNU Affero General Public License v3.0](LICENSE).
Per il codice e le risorse di terze parti, consulta [licenze e attribuzioni](THIRD_PARTY_NOTICES.md).
