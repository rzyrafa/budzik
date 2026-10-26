# Project: Native Android Alarm Clock (Budzik)

## Architecture
- **Pattern**: Clean Architecture & MVI/MVVM with Repository Pattern, built with Kotlin and Jetpack Compose (Material 3).
- **Presentation Layer**:
  - `MainActivity`: Jetpack Compose UI containing `AlarmListScreen`, `BatchAlarmDialog`, and `ClearAllConfirmationDialog`.
  - `RingingActivity`: Specialized lockscreen Activity configured with `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `FLAG_KEEP_SCREEN_ON`, back gesture disabling, and volume hardware key interception hosting `RingingScreen` and `MathNumpad`.
- **Domain Layer**:
  - `MathProblemGenerator`: Pure Kotlin arithmetic engine generating addition problems with strict digit constraints (Easy 1-digit, Medium 2-digit, Hard 4-digit) and validating sums.
  - `BatchAlarmGenerator`: Pure Kotlin generator calculating exact sequential alarm timestamps spaced by configurable intervals with midnight rollover handling.
  - Domain models: `Alarm`, `Difficulty`, `MathProblem`, `RingingState`.
- **Data Layer**:
  - Room Database: `AlarmDatabase` with `AlarmEntity` and `RingingStateEntity`.
  - `AlarmDao`: CRUD operations, active alarm queries, bulk deletion.
  - `RingingStateDao`: Singleton entity tracking active alarm ringing for Direct Boot / Reboot Trap.
  - `AlarmRepository`: Implementation combining Room DAO and system scheduler.
- **Engine / System Layer**:
  - `AlarmScheduler`: Exact alarm scheduling via `AlarmManager.setAlarmClock()`, pending intent management with `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.
  - `AlarmReceiver`: BroadcastReceiver handling `AlarmManager` triggers, starting `AlarmRingingService` and launching `RingingActivity`.
  - `BootReceiver`: Restores scheduled alarms upon `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` and activates Reboot Trap if an alarm was ringing during restart.
  - `TimeChangeReceiver`: Reschedules active alarms on `TIME_SET` and `TIMEZONE_CHANGED`.
  - `AlarmRingingService`: Foreground Service (`mediaPlayback`) looping alarm audio on `STREAM_ALARM`, enforcing maximum volume, and maintaining an ongoing notification with full-screen intent.
  - `BatteryOptimizationHelper`: Checks and requests exemption from battery optimizations.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Gradle Build Setup | Root & app build scripts, Gradle 8.10.2 wrapper, Java 21 binding in gradle.properties, Compose/Room/KSP/Robolectric plugins | M1 | Survey (Explorer 1) |
