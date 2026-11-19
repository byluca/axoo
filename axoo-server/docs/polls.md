# Sondaggi

Il database conserva le opzioni dei sondaggi in `posts.poll_options` e i voti
in `poll_votes`. Un trigger protegge testo, entità emoji, flair, media e opzioni
dei post con sondaggio. Eliminazione e moderazione restano disponibili.

`POST /posts` accetta il campo opzionale `poll`:

```json
{"body":"Cosa scegli?","client_id":"UUID","poll":{"options":[{"text":"Caffè ☕","entities":[]},{"text":"Tè","entities":[]}]}}
```

Sono richieste da 2 a 4 opzioni non vuote, fino a 100 code point ciascuna. Le entità
emoji seguono lo stesso contratto, i controlli sul testo e gli sblocchi dei post.
Gli ID delle opzioni sono generati dal server. L'idempotenza confronta anche
opzioni, ordine ed entità emoji, indipendentemente dagli ID generati.

`PUT /posts/{id}/poll/vote` accetta `{"option_id":"UUID"}` e restituisce `{"poll":...}`.
Il voto è singolo e definitivo: ripetere la stessa scelta restituisce lo stesso
stato; cambiarla restituisce 409. Un lock sul post e la chiave unica post/utente
impediscono duplicazioni anche con richieste concorrenti. Post eliminati, nascosti
o bloccati non accettano voti.

Le letture di feed, dettaglio e post personali includono `poll` con opzioni,
`total_votes` e la scelta personale `my_option_id`, se presente. Prima del voto
sono nascosti conteggi per opzione e avatar; il totale resta visibile. Dopo il voto
ogni opzione comprende `votes` e fino a tre `voters` con `user_id` e `avatar_config`.
Gli avatar bloccati sono esclusi dalle anteprime. Il caricamento è in batch per
la pagina, senza una query per post o per votante.

Eseguire `go test ./...` e `go vet ./...`. Per i test PostgreSQL usare un database
locale dedicato con `AXOO_POLL_TEST_DB_URL` e `go test -race ./internal/posts -run Poll`.
I test creano schemi isolati e verificano concorrenza, retry, risposta JSON,
avatar e immutabilità anche fuori dall’API.
