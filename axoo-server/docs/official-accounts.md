# Account ufficiali

Axoo e AUP usano `avatar_config.official_account` (`axoo` o `aup`), assegnato solo
con credenziali amministrative. Le app riusano questo metadato già presente nei
profili, post e commenti per mostrare avatar ufficiale e spunta. Non richiede nuove
colonne o una migrazione. La verifica non assegna permessi di moderazione.

La API rifiuta il campo riservato negli aggiornamenti avatar, impedisce di
sostituire gli avatar ufficiali e riserva i nickname. La tabella `users` ha RLS
abilitata senza policy pubbliche: i client non possono modificarla tramite REST.
I client caricano gli avatar ufficiali da `memes/avatars/official/v1/`
su Supabase Storage.

AUP appartiene a PoliBa e usa l'identificativo `aup@account.example`: è un dominio
riservato, non una casella email. La password individuale resta in Supabase Auth;
non usa la password condivisa alpha e non invia OTP.
`auth/email-eligibility` restituisce `login_method: account_password` per AUP e
per gli account admin dedicati descritti in [Eventi](events.md).
Il normale bootstrap e i controlli su termini, sospensioni e ban restano attivi.

Per la rotazione della password individuale si usano gli strumenti di
amministrazione di Supabase Auth.
