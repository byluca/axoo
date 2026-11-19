# Sicurezza della community

La versione dei termini è `2026-10-07`.

Il server restituisce `terms_required` e `terms_version` nel bootstrap. L’accettazione usa `PUT users/me/terms` con versione e `adult: true`, conserva il momento della prima accettazione della versione e viene richiesta anche agli account esistenti. Il server impedisce pubblicazione e modifica senza accettazione. Il consenso si raccoglie nel form email e viene salvato dopo la verifica OTP. Le sessioni già attive senza consenso riusano il form per accettare, senza una card e senza richiedere un nuovo codice.

`PUT posts/:id/hidden` nasconde quel post solamente per l’utente autenticato. La tabella conserva la preferenza tra sessioni e dispositivi; feed Hot/New e contatore dei nuovi post la rispettano. Il post rimane accessibile tramite un collegamento diretto. È distinto da `moderation/actions` con azione `hide`, che rimuove globalmente il contenuto.

## Moderazione dal pannello

Le segnalazioni sono raggruppate per contenuto e ordinate dalla prima segnalazione più vecchia. Dal pannello il moderatore può rimuovere contenuti, lasciarli online, ripristinarli e gestire sospensioni o ban dell’autore. Non sono previste scadenze o rimozioni automatiche.

Ogni nuova segnalazione produce un evento nei log. `MODERATION_ALERT_WEBHOOK`, se configurato, riceve un avviso dopo il salvataggio con gli identificativi della segnalazione e del contenuto, senza testo del contenuto o email degli utenti. Un errore di consegna viene registrato.

Il ruolo moderatore è assegnabile con `cmd/utility/moderator` tramite UUID o
nickname. Segnalare, bloccare e nascondere per sé sono operazioni disponibili
agli utenti autenticati.

Il database usa `users.terms_version`, `users.terms_accepted_at` e
`user_hidden_posts` con chiavi utente/post, data e RLS.
