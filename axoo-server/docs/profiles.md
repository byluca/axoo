# Profili e bio

Lo schema usa `users.bio`, `users.bio_entities` e il tipo di segnalazione `user`.

`bio_entities` conserva le emoji personalizzate con gli stessi offset Unicode di post e commenti. È una lista vuota per le bio esistenti. `PUT /users/me/bio` accetta al massimo 20 entità emoji, verifica i loro intervalli e salva testo ed entità insieme; cancellare la bio cancella anche le entità. Le risposte del profilo proprio e pubblico includono `bio_entities`.

- `GET /users/:userId`: avatar, nickname, bio e università. Richiede autenticazione e appartenenza alla stessa città. Utenti inesistenti, sospesi, bannati o bloccati dal richiedente restituiscono 404. Il blocco resta una preferenza personale a senso unico, come nel feed.
- `PUT /users/me/bio` con `{"bio":"..."}`: modifica la propria bio e restituisce il profilo aggiornato. La bio è facoltativa, viene ripulita dagli spazi esterni e accetta fino a 200 code point Unicode; `""` la cancella. Campi mancanti, null e testi troppo lunghi restituiscono 400.
- `GET /users/me` include anche `bio`.
- Le segnalazioni accettano `target_type: "user"` e compaiono nella coda della città del profilo. Il moderatore può archiviarle oppure sospendere/bannare l’account; un profilo sospeso o bannato non è consultabile.

Il profilo altrui non contiene email, ruoli, data di iscrizione o stato di moderazione. Non espone lo storico di post e commenti.

Verifica su PostgreSQL locale con `AXOO_USERS_TEST_DB_URL` e
`go test ./internal/users -run TestPublicProfilesDatabase -count=1`.
