# Test Infrastructure & Verification Harness Specification

**Document ID**: TEST-INFRA-BUDZIK-001  
**Project**: Native Android Alarm Clock Application (`Budzik`)  
**Target Platform**: Android 15 (API 35), Min SDK 26, Java 21, Kotlin 2.0  
**Test Suite Type**: Dual Track 4-Tier Opaque-Box & Integration Test Suite  
**Author**: E2E Test Suite Designer & Writer (`teamwork_preview_test_writer_e2e_1`)  

---

## 1. Executive Summary & Purpose

This document establishes the authoritative testing infrastructure, architectural harness, oracle definitions, and execution protocols for the native Android alarm clock application (**Budzik**). 

In accordance with the **Dual Track Testing Methodology**, the test suite is developed in parallel with application milestones (M1–M5) to provide immediate, unbiased, opaque-box verification against `PROJECT.md` interface contracts and `ORIGINAL_REQUEST.md` acceptance criteria. When all milestones are implemented, the entire suite executes during Milestone M6 to gate production release.

---

## 2. 4-Tier Testing Methodology

The test suite is partitioned into four distinct validation tiers:

| Tier | Name | Target Scope | Minimum Requirement | Primary Verification Focus |
| :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | **Feature Coverage** | Isolated feature capabilities & domain contracts | $\ge 5$ test cases per feature | Mathematical constraints, batch timestamp math, Room DAO/Repository CRUD, Anti-Cheat state transitions |
| **Tier 2** | **Boundary & Corner Cases** | Edge values, extremes, and error paths | $\ge 5$ test cases per feature | Min/max operand digits, midnight rollover ($23:59 \rightarrow 00:00$), zero/negative intervals, empty databases, rapid toggles |
| **Tier 3** | **Cross-Feature Combinations** | Multi-component & pairwise interactions | $\ge 5$ comprehensive combination tests | Batch generation + difficulty tiers, bulk deletion + scheduler cancellation, reboot trap persistence across simulated restarts, recurrence bitmasks + clock recalculation |
| **Tier 4** | **Real-World Scenarios** | End-to-end user workflows & lifecycle simulations | $\ge 5$ realistic workflow scenarios | Heavy sleeper multi-alarms, student anti-cheat reboot trap evasion, shift worker midnight shifts, weekday recurrence with time zone change, battery saver bulk clearing |

---

## 3. Authoritative Output Oracles

In accordance with opaque-box test design, every expected assertion in the test suite is derived from strict specifications rather than implementation artifacts:

### 3.1 Mathematical Addition Engine (`R3`)
- **Easy Tier**: $a \in [1, 9]$, $b \in [1, 9]$
  - Expected Range: $a, b \in [1, 9]$
  - Expected Solution: $a + b \in [2, 18]$
- **Medium Tier**: $a \in [10, 99]$, $b \in [10, 99]$
  - Expected Range: $a, b \in [10, 99]$
  - Expected Solution: $a + b \in [20, 198]$
- **Hard Tier**: $a \in [1000, 9999]$, $b \in [1000, 9999]$
  - Expected Range: $a, b \in [1000, 9999]$
  - Expected Solution: $a + b \in [2000, 19998]$
- **Validation Rule**: `validateAnswer(problem, answer) == (answer == problem.solution)` where `problem.solution == problem.operandA + problem.operandB`.

### 3.2 Sequential Batch Alarm Calculation (`R2`)
- **Count & Spacing**:
  Given base time $(H_{base}, M_{base})$, count $N > 0$, interval $I > 0$ minutes:
  - Total minutes for index $i \in [0, N-1]$: $T_i = H_{base} \times 60 + M_{base} + i \times I$
  - Rolled Hour: $H_i = \lfloor(T_i \pmod{1440}) / 60\rfloor$
  - Rolled Minute: $M_i = T_i \pmod{60}$
  - Absolute Trigger Epoch Millis: $E_i = E_{base} + i \times (I \times 60\,000)$
- **Clear All**:
  - Cancels all registered pending intents in `AlarmScheduler`.
  - Atomically truncates the alarm database table (`deleteAll()`).
  - Clears any active ringing session in `RingingStateDao` to prevent reboot trap false positives.

