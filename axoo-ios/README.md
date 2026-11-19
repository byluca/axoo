# Axoo iOS

Client nativo in Swift con UIKit programmatico e motore C++ Telegram rlottie.

## Build

macOS, Xcode 26.2 o successivo e XcodeGen 2.44 o successivo. Minimo iOS 16.

```sh
brew install xcodegen
cp configuration/Local.xcconfig.example configuration/Local.xcconfig
# Impostare le proprie chiavi client nel file locale.
xcodegen generate
open Axoo.xcodeproj
```

Selezionare lo schema Axoo e un simulatore. Run compila l'app; Cmd+U esegue i test.
`project.yml` è la fonte del progetto Xcode generato.
Debug compila anche senza chiavi. Supabase serve per il login; KLIPY per GIF e
meme. Per le push aggiungere `app/GoogleService-Info.plist` e configurare APNs.
Per un ambiente HTTPS di sviluppo imposta `AXOO_API_URL` e `AXOO_SUPABASE_URL`
in `Local.xcconfig`. Lo stesso file consente di cambiare team Apple, bundle ID
e progetto Firebase. Per compilare una Release senza push, usa
`AXOO_REQUIRE_FIREBASE_CONFIG = NO`.

## Codice

Dentro `app/`:

- `application/`: avvio, dipendenze e navigazione.
- `core/`: rete, sessione e servizi di piattaforma.
- `ui/`: componenti grafici e tema condivisi.
- `engine/`: emoji e rendering Lottie.
- `features/`: codice diviso per funzionalità.
- `resources/`: immagini, animazioni, font, suoni e licenze.

Test in `tests` e `uitests`; bridge C++ in `native`, codice di terze parti in
`vendor`. Le funzionalità raggruppano dati, dominio e presentazione dove servono.
Il [workflow iOS Simulator](../.github/workflows/ios-simulator.yml) compila ed esegue i test su GitHub.

Il codice originale del client Axoo è disponibile sotto la [GNU Affero General Public License v3.0](LICENSE).
Per il codice e le risorse di terze parti, consulta [licenze e attribuzioni](THIRD_PARTY_NOTICES.md).
