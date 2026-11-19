# Accesso alpha con password comune

Gli indirizzi presenti in `alpha_testers`, attivi e non scaduti, possono accedere
con la password comune. `POST /api/v1/auth/email-eligibility` restituisce
`login_method: "password"` per questi indirizzi e `"otp"` per gli altri.

`POST /api/v1/auth/alpha-session` riceve email e password, ricontrolla l'invito
e restituisce una normale sessione Supabase con refresh token. La generazione
e lo scambio del token monouso avvengono sul server, senza inviare email.
Il middleware esistente continua a controllare revoca, scadenza, ban e termini.

La password non è inclusa nel codice o nelle app. È salvata solo come hash bcrypt
in `alpha_login_settings`, con RLS e accesso revocato ai ruoli pubblici.
Per configurarla o cambiarla, dalla radice del server:

```sh
go run ./cmd/utility/alphapassword
```

Il comando legge la password da terminale con input nascosto e aggiorna
la tabella esistente `alpha_login_settings`. I nuovi account alpha usano
automaticamente la password comune.

Gli account amministratori e moderatori mantengono l'accesso personale. L'accesso
con password comune a questi account richiede un'abilitazione esplicita tramite
`allow_privileged` nella configurazione privata.