### 3.3 State Machine & Anti-Cheat Invariants (`R1`, `R4`)
- **Volume Lock Invariant**: When an alarm is actively ringing, `enforceMaxVolume()` must maintain stream volume at `streamMaxVolume` regardless of volume-down key events or external adjustments.
- **Reboot Trap Invariant**:
  - `startRinging(alarmId)` writes `isRinging = true` and `alarmId` to persistent storage before launching UI.
  - On system boot (`BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED`), if `isRebootTrapActive() == true`, the alarm must immediately resume ringing at max volume without waiting for user unlock.
  - Solving all required math problems is the **exclusive** path to execute `clearRingingState()` and `stopRinging()`.

---

## 4. Test Directory Layout

All tests reside under `app/src/test/java/com/budzik/alarm/e2e/`:

```
app/src/test/java/com/budzik/alarm/e2e/
├── HarnessFakes.kt                    # Test harness, in-memory repository, mock scheduler & enforcement fakes
├── Tier1FeatureCoverageTest.kt        # Feature coverage for Math, Batch, Persistence, and State Transitions
├── Tier2BoundaryCornerTest.kt         # Edge cases, boundary values, midnight rollover, invalid inputs, empty sets
├── Tier3CrossFeatureTest.kt           # Pairwise and cross-module interactions
└── Tier4RealWorldScenarioTest.kt      # End-to-end user stories and lifecycle workflows
```

---

## 5. Test Harness & Contract Doubles (`HarnessFakes.kt`)

To guarantee **Progressive Testability** and **Independence**, the test suite uses lightweight, pure JVM test doubles matching the interface contracts in `PROJECT.md`:

1. **`FakeAlarmRepository`**: In-memory, coroutine-safe implementation of `AlarmRepository` supporting `StateFlow<List<AlarmEntity>>`, CRUD queries, and ringing state management.
2. **`FakeAlarmScheduler`**: Records scheduled, cancelled, and rescheduled alarm IDs without invoking the Android OS `AlarmManager` C++ service.
3. **`FakeAlarmEnforcementController`**: Tracks ringing state, volume levels, and reboot trap flags.
4. **Reference Domain Generators**: Reference implementations of `IMathProblemGenerator` and `IBatchAlarmGenerator` implementing the exact mathematical specifications to act as oracles and verify contract compliance.

These doubles allow tests to run on any JVM without requiring an Android emulator or device, executing full suites in under 2 seconds.

---

## 6. Execution Commands

### 6.1 Run Full E2E Test Suite
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test --tests "com.budzik.alarm.e2e.*"
```

### 6.2 Run Specific Tiers
```powershell
# Tier 1: Feature Coverage
.\gradlew.bat test --tests "com.budzik.alarm.e2e.Tier1FeatureCoverageTest"

# Tier 2: Boundary & Corner Cases
.\gradlew.bat test --tests "com.budzik.alarm.e2e.Tier2BoundaryCornerTest"

# Tier 3: Cross-Feature Combinations
.\gradlew.bat test --tests "com.budzik.alarm.e2e.Tier3CrossFeatureTest"

# Tier 4: Real-World Scenarios
.\gradlew.bat test --tests "com.budzik.alarm.e2e.Tier4RealWorldScenarioTest"
```

### 6.3 Run All Project Tests (Domain + Dao + E2E)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat test
```

---

## 7. Quality & Integrity Safeguards

1. **Zero Facade Tests**: Every test asserts concrete calculations, state transitions, or mathematical truths. No trivial `assertTrue(true)` tests exist.
2. **Deterministic Execution**: Tests use fixed reference epoch timestamps and pseudo-random generators with fixed or tracked seeds where needed, eliminating test flakiness.
3. **Adversarial Stress**: Includes fuzz-testing loops (e.g. 1,000 iterations for math digit verification) and adversarial inputs (negative intervals, midnight wraparounds, out-of-order puzzle inputs).
4. **Strict Isolation**: Each test class and `@Test` method instantiates its own isolated fake repository and scheduler instances with zero cross-test state leakage.
