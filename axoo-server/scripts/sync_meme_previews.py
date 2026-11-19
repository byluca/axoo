#!/usr/bin/env python3
"""Genera le rendition dei meme e pubblica il manifest usato dal picker.

Default: dry-run. Usa --apply per caricare blur, preview da griglia e catalogo.
Gli asset derivati sono content-addressed, quindi possono avere cache immutable.
"""

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import mimetypes
import os
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
ENV_PATH = ROOT / ".env"
BUCKET = "memes"
CATEGORIES = ["italia", "world", "tv", "musica", "videogames", "sport"]
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".gif"}
IMMUTABLE_CACHE_SECONDS = "31536000"
ORIGINAL_CACHE_SECONDS = "604800"
CATALOG_CACHE_SECONDS = "300"
CACHE_REFRESH_WORKERS = 4
REQUEST_ATTEMPTS = 4


def load_env() -> None:
    if not ENV_PATH.exists():
        return
    for raw_line in ENV_PATH.read_text().splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if separator:
            os.environ.setdefault(key.strip(), value.strip())


load_env()
SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
SERVICE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
API_URL = f"{SUPABASE_URL}/storage/v1/object"


def request(url: str, *, method: str = "GET", data: bytes | None = None, headers: dict | None = None) -> bytes:
    for attempt in range(REQUEST_ATTEMPTS):
        req = urllib.request.Request(url, method=method, data=data)
        req.add_header("Authorization", f"Bearer {SERVICE_KEY}")
        req.add_header("apikey", SERVICE_KEY)
        for key, value in (headers or {}).items():
            req.add_header(key, value)
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code not in {408, 429, 500, 502, 503, 504} or attempt == REQUEST_ATTEMPTS - 1:
                raise
        except (TimeoutError, urllib.error.URLError):
            if attempt == REQUEST_ATTEMPTS - 1:
                raise
        time.sleep(2 ** attempt)
    raise RuntimeError("richiesta Supabase non riuscita")


def object_url(path: str, public: bool = False) -> str:
    encoded = urllib.parse.quote(path, safe="/")
    prefix = "public/" if public else ""
    return f"{API_URL}/{prefix}{BUCKET}/{encoded}"


def list_files(prefix: str) -> set[str]:
    payload = json.dumps({"prefix": prefix, "limit": 1000, "sortBy": {"column": "name", "order": "asc"}}).encode()
    raw = request(
        f"{API_URL}/list/{BUCKET}",
        method="POST",
        data=payload,
        headers={"Content-Type": "application/json"},
    )
    return {item["name"] for item in json.loads(raw) if item.get("name")}


def upload(path: str, data: bytes, mime_type: str, cache_seconds: str) -> None:
    request(
        object_url(path),
        method="POST",
        data=data,
        headers={
            "Content-Type": mime_type,
            # Supabase Storage expects the same format used by storage-js:
            # `max-age=<seconds>`, not a complete HTTP Cache-Control value.
            "Cache-Control": f"max-age={cache_seconds}",
            "x-upsert": "true",
        },
    )


def stored_cache_control(path: str) -> str:
    encoded_path = urllib.parse.quote(path, safe="/")
    raw = request(f"{API_URL}/info/{BUCKET}/{encoded_path}")
    return str(json.loads(raw).get("cache_control") or "")


def has_cache_seconds(cache_control: str, expected_seconds: str) -> bool:
    return any(
        directive.strip() == f"max-age={expected_seconds}"
        for directive in cache_control.split(",")
    )


def refresh_public_object_cache(path: str, mime_type: str, cache_seconds: str) -> str:
    if has_cache_seconds(stored_cache_control(path), cache_seconds):
        return path
    data = request(object_url(path, public=True))
    upload(path, data, mime_type, cache_seconds)
    stored = stored_cache_control(path)
    if not has_cache_seconds(stored, cache_seconds):
        raise RuntimeError(f"{path}: cacheControl salvato {stored!r}, atteso {cache_seconds!r}")
    return path


def refresh_cache_control() -> None:
    targets: list[tuple[str, str, str]] = []
    for category in CATEGORIES:
        for folder in ("_grid", "_previews"):
            prefix = f"{folder}/{category}"
            targets.extend(
                (f"{prefix}/{name}", "image/webp", IMMUTABLE_CACHE_SECONDS)
                for name in sorted(list_files(f"{prefix}/"))
                if Path(name).suffix.lower() == ".webp"
            )
    targets.append(("_catalog/memes.json", "application/json", CATALOG_CACHE_SECONDS))

    with ThreadPoolExecutor(max_workers=CACHE_REFRESH_WORKERS) as executor:
        refreshed = executor.map(lambda target: refresh_public_object_cache(*target), targets)
        for index, path in enumerate(refreshed, start=1):
            if index % 50 == 0 or index == len(targets):
                print(f"Cache aggiornata: {index}/{len(targets)} ({path})")


