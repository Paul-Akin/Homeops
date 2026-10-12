# HomeOps: Household Cleaning Assistant for Android

**Design Document v0.1**
Status: Draft for implementation
Owner: Paul
Target platform: Android (phone), single user, single household

> Working name is "HomeOps". Rename freely; the package name below is a placeholder.

---

## 1. Purpose

HomeOps treats a house the way a maintenance organization treats a facility: inventory the spaces, inspect them (photos), generate work orders (tasks), estimate labor (time), schedule recurring maintenance (reminders), and keep records (progress history). It also tracks where household items are stored and proposes better storage locations.

### 1.1 Goals

1. Turn a photo of a room into a validated list of concrete cleaning tasks.
2. Generate room-specific recurring cleaning plans from the user's home inventory and preferences.
3. Estimate task and plan durations that get more accurate with use.
4. Remind the user when tasks are actually due, at times the user is likely to act.
5. Show progress over time with charts and achievement badges.
6. Maintain a searchable map of rooms, zones, containers, and items, and propose storage locations that reduce retrieval effort.

### 1.2 Non-goals (v1)

- Multi-user households, sharing, or cloud sync.
- iOS.
- 3D scanning, ARCore, or LiDAR mapping.
- On-device ML model training.
- Play Store release (sideload and personal use first).

### 1.3 Design principles

- **Local-first.** All user data lives on the device. Only photo analysis and plan generation call the cloud.
- **The model observes, the app decides.** The LLM returns structured JSON. App code validates it, clamps it, and owns all scheduling, estimation, and persistence logic.
- **Deterministic math over AI guesses** wherever real user data exists (time estimates, reminder timing, placement).
- **User confirms AI output.** AI-generated tasks are proposals until the user accepts them.
- **No secrets in the APK.** The LLM API key lives only on the proxy backend.

---

## 2. Tech Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose + Material 3 | Single-activity app |
| Navigation | Navigation Compose | |
| Architecture | MVVM + unidirectional data flow | ViewModel exposes `StateFlow<UiState>` |
| DI | Hilt | |
| Persistence | Room (SQLite) | Schema export enabled, migrations required |
| Preferences | DataStore (Preferences) | |
| Background work | WorkManager | Daily recompute + notification dispatch |
| Camera | CameraX | Plus system photo picker for gallery import |
| Image handling | Coil (display), Android `Bitmap` APIs (downscale) | |
| Networking | Retrofit + OkHttp + kotlinx.serialization | |
| Charts | Vico (Compose) | |
| Testing | JUnit 5, Turbine, MockK, Robolectric, Compose UI tests | |
| Min SDK | 26 (Android 8.0) | Notification channels available |
| Target SDK | Latest stable | |
| Backend proxy | Cloudflare Worker (TypeScript) | Alternative: Python FastAPI on any small host |
| LLM | Anthropic Claude, model `claude-sonnet-5-5` | Configurable on the proxy; verify current model IDs in Anthropic docs |

---

## 3. System Architecture

```
┌──────────────────────── Android App ────────────────────────┐
│                                                             │
│  UI (Compose screens)                                       │
│     │                                                       │
│  ViewModels  ──────────────►  Domain layer (pure Kotlin)    │
│     │                          - TimeEstimator              │
│     │                          - DecayScheduler             │
│     │                          - NotificationTimeLearner    │
│     │                          - PlacementOptimizer         │
│     │                          - BadgeEngine                │
│     │                          - AiOutputValidator          │
│     ▼                                                       │
│  Repositories                                               │
│     ├── Room DB (all user data)                             │
│     ├── DataStore (settings)                                │
│     ├── ImageStore (app-private files dir)                  │
│     └── AiClient (Retrofit) ─────────────┐                  │
│                                          │                  │
│  WorkManager: DailyRecomputeWorker,      │                  │
│               ReminderWorker             │                  │
└──────────────────────────────────────────┼──────────────────┘
                                           │ HTTPS + device token
                                           ▼
                          ┌──────────── Proxy ────────────┐
                          │ Auth check, rate limit,       │
                          │ prompt assembly, schema       │
                          │ enforcement, API key storage  │
                          └──────────────┬────────────────┘
                                         ▼
                                  Anthropic Messages API
```