| 2 | AndroidManifest Configuration | All permissions (SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM, USE_FULL_SCREEN_INTENT, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS), Activity/Receiver/Service declarations | M1 | Survey (Explorer 1) |
| 3 | MathProblemGenerator Easy | 1-digit addition (operands in 1..9, sum = a + b) | M2 | Survey (Explorer 3), R3 |
| 4 | MathProblemGenerator Medium | 2-digit addition (operands in 10..99, sum = a + b) | M2 | Survey (Explorer 3), R3 |
| 5 | MathProblemGenerator Hard | 4-digit addition (operands in 1000..9999, sum = a + b) | M2 | Survey (Explorer 3), R3 |
| 6 | MathProblem Validation | Pure Kotlin arithmetic verification, input parsing, error handling | M2 | Survey (Explorer 3), R3 |
| 7 | BatchAlarmGenerator Core | Sequential alarm generation for N alarms at interval M minutes with exact timestamps | M2 | Survey (Explorer 3), R2 |
| 8 | Midnight Rollover Calculation | Rollover past 23:59 advancing date correctly without past-timestamp scheduling errors | M2 | Survey (Explorer 3), R2 |
| 9 | Room AlarmEntity Schema | Table holding alarm id, hour, minute, daysOfWeek bitmask, isEnabled, difficulty, problemCount, label | M3 | Survey (Explorer 3), Persistence |
| 10 | Room RingingStateEntity Schema | Singleton table tracking active ringing alarm id, difficulty, problem count for reboot trap | M3 | Survey (Explorer 3), R4 |
| 11 | Room AlarmDao & Repository | Insert, update, delete, getById, getAllAlarms, getEnabledAlarms, deleteAll, and repository abstractions | M3 | Survey (Explorer 3), R2, AC |
| 12 | Exact Alarm Scheduling | Scheduling alarms via `AlarmManager.setAlarmClock()` with `AlarmClockInfo` and `FLAG_IMMUTABLE` PendingIntent | M4 | Survey (Spec Miner 2), R1 |
| 13 | Boot Restoration Receiver | `BootReceiver` restoring all enabled alarms from Room upon `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` | M4 | Survey (Spec Miner 2), R1 |
| 14 | Time & Timezone Change Receiver | Reschedules alarms on `ACTION_TIME_SET` and `ACTION_TIMEZONE_CHANGED` | M4 | Survey (Spec Miner 2), R1 |
| 15 | Battery Optimization Bypass | Checking `isIgnoringBatteryOptimizations` and intent to request exemption | M4 | Survey (Spec Miner 2), R1 |
| 16 | AlarmRingingService Foreground Audio | Foreground service playing alarm ringtone on `STREAM_ALARM` with ongoing full-screen notification | M4 | Survey (Spec Miner 2), R4 |
| 17 | Volume Lock Enforcement | Intercepting volume down keys and resetting `STREAM_ALARM` to maximum volume | M4 | Survey (Spec Miner 2), R4 |
| 18 | Reboot Trap Engine | Recording ringing state in Room / storage; resuming alarm immediately upon boot if restarted while ringing | M4 | Survey (Spec Miner 2), R4 |
| 19 | Material 3 AlarmListScreen | Compose UI displaying configured alarms with toggle switches and delete actions | M5 | Survey (Explorer 3), R5 |
| 20 | Batch Creation Dialog | Compose dialog to configure count, interval in minutes, difficulty, and create alarms in bulk | M5 | Survey (Explorer 3), R2, R5 |
| 21 | Clear All Action & Dialog | One-click button with confirmation dialog canceling all scheduled alarms in AlarmManager and clearing Room DB | M5 | Survey (Explorer 3), R2, R5 |
| 22 | Fullscreen Lockscreen Overlay | `RingingActivity` with `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `FLAG_KEEP_SCREEN_ON` | M5 | Survey (Spec Miner 2), R4, R5 |
| 23 | Anti-Cheat Navigation Blocker | Blocking back press / gestures via `OnBackPressedCallback` and disabling dismissal until solved | M5 | Survey (Spec Miner 2), R4 |
| 24 | Dedicated On-Screen MathNumpad | 12-key Compose numpad (0-9, backspace, submit) with clear progression display | M5 | Survey (Explorer 3), R3, R5 |
| 25 | Comprehensive Unit & Logic Test Suite | Unit tests for `MathProblemGeneratorTest`, `BatchAlarmGeneratorTest`, `AlarmDaoTest` passing via `gradlew.bat test` | M6 | Survey, Acceptance Criteria |
| 26 | Gradle Compilation & APK Verification | Complete build verifying `gradlew.bat assembleDebug` produces valid debug APK and manifest validity | M6 | Survey, Acceptance Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Project Scaffolding & Build Foundation | Gradle 8.10.2 wrapper, Java 21 binding, build scripts, Compose/Room dependencies, AndroidManifest.xml foundation | none | DONE |
| M2 | Domain Generators & Logic Engine | MathProblemGenerator (Easy/Medium/Hard), BatchAlarmGenerator with interval and rollover, domain models, unit tests | M1 | DONE |
| M3 | Room Persistence & Repository Layer | AlarmEntity, RingingStateEntity, AlarmDao, RingingStateDao, AlarmDatabase, AlarmRepository, in-memory DAO tests | M1, M2 | PLANNED |
| M4 | Alarm Scheduling Engine & Anti-Cheat Services | AlarmScheduler (setAlarmClock), BootReceiver, TimeChangeReceiver, AlarmReceiver, AlarmRingingService (volume lock, mediaPlayback), Reboot Trap engine | M1, M2, M3 | PLANNED |
| M5 | User Interface & Lockscreen Ringing Experience | Jetpack Compose UI: AlarmListScreen, BatchAlarmDialog, ClearAllAction, RingingActivity lockscreen overlay, MathNumpad, BackHandler blocking | M1, M2, M3, M4 | PLANNED |
| M6 | E2E Test Suite Pass & Final Verification | Execute 100% of E2E tests from TEST_READY.md, run gradlew.bat test and gradlew.bat assembleDebug to verify debug APK | M1, M2, M3, M4, M5 | PLANNED |

## Interface Contracts

### Domain Generators ↔ Presentation & Data
```kotlin
package com.budzik.alarm.domain.model

