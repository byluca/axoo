# Eventi

La voce **Eventi** nella barra inferiore Android e iOS apre gli eventi della
città universitaria, ordinati per data. L'elenco include oggi e i giorni successivi.
Solo `users.is_admin = true` può creare, modificare ed eliminare eventi nella
propria città. Il server verifica il ruolo a ogni scrittura; moderatori e vecchi
permessi `can_create_events` non concedono accesso.

Gli account admin dedicati possono usare un identificativo nel dominio riservato
`account.example` e una password individuale Supabase Auth. L’operatore deve
creare l’identità confermata e il profilo con `is_admin = true`,
`is_alpha_tester = false` e l’università che determina la città degli eventi.
`auth/email-eligibility` restituisce `login_method: account_password`: Android e
iOS mostrano già il campo password. Non viene inviato un codice email.
La rimozione del ruolo admin revoca questo accesso; gli admin con email
universitaria conservano il normale login OTP. I controlli su termini, ban e
sospensioni restano attivi. Il ruolo è quello amministrativo esistente e abilita
anche le altre funzioni riservate agli admin. Le credenziali restano fuori dal
repository e dai log.

L'interfaccia ripristina card, dettaglio e modulo dei commit Android `282f716d`
e iOS `7b7d341`. Il pulsante Crea è circolare, in basso a destra sopra la barra.
Toccando una card si apre il dettaglio; una pressione prolungata apre le azioni
admin di modifica ed eliminazione, con conferma per l'eliminazione.
La locandina usa il ritaglio e le dimensioni del componente originale.

Il modulo originale richiede titolo (120 caratteri), descrizione e contatti
(10000), luogo (160), indirizzo (300), ingresso/prezzo (160), locandina,
inizio e fine. La fine deve seguire l'inizio ed essere futura. Gli orari sono
mostrati in Europe/Rome; API e database ricevono RFC3339/UTC.
Per compatibilità con gli eventi e i client precedenti, l'API continua ad
accettare luogo fino a 200 caratteri e descrizione, locandina, fine, indirizzo
e ingresso opzionali. Senza fine, l'inizio deve essere futuro.

API autenticate, soggette alle restrizioni account esistenti:

- `GET /api/v1/events?cursor=...`: `events`, `next_cursor` opzionale, `can_manage`.
  Pagine da 20; il cursore conserva data e ID per eventi simultanei.
- `GET /api/v1/events/:id`: singolo evento, 404 se assente o di altra città.
- `POST /api/v1/events`: `title`, `location`, `description`, `starts_at`,
  `ends_at`, `address`, `entry_info`, `poster`; restituisce l'evento con 201.
- `PATCH /api/v1/events/:id`: stessi campi; restituisce l'evento aggiornato.
- `DELETE /api/v1/events/:id`: 204; 403 senza ruolo admin, 404 se assente o di altra città.

Città e autore di un nuovo evento provengono dall'account autenticato.
Nome e avatar dell'autore vengono letti dal suo profilo. La modifica conserva
l'autore originale. Agli eventi precedenti senza autore non viene attribuito
un profilo arbitrario: mostrano «Organizzatore» e restano modificabili dagli admin.

## Database

La bacheca usa `campus_events` con città, autore opzionale, orari, indirizzo,
informazioni sull’ingresso e metadati della locandina.

## Locandine

La foto segue `POST /api/v1/media/photos`: JPEG, massimo 3 MB e 2048 pixel
per lato. I client riusano la preparazione foto esistente (massimo 1600 pixel).
`poster` contiene `url`, `width`, `height`, `type: photo`, `mime_type: image/jpeg`.
Il server verifica proprietà e presenza nello storage nella transazione di
scrittura. In modifica è possibile conservare la locandina originale, anche
se caricata dall'altro admin che ha creato l'evento.
La pulizia conserva le foto collegate a eventi; dopo l'eliminazione rientrano
nella pulizia ordinaria.

Verifiche: `go test -race ./...`; integrazione PostgreSQL isolata con
`AXOO_EVENTS_TEST_DB_URL`. Android: `./gradlew testDebugUnitTest assembleDebug lintDebug`.
iOS: validatori locali; XCTest e build UIKit richiedono macOS/Xcode.
