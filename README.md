# LogEZ

Offline-first fitness logger for Android — routines, live workout tracking, GPS runs/walks with maps, Health Connect wellness metrics, and analytics for any kind of fitness enthusiast.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Persistence | Room (single Gradle module, offline-first) |
| DI | Hilt |
| Architecture | MVVM + repository pattern (see [`documentation/ARCHITECTURE.md`](documentation/ARCHITECTURE.md)) |
| Min/target/compile SDK | 26 / 36 / 36 |

## Getting started

**Requirements:** Android Studio (latest stable) or a terminal with the Android SDK installed, and a JDK 17–21 (JDK 25+ breaks the Robolectric-based unit tests — point `JAVA_HOME` or Android Studio's bundled JBR at 17–21).

1. Clone the repo and open it in Android Studio, or work from the terminal with the Gradle wrapper (`./gradlew`, `gradlew.bat` on Windows) — never install Gradle globally.
2. Copy [`local.properties.example`](local.properties.example) to `local.properties` and fill in your Android SDK path. No map API key is needed: route maps load [OpenFreeMap](https://openfreemap.org/) tiles, which need no key or account.
3. For a signed release build only (not needed for debug, staging or tests), copy [`keystore.properties.example`](keystore.properties.example) to `keystore.properties` and point it at your own keystore, and set `logez.contactEmail` in `gradle.properties`: release packaging refuses to run while it is empty, because the address appears in the privacy policy.

### Build & run

| What you want | Command |
|---|---|
| Debug APK (unsigned, installable) | `./gradlew :app:assembleDebug` |
| Install on a running emulator/device | `./gradlew :app:installDebug` |
| Release-equivalent APK for testing (R8, shrunk resources, debug key, installs beside other copies as "LogEZ staging") | `./gradlew :app:assembleStaging` |
| Signed release APK | `./gradlew :app:assembleRelease` |
| Signed release bundle for Google Play | `./gradlew :app:bundleRelease` |
| Release lint (CI runs this; the build fails on any NewApi error) | `./gradlew :app:lintRelease` |

Output lands under `app/build/outputs/apk/{debug,staging,release}/` and `app/build/outputs/bundle/release/`. Release builds run R8; the rules are in `app/proguard-rules.pro`.

The privacy policy lives in `feature/privacy/PrivacyPolicyContent.kt` and is also published as `site/privacy/index.html` (GitHub Pages, via `.github/workflows/pages.yml`). A unit test fails when the two drift; its failure message says where the regenerated page is. Open-source notices live in `app/src/main/assets/licenses/` (update `notices.json` when adding a dependency).

### Tests

```bash
./gradlew :app:testDebugUnitTest
```

Test reports land in `app/build/reports/tests/`. Tests live in `app/src/test/`, use JUnit4 + Robolectric, and favor hand-written in-memory fakes (`app/src/test/java/com/enil/logez/fakes/`) over a mocking framework — match that pattern for new tests.

There is currently no instrumented/Compose UI test suite (`app/src/androidTest/` doesn't exist yet) — new UI-behavior coverage should go there if you add it.

## More docs

- [`documentation/ARCHITECTURE.md`](documentation/ARCHITECTURE.md) — package layout, data flow, and where things live.
- [`documentation/adr/`](documentation/adr/) — architecture decision records for the choices worth knowing before you touch the related code.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — commit/branch conventions and how this codebase likes its tests written.

## License

All rights reserved — see [`LICENSE`](LICENSE).
