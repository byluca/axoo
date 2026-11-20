# Axoo iOS

The Axoo iOS app uses Swift, UIKit, and the Telegram rlottie renderer. It supports iOS 16 and later.

## Build and test

Use macOS with Xcode 26.2 or newer and XcodeGen 2.44 or newer. From `axoo-ios/`:

```sh
brew install xcodegen
cp configuration/Local.xcconfig.example configuration/Local.xcconfig
xcodegen generate
open Axoo.xcodeproj
```

Select the **Axoo** scheme and a simulator. Press **Run** to build or **Cmd+U**
to run the tests. Edit `project.yml` to change the generated project.

Debug builds work without credentials. For login, set `AXOO_SUPABASE_PUBLISHABLE_KEY`
in `configuration/Local.xcconfig`. Set `AXOO_API_URL` and `AXOO_SUPABASE_URL`
for an HTTPS development instance and `AXOO_KLIPY_API_KEY` for GIFs and memes.
The [example config](configuration/Local.xcconfig.example) also lists the signing settings.

For push notifications, add `app/GoogleService-Info.plist` and configure APNs.
Set `AXOO_REQUIRE_FIREBASE_CONFIG = NO` to build a Release without push.

App code is in `app/`, tests are in `tests/` and `uitests/`, and the C++ bridge
is in `native/`.

## License

Axoo’s original code is licensed under [AGPL-3.0-only](LICENSE).
See the [third-party notices](THIRD_PARTY_NOTICES.md).
