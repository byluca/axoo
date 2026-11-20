# Axoo Android

The Axoo Android app uses Kotlin, Android Views, and the Telegram rlottie renderer.

## Build and test

Install JDK 21, Android SDK 36, NDK `27.0.12077973`, and CMake 3.22.1 or newer. From `axoo-kotlin/`:

```sh
cp local.properties.example local.properties
# Set sdk.dir in local.properties.
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Debug builds work without credentials. For login, set `supabase.publishable.key`.
For a development instance, also set `axoo.api.url` and `supabase.url` to HTTPS URLs.
For GIFs and memes, set `klipy.api.key`. For push notifications, add
`app/google-services.json`.

App code is in `app/src/main/kotlin/com/axoo`, the C++ renderer is in
`app/src/main/cpp`, and tests are in `app/src/test` and `app/src/androidTest`.

## License

Axoo’s original code is licensed under [AGPL-3.0-only](LICENSE).
See the [third-party notices](THIRD_PARTY_NOTICES.md).
