# HomeOps

Android household cleaning assistant. Spec: [`DESIGN.md`](DESIGN.md). Working rules: [`CLAUDE.md`](CLAUDE.md).

## Status

| Milestone | State |
|---|---|
| M1: Core manual app | Implemented. Domain tests pass on the JVM; full Android build, lint, and on-device acceptance pending (see below). |
| M2 to M8 | Not started |

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

## Layout

```
app/src/main/java/com/example/homeops/
  domain/      pure Kotlin: Tuning.kt (all constants), Dueness, PriorEstimate, FormValidation
  data/db/     Room entities, DAOs, converters, HomeOpsDatabase (v1)
  data/repo/   HomeRepository (rooms, zones), TaskRepository (tasks, completion log)
  di/          Hilt module (database, DAOs, Clock)
  ui/          Compose screens: Today, Rooms, Room detail, Task editor, Log
```