### 3.1 Module / package layout

Single Gradle module for v1, organized by layer then feature:

```
app/src/main/java/com/example/homeops/
  di/                     Hilt modules
  data/
    db/                   Room database, DAOs, entities, migrations
    repo/                 Repositories
    ai/                   Retrofit API, DTOs, AiClient
    image/                ImageStore, downscale + EXIF strip
    prefs/                DataStore wrapper
  domain/
    model/                Pure Kotlin domain models
    estimation/           TimeEstimator
    scheduling/           DecayScheduler, NotificationTimeLearner
    mapping/              PlacementOptimizer, DistanceModel
    progress/             BadgeEngine, stats queries
    validation/           AiOutputValidator
  work/                   WorkManager workers
  notify/                 Notification channels and builders
  ui/
    theme/
    nav/
    home/                 Today dashboard
    rooms/                Room list, room detail, zone editor
    tasks/                Task list, task detail, timer
    capture/              Camera + analysis review
    plan/                 Plan generation + review
    progress/             Charts, badges
    map/                  Storage map, item search, placement suggestions
    settings/
proxy/                    Cloudflare Worker (separate npm project)
```

Domain layer classes must be pure Kotlin (no Android imports) so they are unit-testable on the JVM.

---

## 4. Data Model

All IDs are `Long` autogenerated primary keys unless noted. Timestamps are `Instant` stored as epoch milliseconds via a Room `TypeConverter`. Areas are in **square feet (ft²)**.

### 4.1 Spatial hierarchy

```
Home (implicit, single) → Room → Zone → Container → Item
```

```kotlin
@Entity data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                 // "Kitchen"
    val roomType: RoomType,           // KITCHEN, BATHROOM, BEDROOM, LIVING, LAUNDRY, GARAGE, OFFICE, HALLWAY, OTHER
    val floor: Int = 1,
    val areaFt2: Double?,             // optional, user-entered
    val weight: Double = 1.0,         // user priority multiplier
    val sortOrder: Int = 0
)

@Entity(foreignKeys = [ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = CASCADE)],
        indices = [Index("roomId")])
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val name: String,                 // "Countertop left", "Under sink"
    val areaFt2: Double?,
    val mapX: Float?, val mapY: Float? // optional position on 2D floor plan, normalized 0..1
)

@Entity(...FK zoneId...)
data class ContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val zoneId: Long,
    val name: String,                 // "Top drawer", "Blue bin"
    val capacitySlots: Int = 10,      // abstract capacity units
    val accessCost: Double = 0.0,     // extra effort, e.g. high shelf = 0.5
    val restrictions: Set<StorageRestriction> = emptySet() // CHILD_SAFE, FOOD_SAFE, DRY, etc.
)

@Entity(...FK containerId, nullable...)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: ItemCategory,       // CLEANING_CHEMICAL, TOOL, LINEN, FOOD, PET, OTHER
    val containerId: Long?,           // current location, null = unassigned
    val usedInZoneId: Long?,          // where it is typically used
    val usesPerMonth: Double = 1.0,   // f_i in placement optimizer
    val slots: Int = 1,               // capacity consumed
    val tags: Set<String> = emptySet(),
    val notes: String? = null
)
```

### 4.2 Tasks and history

```kotlin
@Entity data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val zoneId: Long?,
    val title: String,                // "Wipe down stovetop"
    val taskType: TaskType,           // WIPE, VACUUM, MOP, DUST, SCRUB, DECLUTTER, LAUNDRY, DISHES, TRASH, OTHER
    val areaFt2: Double?,
    val severity: Int = 3,            // 1..5, last observed soil level
    val estMinutesPrior: Double,      // t0, from table or AI
    val sigmaLn: Double = 0.4,        // log-normal shape parameter
    val tauDays: Double,              // decay time constant
    val lastCompletedAt: Instant?,
    val createdAt: Instant,
    val source: TaskSource,           // MANUAL, AI_PHOTO, AI_PLAN
    val isActive: Boolean = true,
    val supplies: List<String> = emptyList()
)

@Entity(...FK taskId...)
data class CompletionEventEntity(   // append-only log
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val completedAt: Instant,
    val actualMinutes: Double?,       // null if timer not used
    val estMinutesAtStart: Double?,
    val beforePhotoId: Long?,
    val afterPhotoId: Long?,
    val wasOnTime: Boolean
)

@Entity data class SnoozeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long, val at: Instant, val snoozeHours: Int
)

@Entity data class CorrectionFactorEntity(
    @PrimaryKey val key: String,      // "GLOBAL" or "TYPE:VACUUM"
    val r: Double = 1.0,
    val n: Int = 0                    // number of observations
)
```

