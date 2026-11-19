# Notifiche in-app

Il server salva la notifica nella tabella `notifications` nella stessa
transazione che crea il commento.

Ogni commento a un post o risposta a un commento genera una notifica per il
destinatario, anche senza dispositivi push registrati. Non vengono notificati
l'autore stesso, gli autori bloccati o contenuti nascosti/rimossi. Salvataggio e
push condividono le regole di visibilità. Il vincolo destinatario/commento evita
duplicati; il rollback del commento annulla anche la notifica.

Le API richiedono la sessione autenticata e usano sempre il suo destinatario:

- `GET /notifications?cursor=...&limit=20`: elenco dal più recente, `next_cursor`,
  `seen_through` e `unread_count`. Gli ID sono stringhe numeriche.
- `GET /notifications/unread-count`: conteggio delle notifiche non viste visibili.
- `POST /notifications/seen` con `{"seen_through":"123"}`: marca come viste le
  notifiche del destinatario fino al limite ricevuto e restituisce il conteggio.
  Le nuove notifiche con ID successivo restano non viste.

La lista risolve il nickname corrente dell'autore. Le push riportano il nickname
al momento dell'invio, senza il testo del commento: “Giulia ha commentato il tuo
post” oppure “Giulia ha risposto al tuo commento”. Il payload `message` permette
ad Android di usare lo stesso testo anche in primo piano.

Per verificare schema, transazioni, deduplicazione, paginazione, lettura,
visibilità e cancellazione, usare un PostgreSQL di test e impostare
`AXOO_NOTIFICATIONS_TEST_DB_URL` durante `go test ./internal/notifications -v`.
Il test crea uno schema isolato dentro una transazione e lo annulla al termine.

## Apprezzamenti

- `post_appreciation`: 10 e 25 voti positivi.
- `comment_appreciation`: 5 voti positivi.

Si usa il contatore dei voti positivi mostrato dall'app, senza sottrarre quelli
negativi. Solo un voto positivo nuovo o modificato che porta il contenuto alla
soglia può registrare il traguardo. Non viene ricostruito lo storico. Il lock
transazionale per contenuto serializza i voti simultanei e gli indici univoci
mantengono una sola notifica per traguardo, anche dopo rimozioni e nuovi voti.
Il rollback annulla sia il voto sia l'eventuale notifica.

Gli apprezzamenti sono aggregati: non mostrano il nome del singolo votante.
`actor_id` contiene l'autore destinatario per mantenere compatibile il formato
esistente; `comment_id` è una stringa vuota per i traguardi dei post. Anteprima,
badge, paginazione e lettura usano il sistema esistente. Contenuti rimossi,
nascosti, di un autore del post bloccato dal destinatario o appartenenti a un
post nascosto dal destinatario restano esclusi. Gli account bannati o sospesi
non ricevono nuovi traguardi.

Questi due tipi sono **solo in-app**: nessuna chiamata al servizio push e
nessun nuovo job pianificato. Android e iOS usano le icone dei voti positivi;
il tocco apre il post o evidenzia il commento apprezzato.
