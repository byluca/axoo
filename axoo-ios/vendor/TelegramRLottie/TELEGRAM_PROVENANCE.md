# Telegram rlottie provenance

Imported on: 2026-05-14

Source repository: https://github.com/DrKLO/Telegram

Pinned commit:

```text
009e97356f966bb81eceba113d210230bf383122
```

Commit metadata verified on 2026-05-14:

- commit message: `update to 12.6.4 (6666)`
- commit date: 2026-04-06
- commit URL: https://github.com/DrKLO/Telegram/commit/009e97356f966bb81eceba113d210230bf383122

Imported subtree:

```text
TMessagesProj/jni/rlottie
```

Destination:

```text
app/src/main/cpp/rlottie_telegram
```

Local integration files added by Axoo:

- `CMakeLists.txt`
- `TELEGRAM_PROVENANCE.md`

Licenses retained from the imported subtree:

- `licenses/COPYING.FTL`
- `licenses/COPYING.LGPL`
- `licenses/COPYING.PIX`
- `licenses/COPYING.RPD`
- `licenses/COPYING.STB`

The root Telegram repository license was also copied to:

```text
LICENSE
```

Integration status:

- This is Axoo's only native RLottie backend.
- Rendering uses a mutex per animation instance and two Kotlin render workers by default.
- `RLottieConcurrencyTest` covers concurrent rendering across distinct and shared animation instances.