### 4.3 Photos and AI results

```kotlin
@Entity data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val zoneId: Long?,
    val filePath: String,             // app-private storage, EXIF stripped
    val takenAt: Instant,
    val widthPx: Int, val heightPx: Int
)

@Entity data class PhotoAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: Long,
    val rawJson: String,              // exact validated response, for audit/debug
    val modelId: String,
    val analyzedAt: Instant,
    val overallSeverity: Int          // 1..5
)

@Entity data class BadgeEntity(
    @PrimaryKey val badgeId: String,  // matches BadgeEngine rule id
    val earnedAt: Instant
)
```

---

## 5. Algorithms

All formulas below are implemented in the `domain` package with unit tests. Defaults are engineering judgment and are centralized in a single `Tuning.kt` object so they can be adjusted without hunting through code.

### 5.1 Time estimation (`TimeEstimator`)

**Prior estimate** for a task:

$$t_0 = a_k + b_k \cdot A \cdot s$$

- $A$ = area in ft² (if null, use `defaultAreaFt2[taskType]`)
- $s$ = severity factor, $s = 0.6 + 0.2\,(\text{severity} - 1)$, giving $s \in [0.6, 1.4]$
- $a_k$ = setup/teardown minutes for task type $k$
- $b_k$ = minutes per ft² at nominal soil for task type $k$

Starting table (to be tuned with real data):

| Task type | $a_k$ (min) | $b_k$ (min/ft²) | Default $A$ (ft²) |
|---|---|---|---|
| VACUUM | 3 | 0.04 | 150 |
| MOP | 5 | 0.06 | 100 |
| WIPE | 1 | 0.25 | 15 |
| DUST | 2 | 0.05 | 120 |
| SCRUB | 3 | 0.6 | 12 |
| DECLUTTER | 2 | 0.3 | 20 |
| DISHES | 5 | 0 | 0 |
| LAUNDRY | 10 | 0 | 0 |
| TRASH | 3 | 0 | 0 |

If the AI supplies `est_minutes`, use it as $t_0$ instead of the table, clamped to $[1, 240]$ minutes.

**Personal correction factor.** On each completion with a timer value, update both the task-type factor and the global factor with an exponentially weighted moving average:

$$r_{n+1} = \alpha\,\frac{t_\text{actual}}{t_\text{est}} + (1 - \alpha)\,r_n, \qquad \alpha = 0.25$$

Rules:
- Reject outliers: skip the update if $t_\text{actual}/t_\text{est} \notin [0.2, 5]$.
- Clamp $r \in [0.33, 3.0]$.
- Use the type factor if its $n \ge 3$, else the global factor.

**Task mean:** $\mu_i = r \cdot t_0$

**Uncertainty.** Treat each duration as log-normal with shape $\sigma_{\ln}$ (default 0.4). Mean and variance:

$$\mathrm{Var}_i = \mu_i^2\left(e^{\sigma_{\ln}^2} - 1\right)$$

**Plan total** for a set of tasks, assuming independence and a normal approximation to the sum (adequate for 4 or more tasks; flag as approximate below that):

$$\mu_T = \sum_i \mu_i, \qquad \sigma_T = \sqrt{\sum_i \mathrm{Var}_i}, \qquad T_{80} = \mu_T + 0.8416\,\sigma_T$$

**UI shows $T_{80}$ by default** ("about 45 min, could run to 55"), with $\mu_T$ available on tap. Rationale: the planning fallacy (Kahneman and Tversky) means users systematically underestimate durations, so quoting the mean is optimistic.

**Shrinking $\sigma_{\ln}$:** after $n \ge 5$ timed completions for a task, recompute $\sigma_{\ln}$ as the sample standard deviation of $\ln(t_\text{actual}/t_\text{est})$, floored at 0.15.

