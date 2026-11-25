# Maintenance scripts

Run these commands from `axoo-server/`. Use your local `.env` for Supabase credentials.

## Memes

```sh
python3 scripts/upload_memes.py --folder /path/to/images --category italia
python3 scripts/sync_meme_previews.py
```

The uploader previews the changes. Add `--apply` to upload images and replace
existing files with the same name. The sync command uses ImageMagick (`magick`)
to prepare previews and the catalog. Add `--apply` to publish them.

## Axooji previews

```sh
python3 scripts/generate_emoji_previews.py
```

This needs CMake, a C++ compiler, and Pillow. It reads the Android checkout and
rebuilds the [Content Desk](../cmd/utility/contentdesk/README.md) previews.
Pass `--android-root` if the Android checkout is elsewhere.

## Alpha accounts

- `go run ./cmd/utility/alphatester` adds or revokes a tester.
- `go run ./cmd/utility/alphapassword` sets the shared alpha password.

## Remove campus events

After replacing the old API on an existing installation, run as the database owner.
This removes the event table, its records, and the legacy event permission.
The new API's regular photo cleanup removes unreferenced event posters.

```sh
psql 'YOUR_POSTGRES_URL' -v ON_ERROR_STOP=1 -f scripts/remove-campus-events.sql
```

## Test

```sh
python3 -m unittest discover -s scripts -p 'test_*.py'
```
