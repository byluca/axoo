# Strumenti di manutenzione

Eseguire i comandi dalla radice di `axoo-server`. Le configurazioni private
restano nel `.env` ignorato da Git; i loro valori non vanno copiati nei documenti.

## Meme e Content Desk

```sh
python3 scripts/upload_memes.py --folder "/percorso/immagini" --category italia
```

Il comando mostra soltanto file e destinazioni, senza richieste di rete.
Le categorie sono `italia`, `world`, `tv`, `musica`, `videogames`, `sport`.
Supporta JPG/JPEG, PNG, WebP e GIF; non attraversa sottocartelle. `--apply`
carica gli originali usando la cache di sette giorni e sostituisce gli oggetti
con lo stesso nome.

```sh
python3 scripts/sync_meme_previews.py
python3 scripts/generate_emoji_previews.py
```

`sync_meme_previews.py` legge lo storage e prepara rendition e catalogo;
soltanto `--apply` li pubblica. Consultare `--help` per gli aggiornamenti cache.
Richiede ImageMagick (`magick`). `generate_emoji_previews.py` genera le WebP
incorporate dal Content Desk, leggendo catalogo e renderer Android: richiede
CMake, compilatore C++ e Pillow. Vedere il [README del Desk](../cmd/utility/contentdesk/README.md).
Il pannello si avvia direttamente con `go run ./cmd/utility/contentdesk`.

## Account alpha

- `go run ./cmd/utility/alphatester`: gestione di un tester esplicito.
- `go run ./cmd/utility/alphapassword`: configurazione interattiva della password alpha.

## Verifiche locali

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
go test -race ./...
```

I test dell'uploader usano storage simulato e verificano anteprima senza rete,
tipi MIME, cache e rifiuto di configurazioni mancanti.