### 5.2 Due-ness and scheduling (`DecayScheduler`)

Model "need to clean" as saturating accumulation since the last completion:

$$D(t) = 1 - e^{-t/\tau}$$

where $t$ is days since `lastCompletedAt` and $\tau$ is `tauDays`.

- A task is **due** when $D \ge D^*$, with $D^* = 0.8$.
- Due interval: $t^* = -\tau \ln(1 - D^*) = \tau \ln 5 \approx 1.609\,\tau$.
- When a user or the AI specifies a nominal frequency $F$ days, initialize $\tau = F / \ln 5$.
- Never-completed tasks are treated as due immediately.

**Priority score** for ordering the Today list:

$$P = D(t) \cdot w_\text{room} \cdot w_\text{task}$$

**Adapting $\tau$ from photos.** When a photo analysis reports severity $s_\text{obs} \in \{1..5\}$ for a zone that has tasks, map it to an observed soil level and back out the implied time constant:

$$D_\text{obs} = \mathrm{clamp}\!\left(\frac{s_\text{obs} - 1}{4},\ 0.05,\ 0.95\right), \qquad \tau_\text{obs} = \frac{-t}{\ln(1 - D_\text{obs})}$$

Then update with EWMA: $\tau \leftarrow 0.3\,\tau_\text{obs} + 0.7\,\tau$, clamped to $[0.5, 180]$ days. Skip if $t < 0.5$ days.

**Behavioral adaptation (simple heuristics, v1):**
- Snoozed 3 or more times in a row: multiply $\tau$ by 1.15 (user thinks it is too frequent).
- Completed while $D < 0.5$ three times in a row: multiply $\tau$ by 0.9 (user wants it more often).

### 5.3 Reminder timing (`NotificationTimeLearner`)

Learn the user's preferred cleaning time of day from completion timestamps over the last 60 days. Time of day is circular, so use circular statistics:

$$\theta_k = \frac{2\pi\,h_k}{24} \quad (h_k \text{ in fractional hours, local time})$$

$$\bar\theta = \operatorname{atan2}\!\left(\sum_k \sin\theta_k,\ \sum_k \cos\theta_k\right), \qquad R = \frac{1}{N}\sqrt{\left(\sum_k \cos\theta_k\right)^2 + \left(\sum_k \sin\theta_k\right)^2}$$

Rules:
- Compute separately for weekdays and weekends.
- If $N \ge 5$ and $R \ge 0.5$: notify at $\bar\theta$ minus 30 minutes (lead time).
- Otherwise: use the user's default reminder time (setting, default 6:00 PM).
- Respect quiet hours (setting, default 10:00 PM to 8:00 AM). Shift into the next allowed window.
- At most one digest notification per day plus at most one "overdue" nudge, to avoid notification fatigue.

### 5.4 Storage placement (`PlacementOptimizer`)

Minimize expected retrieval effort across items:

$$J = \sum_i f_i \left[\, d(z(c_i),\ u_i) + \kappa_{c_i} \right]$$

- $f_i$ = `usesPerMonth`
- $c_i$ = assigned container, $z(c_i)$ its zone
- $u_i$ = `usedInZoneId` (if null, skip the item)
- $\kappa_c$ = container `accessCost`
- Subject to: $\sum_{i \in c} \text{slots}_i \le \text{capacitySlots}_c$ for every container, and item category must satisfy container restrictions (e.g., `CLEANING_CHEMICAL` cannot go in a `FOOD_SAFE` container).

**Distance model** $d(z_1, z_2)$ (unitless effort, v1):

| Relation | $d$ |
|---|---|
| Same zone | 0 |
| Same room, different zone | 1 |
| Different room, same floor | 3 |
| Different floor | 6 |

If both zones have `mapX/mapY` and are on the same floor, replace the room-level value with $1 + 4\cdot\lVert \mathbf{x}_1 - \mathbf{x}_2 \rVert$ using normalized floor plan coordinates.

**Solver (v1):** greedy. Sort items by $f_i$ descending, assign each to the feasible container with the lowest marginal cost. Report $J_\text{current}$ vs $J_\text{proposed}$ and list only moves with savings $\Delta J_i \ge 0.5$. Greedy is not guaranteed optimal; a Hungarian or ILP solver is a v2 improvement if results look poor.