def identify_dimensions(source: Path) -> tuple[int, int]:
    result = subprocess.run(
        ["magick", f"{source}[0]", "-auto-orient", "-format", "%w %h", "info:"],
        check=True,
        capture_output=True,
        text=True,
    )
    width, height = result.stdout.strip().split()
    return int(width), int(height)


def generate_webp(source: Path, destination: Path, size: str, quality: int, blur: bool) -> None:
    command = ["magick", f"{source}[0]", "-auto-orient", "-thumbnail", size]
    if blur:
        command += ["-blur", "0x1.2"]
    command += [
        "-strip",
        "-quality", str(quality),
        "-define", "webp:method=6",
        str(destination),
    ]
    subprocess.run(command, check=True, capture_output=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="carica rendition e manifest su Supabase")
    parser.add_argument(
        "--refresh-original-cache",
        action="store_true",
        help="ri-carica gli originali identici con Cache-Control di 7 giorni",
    )
    parser.add_argument(
        "--refresh-cache-control",
        action="store_true",
        help="ri-carica preview e catalogo esistenti con gli header Cache-Control corretti",
    )
    args = parser.parse_args()

    if not SUPABASE_URL or not SERVICE_KEY:
        raise SystemExit("SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY mancanti in .env")
    if args.refresh_cache_control:
        if not args.apply:
            raise SystemExit("--refresh-cache-control richiede --apply")
        refresh_cache_control()
        return
    if shutil.which("magick") is None:
        raise SystemExit("ImageMagick 'magick' non trovato")

    manifest_items: list[dict] = []
    generated = 0
    skipped = 0
    with tempfile.TemporaryDirectory(prefix="axoo-meme-renditions-") as temp:
        temp_dir = Path(temp)
        for category in CATEGORIES:
            originals = sorted(
                name for name in list_files(f"{category}/")
                if Path(name).suffix.lower() in IMAGE_SUFFIXES
            )
            blur_files = list_files(f"_previews/{category}/")
            grid_files = list_files(f"_grid/{category}/")
            print(f"{category}: {len(originals)} originali")

            for index, name in enumerate(originals, start=1):
                original_path = f"{category}/{name}"
                source_data = request(object_url(original_path, public=True))
                digest = hashlib.sha256(source_data).hexdigest()[:12]
                stem = Path(name).stem
                blur_name = f"{stem}-{digest}.webp"
                grid_name = f"{stem}-{digest}.webp"
                blur_path = f"_previews/{category}/{blur_name}"
                grid_path = f"_grid/{category}/{grid_name}"

                source = temp_dir / f"source-{index}{Path(name).suffix}"
                blur = temp_dir / f"blur-{index}.webp"
                grid = temp_dir / f"grid-{index}.webp"
                source.write_bytes(source_data)
                width, height = identify_dimensions(source)

                needs_blur = blur_name not in blur_files
                needs_grid = grid_name not in grid_files
                if needs_blur:
                    generate_webp(source, blur, "32x32>", 35, blur=True)
                if needs_grid:
                    generate_webp(source, grid, "480x480>", 76, blur=False)

                if args.apply:
                    if needs_blur:
                        upload(blur_path, blur.read_bytes(), "image/webp", IMMUTABLE_CACHE_SECONDS)
                    if needs_grid:
                        upload(grid_path, grid.read_bytes(), "image/webp", IMMUTABLE_CACHE_SECONDS)
                    if args.refresh_original_cache:
                        mime_type = mimetypes.guess_type(name)[0] or "application/octet-stream"
                        upload(original_path, source_data, mime_type, ORIGINAL_CACHE_SECONDS)

                if needs_blur or needs_grid:
                    generated += 1
                    print(f"  [GEN] {name} -> {grid_name}")
                else:
                    skipped += 1

                manifest_items.append({
                    "path": original_path,
                    "category": category,
                    "width": width,
                    "height": height,
                    "grid_path": grid_path,
                    "preview_path": blur_path,
                })

    manifest = {
        "version": datetime.now(timezone.utc).isoformat(),
        "items": manifest_items,
    }
    manifest_data = json.dumps(manifest, ensure_ascii=False, separators=(",", ":")).encode()
    if args.apply:
        upload("_catalog/memes.json", manifest_data, "application/json", CATALOG_CACHE_SECONDS)

    mode = "APPLICATO" if args.apply else "DRY-RUN"
    print(f"{mode}: {len(manifest_items)} meme, {generated} rendition da generare, {skipped} già aggiornati")
    if not args.apply:
        print("Per applicare: python3 scripts/sync_meme_previews.py --apply --refresh-original-cache")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")
        raise SystemExit(f"Supabase HTTP {error.code}: {detail}") from error