enum class Difficulty { EASY, MEDIUM, HARD }

data class MathProblem(
    val operandA: Int,
    val operandB: Int,
    val solution: Int
)

data class GeneratedAlarm(
    val hour: Int,
    val minute: Int,
    val triggerEpochMs: Long,
    val difficulty: Difficulty
)
```

```kotlin
package com.budzik.alarm.domain.generator

interface IMathProblemGenerator {
    fun generateProblem(difficulty: Difficulty): MathProblem
    fun validateAnswer(problem: MathProblem, answer: Int): Boolean
}

interface IBatchAlarmGenerator {
    fun generateBatch(
        count: Int,
        intervalMinutes: Int,
        baseHour: Int,
        baseMinute: Int,
        baseEpochMs: Long,
        difficulty: Difficulty
    ): List<GeneratedAlarm>
}
```

### Data Layer ↔ Scheduling & Presentation
```kotlin
package com.budzik.alarm.data.local

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val daysOfWeekBitmask: Int, // 0 = one-time, bits 1..7 for Mon..Sun
    val difficulty: String,     // EASY, MEDIUM, HARD
    val problemCount: Int,
    val label: String
)

@Entity(tableName = "ringing_state")
data class RingingStateEntity(
    @PrimaryKey val id: Int = 1, // Singleton
    val alarmId: Long,
    val isRinging: Boolean,
    val difficulty: String,
    val problemCount: Int,
    val solvedCount: Int
)
```

```kotlin
package com.budzik.alarm.domain.repository

interface AlarmRepository {
    fun getAllAlarms(): Flow<List<AlarmEntity>>
    suspend fun getAlarmById(id: Long): AlarmEntity?
    suspend fun getEnabledAlarms(): List<AlarmEntity>
    suspend fun insertAlarm(alarm: AlarmEntity): Long
    suspend fun updateAlarm(alarm: AlarmEntity)
    suspend fun deleteAlarm(id: Long)
    suspend fun deleteAllAlarms()
    
    suspend fun getRingingState(): RingingStateEntity?
    suspend fun setRingingState(state: RingingStateEntity)
    suspend fun clearRingingState()
}
```

### Scheduling Engine ↔ Receivers & Services
```kotlin
package com.budzik.alarm.engine

interface AlarmScheduler {
    fun schedule(alarm: AlarmEntity)
    fun cancel(alarmId: Long)
    fun cancelAll(alarmIds: List<Long>)
    fun rescheduleAll(alarms: List<AlarmEntity>)
}

interface AlarmEnforcementController {
    fun startRinging(alarmId: Long)
    fun stopRinging()
    fun enforceMaxVolume()
    fun isRebootTrapActive(): Boolean
}
```

## Code Layout
```
budzik/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew.bat
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/budzik/alarm/
        │   │   ├── BudzikApplication.kt
        │   │   ├── domain/
        │   │   │   ├── model/ (Alarm, Difficulty, MathProblem, RingingState)
        │   │   │   ├── generator/ (MathProblemGenerator, BatchAlarmGenerator)
        │   │   │   └── repository/ (AlarmRepository)
        │   │   ├── data/
        │   │   │   ├── local/ (AlarmEntity, RingingStateEntity, AlarmDao, RingingStateDao, AlarmDatabase)
        │   │   │   └── repository/ (AlarmRepositoryImpl)
        │   │   ├── engine/
        │   │   │   ├── AlarmScheduler.kt
        │   │   │   ├── AlarmReceiver.kt
        │   │   │   ├── BootReceiver.kt
        │   │   │   ├── TimeChangeReceiver.kt
        │   │   │   ├── AlarmRingingService.kt
        │   │   │   └── BatteryOptimizationHelper.kt
        │   │   └── ui/
        │   │       ├── MainActivity.kt
        │   │       ├── RingingActivity.kt
        │   │       ├── list/ (AlarmListScreen, AlarmViewModel)
        │   │       ├── dialogs/ (BatchAlarmDialog, ClearAllDialog)
        │   │       ├── ringing/ (RingingScreen, MathNumpad, RingingViewModel)
        │   │       └── theme/ (Color, Theme, Type)
        │   └── res/
        │       ├── values/ (strings.xml, colors.xml, themes.xml)
        │       └── drawable/
        └── test/
            └── java/com/budzik/alarm/
                ├── domain/
                │   ├── MathProblemGeneratorTest.kt
                │   └── BatchAlarmGeneratorTest.kt
                └── data/
                    └── AlarmDaoTest.kt
```
