# Alarme

An Android alarm clock: reliable scheduling & ring
delivery, wake-up missions, snooze/wake-up-check, skip-once, quick alarms,
ringtones, reliability screens (anti-defeat), local backup, and a next-alarm
widget. All features free — no ads, no analytics, no paywall.

## Requirements

- JDK 17
- Android SDK: platform 37, build-tools 36.0.0 (`android sdk install platforms/android-37.0 build-tools/36.0.0`), `ANDROID_HOME` set
- For instrumented tests: an emulator or a device

## Build & test

```bash
./gradlew :app:assembleDebug          # debug APK (pseudo-locales enabled)
scripts/gate.sh                       # ktfmtCheck + detekt + lint + unit tests
./gradlew :app:connectedDebugAndroidTest  # instrumented: UI smoke + a11y checks + Room on device
./gradlew :app:assembleRelease        # minified, shrunk, installable APK
```

Unit tests run on the JVM (Robolectric for anything touching `android.*`); the instrumented suite
lives in `app/src/androidTest` and starts the real activity with the real dependency graph, with
Android's accessibility test framework enabled so hierarchy violations fail the run.

## Release

`assembleRelease` runs R8 in full mode with resource shrinking (`app/proguard-rules.pro` holds the
keep rules: the model package, because backups embed kotlinx.serialization's fully-qualified
discriminators, and the receivers the system resolves by name).

Signing is read from an untracked `keystore.properties` at the repository root:

```properties
storeFile=alarme-release.jks
storePassword=…
keyAlias=alarme
keyPassword=…
```

Without that file the release build is signed with the debug key so it stays installable.

Pushing a `vX.Y.Z` tag (`.github/workflows/release.yml`) runs the quality gate, builds the signed
release APK, and publishes it to a GitHub release. The tag supplies `versionName` and derives
`versionCode` as `major*10000 + minor*100 + patch`. CI needs the repository secrets
`KEYSTORE_BASE64` (base64 of the `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

A hand-written baseline profile (`app/src/main/baseline-prof.txt`) covers startup and the ring
path; AGP compiles it into `assets/dexopt/baseline.prof` and `androidx.profileinstaller` installs it
on first run. The release APK is ~74 MB, dominated by ML Kit (barcode + image labelling) and CameraX.

## Distribution

There is no store presence and no signing service: sideload the release APK
(`adb install -r app-release.apk`, or share the file). Everything works offline; the only network
use is ML Kit's bundled models. Users who move to a new device export a backup file and restore it
there (`Settings → Backup & restore`), which also carries imported/recorded ringtones.

## Modules

```
app                       MainActivity, navigation, DI glue, Glance widget
core/model                pure Kotlin: Alarm, Mission (sealed), calendar math
core/common               Clock/Dispatcher abstractions, AppResult
core/data                 Room DB, DataStore settings, repositories, backup
core/designsystem         M3 theme, dynamic color, shared composables
core/alarm-engine         scheduler, receivers, FGS, ring state machine
core/audio                players, volume lock, TTS, audio focus
core/sensors              shake, squat (light/proximity), step counter
feature/alarmlist         list, next-alarm header, skip/undo, quick alarms
feature/editor            alarm editor (time, repeat, missions, options)
feature/ring              takeover activity, ring UI, mission screens
feature/ringtones         picker, SAF import+copy, recorder
feature/settings          settings, permission center, reliability, diagnostics
feature/backup            export/import ZIP (JSON db + audio files)
```
