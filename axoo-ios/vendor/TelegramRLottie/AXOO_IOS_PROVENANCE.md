# Axoo iOS native renderer

Copied from Axoo Android `78bd3bb7`, `app/src/main/cpp/lottie_engine`.
Upstream Telegram revision and notices are retained in `TELEGRAM_PROVENANCE.md`,
`LICENSE`, per-file headers and `licenses/`. Original file hashes are recorded
in `ANDROID_SNAPSHOT.json`. This snapshot contains Axoo's local Android changes.

iOS patch: explicitly include `<limits>` in `src/vector/vrle.cpp`.
The iOS target uses the portable C++ source list from Android's CMake target.
Android JNI, bitmap/log libraries, linker scripts and NEON assembly are excluded.
The C boundary, gzip decoder and Swift lifecycle code live outside this vendor
directory. License notices are also bundled under `licenses/TelegramRLottie`.
