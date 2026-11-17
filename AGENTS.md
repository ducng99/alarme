# AGENTS.md

Alarme is an Android alarm clock built with Kotlin, Compose Material 3, Hilt, Room, DataStore, Media3, CameraX, ML Kit, and Glance. All features are free; the app has no network permission and no analytics.

## Environment

- JDK 17 (`JAVA_HOME` must point to it; JDK 27 on this machine is too new for the Gradle daemon).
- Android SDK: `ANDROID_HOME=/opt/android-sdk`, platform 37, build-tools 36.0.0.
- Gradle wrapper 9.6.0. `minSdk 35`, `compileSdk/targetSdk 37`, `applicationId dev.tomng.alarme`.
- Reference material lives outside this repo: `../.pi/plans/alarme-android-app.md` (build plan) and `../findings/01…06.md` (behavioral spec). `README.md` covers release/signing/baseline-profile details.

## Commands

```bash
./gradlew :app:assembleDebug                 # debug APK (pseudo-locales enabled)
scripts/gate.sh                              # ktfmtCheck + detekt + lintDebug + testDebugUnitTest
./gradlew ktfmtFormat                        # auto-format Kotlin
./gradlew detekt
./gradlew lintDebug
./gradlew :app:connectedDebugAndroidTest     # instrumented tests (emulator/device required)
./gradlew :app:assembleRelease               # R8 full mode + resource shrinking
```

Unit tests are JVM tests:

```bash
# Android modules (most of the tree)
./gradlew :core:alarm-engine:testDebugUnitTest
./gradlew :feature:alarmlist:testDebugUnitTest --tests "*AlarmListViewModelTest*"

# Pure JVM modules (:core:model, :core:common) use the plain test task
./gradlew :core:model:test --tests "dev.tomng.alarme.core.model.scheduling.*"
```

Instrumented tests only exist in `app/src/androidTest`; they launch the real activity with the real Hilt graph and run Android accessibility checks, so hierarchy violations fail the build.

## Architecture

Gradle modules (actual list from `settings.gradle.kts`; `README.md` omits `feature/missions` and `feature/widget`):

- `app` — `MainActivity`, manual state-based top-level navigation, Hilt glue, manifest, release config.
- `core/model` — pure Kotlin (JVM module): `Alarm`, sealed `Mission`, scheduling math, backup payloads.
- `core/common` — pure Kotlin (JVM module): `AppClock`, dispatcher abstractions, application scope.
- `core/data` — Room v1 (`alarme.db`, `exportSchema = false`), DataStore settings, repositories, skip/ring-event logic.
- `core/designsystem` — Material 3 theme, dynamic color.
- `core/alarm-engine` — AlarmManager scheduling, broadcast receivers, foreground services, wake-up check, notifications, power-off/uninstall blockers, the ring state machine.
- `core/audio` — `RingAudioHost`, players, volume lock, crescendo, TTS, vibration, audio focus.
- `core/sensors` — shake, squat/cover, step-counter trackers.
- `feature/alarmlist`, `feature/editor`, `feature/missions`, `feature/ring`, `feature/ringtones`, `feature/settings`, `feature/backup`, `feature/widget` — Compose screens, ViewModels, feature-specific Hilt modules.

### Ring path (the most important flow)

1. `AlarmSchedulerImpl` arms alarms via `AlarmManager.setAlarmClock` (snooze and wake-up check are real AlarmManager PIs too; no WorkManager fallback). Schedule IDs encode device/alarm/time and are validated on delivery; stale or bogus rings are rejected.
2. Broadcast receivers (`AlarmRingReceiver`, `BootCompletedReceiver`, `TimeChangeReceiver`, `AppUpdateReceiver`, `ExactAlarmPermissionReceiver`, `AlarmWarmupReceiver`, `AlarmPrepareReceiver`) feed `RingDeliveryDispatcher` / `BootHandler` / `TimeChangeGuard`.
3. Valid delivery starts `AlarmService` (foreground, `systemExempted`, `START_REDELIVER_INTENT`, `directBootAware`). The service primes a `RingRequest` in `RingEffectExecutor` and submits `AlarmRingRequest` to `RingSessionStore`.
4. `RingStateMachine.reduce(state, event): RingTransition` is a pure reducer. Every time-dependent event carries `now`; every asynchronous action leaves as a `RingEffect`. Do not read the clock or touch Android inside reducers - add events/effects instead.
5. `RingSessionStore` owns the process-wide live session and serialises `submit()`; `RingEffectExecutor` is the only Android-touching consumer of effects and feeds timer/tick results back as events.
6. `RingAudioHost` implements the `RingHost` audio seam; `RingActivity` renders the takeover UI and submits UI events. The ring screen is a separate activity, not a destination in `MainActivity`.

When changing ring behavior, update the reducer (and its effects) plus the executor and tests. Most behavior belongs in `core/alarm-engine` unit tests, not UI tests.

### Data and DI

- Hilt throughout: `@HiltAndroidApp` on `AlarmeApplication`, `@AndroidEntryPoint` on activities/services/receivers, `@SingletonComponent` bindings in each module's `di/*Module.kt` (`DataModule`, `SchedulingModule`, `AudioModule`, feature modules).
- Repositories are the source of truth; entities are mapped to model objects (`AlarmEntityMapper`). `AlarmRepository.observeNextAlarm()` recomputes the next occurrence from the clock instead of trusting the stored `time` column.
- Backups are ZIP files containing `alarms.json` (`BackupPayload`, `formatVersion`) plus audio files; keep `dev.tomng.alarme.core.model.**` R8-kept because serialization discriminators are fully-qualified class names.
- Settings live in DataStore; reliability-critical scheduling state uses device-protected storage so alarms survive reboot-before-unlock.
- The Glance widget (`feature/widget`) is refreshed from `AlarmeApplication` by observing the next alarm.

## Conventions and constraints

- Test-first is the project rule: write the failing unit test before implementation. Use Truth, Turbine, `kotlinx-coroutines-test`, `FakeAppClock`, and in-module fakes; use Robolectric for anything touching `android.*`.
- Formatting is enforced by ktfmt (`kotlinLangStyle`) and detekt with `build.maxIssues: 0`. Keep comments sparse and only where code is not self-explanatory. Do not use em-dashes in comments; use hyphens. Do not wrap long lines in comments or markdown.
- Compose screens take callbacks and are testable with `createComposeRule` under Robolectric; ViewModels use constructor injection and expose state flows.
- Release uses R8 full mode and resource shrinking. Built-in sounds are referenced only by name string (`android.resource://.../raw/<name>`), so resource shrinking removes them unless listed in `feature/ringtones/src/main/res/raw/keep.xml`; add every new built-in sound there. Signing comes from untracked `keystore.properties`; without it the release build falls back to the debug key.
- Debug builds enable pseudo-locales; all user-facing strings must stay in resources. `lint.xml` has one intentional `mipmap-anydpi-v26` exception.
