# HomeOps

Android household cleaning assistant. Spec: [`DESIGN.md`](DESIGN.md). Working rules: [`CLAUDE.md`](CLAUDE.md).

## Status

| Milestone | State |
|---|---|
| M1: Core manual app | Done: 28/28 tests, verified on the emulator. |
| M2: Scheduling and reminders | Implemented. Domain tests pass on the JVM; Android tests, lint, and on-device check pending. |
| M3 to M8 | Not started |

## Build and test

Requirements: Android Studio (Narwhal or newer), or the Android SDK (platform 36) plus a JDK.

The build runs on **Java 21**, declared in `gradle/gradle-daemon-jvm.properties`. Gradle and Android Studio pick an
installed JDK 21 automatically even if your default Java is newer (Gradle 8.14 cannot run on Java 25). If no JDK 21 is
installed, install one (for example `winget install EclipseAdoptium.Temurin.21.JDK`) or let Android Studio download it.

```bash
./gradlew test              # JVM unit tests: domain (JUnit 5) + data layer (Robolectric via JUnit vintage)
./gradlew lint              # Android lint
./gradlew installDebug      # install on a connected device/emulator
```

After the first successful build, commit the generated Room schema `app/schemas/com.example.homeops.data.db.HomeOpsDatabase/1.json`.
Every later schema change needs a new version, a `Migration`, and its exported JSON.

## M1 acceptance checklist (manual)

1. Rooms tab: add 3 rooms (e.g. Kitchen, Bathroom, Living room). Optionally add zones in a room.
2. In each room, "Add task" until there are 10 tasks total.
3. Today tab: all 10 appear under "Due now" (never-completed tasks are due immediately). Tap the check on each.
4. Log tab: 10 completions, all "On time".
5. Force-stop the app (Settings > Apps > HomeOps > Force stop), reopen: rooms, tasks, and the log are unchanged.

`M1AcceptanceTest` automates the same flow against a file-backed database (close and reopen = restart).

## M2 acceptance checklist (manual)

The app upgrades an M1 database in place (DB v1 to v2). Run `./gradlew test` before installing over M1 data:
`Migration1To2Test` proves the upgrade on a real v1 file.

1. Settings tab: turn on **Daily reminder** and allow notifications when Android asks.
2. Make a task due: add a task with "Every 1 days" (never-completed tasks are due immediately).
3. Quick check: set **Default reminder time** to 2 to 3 minutes from now. The digest ("1 task due, about 5 min")
   arrives at about that time (Android may delay background work by a few minutes).
4. Tap **Snooze 3 h** on the notification: the notification clears and a repeat is scheduled 3 h later
   (moved to 8:00 AM if that lands in quiet hours). Snoozing the same task 3 times lengthens its interval by 15%.
5. Real acceptance: complete the 1-day task today; tomorrow at the configured time the digest lists it again.

`ReminderNotifierTest` and `NotificationTimeLearnerTest` automate steps 3 to 5.

## Layout

```
app/src/main/java/com/example/homeops/
  domain/      pure Kotlin: Tuning.kt (all constants), Dueness, DecayScheduler, NotificationTimeLearner,
               Digest, PriorEstimate, FormValidation
  data/db/     Room entities, DAOs, converters, migrations, HomeOpsDatabase (v2)
  data/repo/   HomeRepository (rooms, zones), TaskRepository (tasks, completion log, snoozes)
  data/prefs/  SettingsRepository (DataStore: reminder settings)
  di/          Hilt module (database, DAOs, DataStore, WorkManager, Clock)
  work/        ReminderScheduler, ReminderWorker, DailyRecomputeWorker (WorkManager)
  notify/      Notification channels, ReminderNotifier, SnoozeReceiver
  ui/          Compose screens: Today, Rooms, Room detail, Task editor, Log, Settings
```