**AI assist (optional):** the LLM may suggest restrictions and `usedInZoneId` guesses for new items. These are proposals the user confirms.

### 5.5 Badges (`BadgeEngine`)

Rule-based, evaluated after each completion and in the daily worker. Each rule is a pure function of the completion log.

| ID | Name | Rule |
|---|---|---|
| `FIRST_TASK` | First Contact | 1 completion |
| `STREAK_7` | Week Warrior | Completed at least 1 task on 7 consecutive days |
| `STREAK_30` | Iron Mop | 30 consecutive days |
| `ROOM_SWEEP` | Full Sweep | Every active room had a completion in the same 7-day window |
| `ON_TIME_90` | Mission Nominal | ≥ 90% of completions on time over the last 30 days, minimum 20 completions |
| `ESTIMATOR` | Calibrated | Global $r \in [0.9, 1.1]$ with $n \ge 20$ |
| `BEFORE_AFTER` | Proof of Work | 10 completions with both before and after photos |
| `ORGANIZER` | Quartermaster | Accepted placement suggestions reducing $J$ by ≥ 20% |

"On time" means completed when $D \le 0.9$.

---

## 6. AI Integration

### 6.1 Proxy contract

The app never calls Anthropic directly. It calls the proxy.

**Auth:** on first launch the app generates a random 256-bit device token, stores it in EncryptedSharedPreferences, and sends it as `Authorization: Bearer <token>`. The proxy holds an allowlist of tokens (personal-use model). Rate limit: 30 analysis requests per token per day.

**Endpoints:**

```
POST /v1/analyze-photo
  body: { "room_type": "KITCHEN", "zones": ["Countertop left", ...],
          "image_base64": "...", "media_type": "image/jpeg" }
  200:  PhotoAnalysis JSON (6.2)

POST /v1/generate-plan
  body: { "rooms": [...], "existing_tasks": [...], "preferences": {...},
          "recent_findings": [...] }
  200:  PlanProposal JSON (6.3)

POST /v1/suggest-item-attributes      (v2, mapping assist)
```

The proxy:
1. Validates the request size (image ≤ 2 MB).
2. Assembles the system prompt and user message.
3. Calls the Messages API, requesting JSON only, with the schema embedded in the prompt (or via tool-use forced output for stricter schema adherence).
4. Parses and schema-validates the response; retries once on parse failure.
5. Returns the JSON or a typed error.

### 6.2 Photo analysis schema

```json
{
  "room_type": "KITCHEN",
  "overall_severity": 3,
  "findings": [
    {
      "zone": "Countertop left",
      "issue": "CLUTTER",
      "description": "Mail and small appliances crowding prep area",
      "severity": 3,
      "est_area_ft2": 6,
      "suggested_task_type": "DECLUTTER",
      "suggested_title": "Clear and wipe left countertop",
      "est_minutes": 8,
      "confidence": 0.8
    }
  ],
  "notes": "Floor not visible in frame"
}
```

Enums: `issue` ∈ {CLUTTER, DUST, STAIN, GREASE, GRIME, SPILL, TRASH, LAUNDRY, DISHES, PET_HAIR, MOLD_SUSPECTED, OTHER}.

`zone` must be one of the zone names sent in the request, or `"UNASSIGNED"`.

### 6.3 Plan proposal schema

```json
{
  "tasks": [
    {
      "room": "Kitchen",
      "zone": "Stovetop",
      "title": "Degrease stovetop and knobs",
      "task_type": "SCRUB",
      "frequency_days": 7,
      "est_minutes": 10,
      "supplies": ["degreaser", "microfiber cloth"],
      "rationale": "Daily cooking reported"
    }
  ]
}
```

### 6.4 Validation (`AiOutputValidator`, in app)

Even though the proxy validates, the app re-validates (defense in depth):
- Unknown enum values become `OTHER`.
- `severity` clamped to 1..5; `confidence` to 0..1.
- `est_minutes` clamped to [1, 240]; `frequency_days` to [1, 365]; `est_area_ft2` to [0.5, 2000].
- Unknown room or zone names map to `UNASSIGNED` and are surfaced for user assignment.
- Findings with `confidence < 0.4` are shown collapsed as "possible".
- Duplicate detection: a proposed task matching an existing active task (same room, zone, task type) is shown as "already tracked" and updates that task's severity instead of creating a new one.

