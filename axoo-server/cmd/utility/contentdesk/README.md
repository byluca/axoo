# Content Desk

Dalla radice di axoo-server:

```sh
go run ./cmd/utility/contentdesk
```

Apri il link privato stampato nel terminale. Il Desk ascolta su loopback.
Legge il catalogo Axooji da `../axoo-kotlin/app/src/main/assets/axooji` e
include le anteprime statiche da `emoji-previews/`.

## Contenuti e notifiche

In **Contenuti**, cerca un profilo per nickname o campus e selezionalo per
pubblicare o votare. `/` apre la ricerca e Invio sceglie il primo risultato.
Bozze di post e risposte restano nel browser anche chiudendo la scheda.

I commenti e le risposte usano lo stesso servizio push dell'API. Il Desk legge
`FCM_SERVICE_ACCOUNT_JSON` dal `.env`, oppure il file locale indicato da
`FCM_SERVICE_ACCOUNT_PATH`; può anche usare `FCM_PROJECT_ID` con le credenziali
Google dell'ambiente. Senza configurazione i commenti vengono salvati ma le push
sono disabilitate. L'esito dell'invio compare nel terminale del Desk.

## Statistiche

La scheda **Statistiche** mostra utenti registrati e nuovi, utenti attivi e
partecipanti, DAU/WAU/MAU, post, commenti, modifiche ai voti e tassi di partecipazione.
Puoi filtrare per oggi, 7 o 30 giorni e per campus; trovi anche un grafico e il
dettaglio giornaliero. **Aggiorna statistiche** rilegge i dati dal database.
Un utente attivo ha aperto l'app o svolto un'azione: non è un conteggio delle
singole aperture. Amministratori, alpha tester e profili gestiti dal Desk sono
esclusi. I giorni seguono il fuso Europe/Rome; DAU/WAU/MAU mantengono sempre le
proprie finestre di 1/7/30 giorni, indipendentemente dal periodo selezionato.

**Nuovi utenti e ritorni** distingue i nuovi iscritti attivi dagli utenti iscritti
prima del periodo che sono tornati. **Già registrati tornati oggi** esclude sempre
gli iscritti di oggi, anche selezionando 7 o 30 giorni. Mostra anche inattivi,
attivi senza partecipazione, utenti attivi in almeno due giorni, giorni attivi
medi e percentuali di attività, attivazione dei nuovi e ritorno dei già registrati.
Quest’ultima percentuale non è una retention a distanza fissa dall’iscrizione.
Le tabelle giornaliere distinguono iscritti quel giorno attivi e già registrati
tornati; quella per campus mostra i tornati nel periodo.

Le statistiche sono accessibili dalla sessione privata del Desk, senza
scegliere un profilo. Le aperture sono registrate da `POST /api/v1/activity/open`.

## Axooji

Se il checkout Android è altrove, usa:

```sh
go run ./cmd/utility/contentdesk -emoji-assets /percorso/axoo-kotlin/app/src/main/assets/axooji
```

Il pulsante **Emoji Axoo** (o **Emoji** nei commenti) apre pack e ricerca.
La selezione inserisce un codice come `:amtab/heart-eyes:` alla posizione del cursore.
Puoi copiarlo, ripeterlo o eliminarlo come testo. Alla pubblicazione il Desk converte
i codici in testo Unicode ed entità animate con gli stessi ID del catalogo Android.
I codici sconosciuti bloccano la pubblicazione con un errore; le normali emoji
incollate restano normali emoji. Il limite di 500 caratteri si applica al testo convertito.

Usa il catalogo della versione dell'app usata dai destinatari, così i client
riconoscono gli ID inviati.

Per aggiornare le anteprime dopo aver aggiunto emoji al catalogo, dalla radice
di axoo-server esegui `python3 scripts/generate_emoji_previews.py` (richiede CMake,
un compilatore C++ e Pillow), poi riavvia il Desk. Lo script legge le animazioni
e il renderer dal checkout Android senza modificarlo. Per un checkout altrove,
usa `--android-root /percorso/axoo-kotlin`. Genera WebP trasparenti da 128 pixel;
se il primo frame è vuoto, cerca un frame visibile nell'animazione.

## Profili gestiti

Il selettore **Profilo attivo** raggruppa i profili in **Uomini**, **Donne** e
**Altri profili** (quelli già presenti senza categoria). Il gruppo si sceglie
quando si crea un profilo, anche manualmente; non viene dedotto dal nickname o dall’avatar.

In **Contenuti → Genera profili di prova** scegli gruppo, campus e quantità (1–300).
Il Desk genera nickname unici e avatar in una singola transazione. Entrambi i gruppi usano nickname anonimi e vari: parole buffe, cibo, frasi universitarie e handle, come `panzeronauta` o `tesialdente`. I profili
sono alpha tester ed esclusi dalle statistiche reali.

I nickname alternano parole brevi, frasi universitarie, handle e combinazioni;
non vengono generate serie della stessa parola con suffissi numerici. Per
rinominare un gruppo già creato, usa `-rename-profiles NUMERO` insieme a
`-rename-profiles-since DATA_RFC3339`: il comando considera solo profili gestiti
alpha tester e rifiuta la modifica se il numero trovato non corrisponde. Cambia
solo il nickname e stampa ID, vecchio e nuovo nome per ogni profilo.
Il formato è quello del client: 3–24 caratteri, soltanto `a-z` e `0-9`, senza
underscore, spazi o trattini. Anche il modulo manuale applica questa regola.
`-repair-profile-nicknames NUMERO -rename-profiles-since DATA_RFC3339` corregge
soltanto i nickname non validi del gruppo, conservando quelli già conformi.

Per aggiungere profili con nickname unici nello stile di quelli esistenti:

```sh
go run ./cmd/utility/contentdesk -add-profiles 20 -profile-campus UUID_DEL_CAMPUS -profile-gender female
```

Usa `-profile-gender male` per Uomini o `female` per Donne. Premi **Aggiorna**
nel Desk per ricaricare l'elenco.
