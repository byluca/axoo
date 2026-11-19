#!/usr/bin/env python3
"""Carica una cartella di meme in una categoria. Senza --apply mostra solo il piano."""
import argparse
import mimetypes
from pathlib import Path

import sync_meme_previews as storage


def upload_folder(folder: Path, category: str, apply: bool = False) -> int:
    if category not in storage.CATEGORIES:
        raise ValueError("categoria non valida")
    if not folder.is_dir():
        raise ValueError("la cartella sorgente non esiste")
    files = sorted(path for path in folder.iterdir()
                   if path.is_file() and path.suffix.lower() in storage.IMAGE_SUFFIXES)
    if not files:
        raise ValueError("nessuna immagine supportata nella cartella")
    if apply and (not storage.SUPABASE_URL or not storage.SERVICE_KEY):
        raise ValueError("configura SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY nel .env del server")
    print(f"{'Caricamento' if apply else 'Anteprima'}: {len(files)} file nella categoria {category}.")
    for path in files:
        destination = f"{category}/{path.name}"
        print(f"{path.name} → {destination} ({path.stat().st_size} byte)")
        if apply:
            mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            storage.upload(destination, path.read_bytes(), mime, storage.ORIGINAL_CACHE_SECONDS)
    if not apply:
        print("Nessuna richiesta di rete eseguita. Aggiungi --apply per caricare o sostituire questi file.")
    else:
        print("Originali caricati. Esegui sync_meme_previews.py per aggiornare anteprime e catalogo.")
    return len(files)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", type=Path, required=True, help="cartella locale con JPG, PNG, WebP o GIF")
    parser.add_argument("--category", choices=storage.CATEGORIES, required=True)
    parser.add_argument("--apply", action="store_true", help="carica gli originali; sostituisce oggetti con lo stesso nome")
    args = parser.parse_args()
    try:
        upload_folder(args.folder, args.category, args.apply)
    except (ValueError, OSError) as error:
        parser.exit(1, f"Errore: {error}\n")


if __name__ == "__main__":
    main()
