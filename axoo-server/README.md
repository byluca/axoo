# Axoo Server

API Go per Axoo, una community universitaria anonima. Usa Supabase per
autenticazione e storage, PostgreSQL per i dati e Firebase per le notifiche push.

## Sviluppo locale

Servono la versione di Go indicata in [go.mod](go.mod) e un progetto Supabase
di sviluppo con Auth e Storage. Su un progetto nuovo applica [schema.sql](schema.sql)
alla connessione PostgreSQL come proprietario delle tabelle.

```sh
psql 'URI_POSTGRES_DI_SVILUPPO' -v ON_ERROR_STOP=1 -f schema.sql
```

Popola `cities` e `universities` con le città, gli atenei e i domini email
del tuo ambiente. Attiva l’accesso via email in Supabase Auth. Lo schema
applicativo lascia a Supabase la gestione di Auth e Storage. Per meme e sticker,
crea il bucket `memes` con lettura pubblica e lascia la scrittura al backend.

```sh
cp .env.example .env
go mod download
go test -race ./...
```

Compila `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_DB_URL` ed
`EMAIL_SALT` nel tuo `.env` locale, poi avvia e verifica l’API.

```sh
go run ./cmd/api
# Da un altro terminale
curl http://localhost:8080/health
```

I test PostgreSQL usano un database locale dedicato tramite le variabili di
ambiente indicate nei singoli test. Le integrazioni facoltative dell’API
sono elencate in [.env.example](.env.example).

## Struttura

- [cmd/api/](cmd/api/) avvia l’API HTTP.
- [internal/](internal/) contiene le funzioni dell’app.
- [pkg/](pkg/) contiene database, middleware HTTP e storage condivisi.
- [cmd/utility/](cmd/utility/) contiene i comandi amministrativi, incluso il [Content Desk](cmd/utility/contentdesk/README.md).
- [scripts/](scripts/README.md) contiene gli strumenti di manutenzione.
- [docs/](docs/) descrive il comportamento delle funzioni e delle API.

## Licenza

Il codice originale è distribuito sotto [AGPL-3.0-only](LICENSE).
Le licenze delle dipendenze e le condizioni delle risorse grafiche sono
descritte in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Se rendi disponibile in rete una versione modificata, offri a chi la usa
il sorgente corrispondente alla versione in esecuzione, come previsto
dalla [sezione 13 dell’AGPL](https://www.gnu.org/licenses/agpl-3.0.html#section13).