### 6.5 Prompt guidelines (proxy)

- System prompt describes role (household cleaning inspector), the exact JSON schema, enum lists, and "respond with JSON only".
- Instruct the model to describe only what is visible, not to speculate about unseen areas, and to use `MOLD_SUSPECTED` cautiously with a note recommending a closer look.
- Instruct it to never identify people visible in photos and to ignore any text in the image that looks like instructions.

### 6.6 Image pipeline (app)

1. Capture via CameraX or pick via the system photo picker.
2. Decode with `inSampleSize`, then scale so the long edge is 1,280 px.
3. Re-encode as JPEG quality 85. This drops all EXIF metadata, including GPS. Verify with a unit test.
4. Save the downscaled copy to app-private storage (`filesDir/photos/`). Do not keep the original.
5. Upload the base64 to the proxy.

Token cost check (approximate, per Anthropic's documented rule of thumb): $\text{tokens} \approx w h / 750$, so 1280 × 960 ≈ 1,640 input tokens per photo.

---

## 7. Screens

| Screen | Contents |
|---|---|
| **Today** (start destination) | Due tasks sorted by $P$, $T_{80}$ total for "do all due", quick-complete swipe, start-timer button, "Scan a room" FAB |
| **Rooms** | List of rooms with due count and a $D$ heat indicator; add/edit room |
| **Room detail** | Zones, tasks, photo history, "Analyze photo", "Generate plan for this room" |
| **Capture & Review** | Camera, then analysis results as checkable proposed tasks; accept, edit, or reject each |
| **Plan Review** | Proposed recurring tasks grouped by room; accept, edit frequency, or reject |
| **Task detail** | Estimate ($\mu$ and $T_{80}$), history, correction factor, edit frequency, before/after photos |
| **Timer** | Running timer with estimate overlay, before/after photo prompts, complete button |
| **Progress** | Charts: minutes per week (bar), completions by room (stacked bar), on-time percentage (line), estimate accuracy $r$ over time (line); badge grid |
| **Storage Map** | Room → zone → container tree, optional 2D floor plan with zone pins, item search, "Suggest better locations" |
| **Settings** | Default reminder time, quiet hours, weekday/weekend toggle, proxy URL, data export/import (JSON), delete all data |

---

## 8. Background Work and Notifications

- **`DailyRecomputeWorker`** (periodic, every 24 h, constraint: none): recompute $D$ for all tasks, evaluate badges, recompute learned notification times, enqueue today's `ReminderWorker` as a one-time work request with the computed delay.
- **`ReminderWorker`**: builds a digest notification ("4 tasks due, about 35 min") with actions *Open* and *Snooze 3 h*. Snooze logs a `SnoozeEventEntity`.
- **Notification channels:** `due_digest` (default importance), `overdue` (low importance), `badges` (low importance).
- **Permissions:** `POST_NOTIFICATIONS` (Android 13+, request contextually on first reminder setup), `CAMERA` (request on first capture). No location, no storage permissions (use photo picker).

---

## 9. Privacy and Security

- All data is local. No analytics SDKs.
- Photos are downscaled and EXIF-stripped before storage or upload.
- Proxy does not persist images or prompts (log only request metadata: token hash, timestamp, latency, status).
- API key exists only as a proxy secret.
- Data export to JSON (excludes photos by default; option to include as a zip).
- "Delete all data" wipes the DB, photos, and DataStore.

---

## 10. Testing Strategy

| Layer | What | Tools |
|---|---|---|
| Domain | Every formula in Section 5 with hand-checked cases, boundary clamps, outlier rejection | JUnit 5 |
| Domain | Circular mean wraparound: inputs at 23:30 and 00:30 must give $\bar\theta \approx$ 00:00, not 12:00 | JUnit 5 |
| Domain | Placement optimizer respects capacity and restrictions; $J_\text{proposed} \le J_\text{current}$ | JUnit 5 |
| Validation | Malformed and adversarial AI JSON (missing fields, bad enums, huge numbers) | JUnit 5 |
| Data | DAO queries, migrations | Room in-memory DB, Robolectric |
| ViewModel | State transitions | Turbine, MockK, `kotlinx-coroutines-test` |
| UI | Today screen, capture review accept/reject flow | Compose UI tests |
| Image | Downscaled output has no EXIF tags | Instrumented test |
| Proxy | Schema enforcement, auth rejection, rate limit | Vitest / Miniflare |

**Hand-check examples** (use as unit test fixtures):
- $\tau = 7/\ln 5 = 4.349$ days; at $t = 7$: $D = 1 - e^{-7/4.349} = 0.800$ ✔
- Three tasks with $\mu = \{10, 20, 30\}$ min, $\sigma_{\ln} = 0.4$: $e^{0.16} - 1 = 0.1735$, so $\mathrm{Var} = \{17.35, 69.40, 156.15\}$, $\sigma_T = \sqrt{242.9} = 15.59$, $T_{80} = 60 + 0.8416 \times 15.59 = 73.1$ min.
- EWMA: $r_0 = 1.0$, observed ratio 1.4, $\alpha = 0.25$: $r_1 = 1.10$.

---

## 11. Milestones

Each milestone ends with a working, installable build.

### M1: Core manual app
- Project scaffold, Hilt, Room, navigation, theme.
- Rooms, zones, tasks CRUD.
- Today screen with manual completion.
- Completion log.
- **Accept:** create 3 rooms and 10 tasks, complete them, data survives app restart.

### M2: Scheduling and reminders
- `DecayScheduler`, priority ordering, `tauDays` from frequency.
- WorkManager workers, notification channels, snooze.
- `NotificationTimeLearner` with defaults and quiet hours.
- **Accept:** a task with 1-day frequency produces a notification the next day at the configured time; snooze logs an event.

### M3: Time estimation and timer
- Prior table, `TimeEstimator`, correction factors, $T_{80}$ display.
- Timer screen.
- **Accept:** unit tests in Section 10 pass; timing a task at 2× estimate shifts that type's $r$ as specified.

### M4: Proxy and photo analysis
- Cloudflare Worker with auth, rate limit, `/v1/analyze-photo`.
- Image pipeline with EXIF strip.
- Capture & Review screen, validator, duplicate detection, $\tau$ adaptation from severity.
- **Accept:** photographing a messy counter yields at least one accepted task linked to the right zone; no EXIF in uploaded image.

### M5: Plan generation
- `/v1/generate-plan`, Plan Review screen, preferences in Settings (pets, time budget per day, products owned).
- **Accept:** generating a plan for a 3-room home yields editable recurring tasks with no duplicates of existing tasks.

### M6: Progress and badges
- Progress screen charts, `BadgeEngine`, badge notifications.
- **Accept:** synthetic 30-day completion log fixture produces correct chart series and expected badges.

### M7: Storage map
- Containers and items CRUD, tree view, search.
- Optional 2D floor plan with draggable zone pins (simple Canvas).
- `PlacementOptimizer` with suggestions UI (accept moves individually).
- **Accept:** fixture with 15 items and 5 containers yields feasible suggestions with $J$ reduction reported.

### M8: Polish
- Data export/import, delete all data, empty states, dark theme, accessibility pass (content descriptions, 48 dp touch targets).

---

## 12. Open Questions

1. App name and package ID.
2. Proxy host: Cloudflare Worker (default) vs Python FastAPI (more familiar language, needs a host).
3. Should "do all due" ever split into multiple sessions automatically when $T_{80}$ exceeds a daily time budget? (Proposed for M5 via the preferences time budget.)
4. Pet-specific tasks (litter box, pet hair) as a preset pack?
5. Whether to add Gemini Nano on-device analysis as an offline fallback (device support is limited).

---

## 13. Conventions for Implementation

- Kotlin official style, ktlint enforced.
- No business logic in Composables or DAOs; formulas live in `domain`.
- All tunable constants live in `domain/Tuning.kt` with a comment citing the section of this document.
- Every Room schema change requires a migration and an exported schema JSON.
- Prefer `Flow` from DAOs; ViewModels expose immutable `UiState` data classes.
- Commit per logical change with a message referencing the milestone (e.g., `M2: add DecayScheduler`).
