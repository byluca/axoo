# Foto nei post

Deploy del server prima della build Android con il pulsante Foto. Non servono
migrazioni SQL: si riusa il campo JSON `posts.media`, con `type: "photo"`.

## Storage e caricamento

- Si usano `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY`, gia presenti sul server.
  La chiave service role non viene mai inviata al client.
- All'avvio il server crea, se assente, il bucket `post-images`: lettura pubblica,
  JPEG, limite 3 MB. Non aggiungere policy di scrittura pubbliche.
- Le foto sono accessibili a chi conosce il loro URL: non sono allegati privati.
- `POST /api/v1/media/photos`, autenticato, accetta un multipart con campo `photo`.
  Limite 12 richieste/minuto per utente per istanza del server.
- Android prepara un JPEG fino a 1600 pixel, applica l'orientamento e rimuove i
  metadati. Il server verifica JPEG, dimensioni (massimo 2048 pixel) e dimensione
  del file, quindi ricodifica per rimuovere anche i metadati di client alterati.
- La risposta e un `ImageMeta` da passare alla creazione/modifica del post.
  Il server verifica che l'oggetto esista e appartenga all'autore del post.
  Il testo rimane obbligatorio; una sola foto, in alternativa a GIF/meme.

## Tentativi successivi e pulizia

Android mantiene la bozza in memoria in caso di errore e riusa un caricamento
gia riuscito quando si riprova a pubblicare. Non e un salvataggio persistente
della bozza dopo la chiusura del processo.

Il server cerca ogni 5 minuti fino a 20 foto caricate da oltre 24 ore e non
referenziate da post attivi, comprese foto sostituite e post eliminati. Le
elimina tramite Storage API, non modificando direttamente `storage.objects`.
Un lock transazionale condiviso con la pubblicazione evita gare con la pulizia.

La pulizia e opportunistica: su Cloud Run con CPU limitata alle richieste o
istanze a zero non ha una scadenza garantita. Un job schedulato separato sarebbe
necessario solo per garantire tempi precisi. Le cache pubbliche possono
continuare a servire un'immagine eliminata fino alla loro scadenza.

## Verifica dopo il deploy

1. Controllare bucket `post-images` e log `photo bucket` / `photo cleanup`.
2. Selezionare una foto dal telefono, controllare orientamento e anteprima,
   pubblicarla e verificarla in home e dettaglio.
3. Provare rimozione, sostituzione, modifica del post e un tentativo senza rete.
4. Verificare che GIF/meme continuino a funzionare.

I test automatici coprono preparazione/validazione server, chiamate Storage,
errori e riuso del caricamento nel ViewModel. Non sostituiscono il test su
telefono e l'integrazione con Supabase reale.

Con `AXOO_PHOTOS_TEST_DB_URL` verso PostgreSQL locale dedicato,
`go test -race ./internal/media` verifica anche che le foto dei post attivi
restino riutilizzabili e che le foto orfane o di post eliminati siano pulite.
Il test crea ed elimina un database temporaneo e non richiede altre tabelle di funzionalità.
