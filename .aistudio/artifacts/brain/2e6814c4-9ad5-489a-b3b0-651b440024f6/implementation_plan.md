# Termux Storage Trigger (Android 12 Targeted & Revised)

A minimal, battery-efficient native Android utility optimized for Android 12 (API 31/32) and forward-compatible with Android 13+. It silently monitors external storage changes via `JobScheduler` ContentUri triggers and dispatches background command intents to Termux without notifications or foreground services.

## User Review & Critical Decisions

> [!IMPORTANT]
> The plan is finalized with all Android 12 (API 31/32) optimizations and fallbacks:

1. **Termux Service Dispatch Fallback Chain**:
   - First attempt: `context.startService(termuxIntent)`. On success, log `"Termux started via startService"`.
   - On `IllegalStateException` or `SecurityException`: Log `"startService blocked: <exception>"`, then attempt `context.bindService(termuxIntent, connection, Context.BIND_AUTO_CREATE)`.
   - If `bindService` fails or throws: Log the error and give up silently without crashing.
   - **Zero calls to `startForegroundService` or `startForeground()` under any circumstance.**
2. **Android 12 vs 13+ Storage Permissions**:
   - Manifest declares:
     - `<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />`
     - `<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />`
     - `<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />`
     - `<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />`
     - `<uses-permission android:name="com.termux.permission.RUN_COMMAND" />`
     - `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />`
     - `<queries><package android:name="com.termux" /></queries>`
   - Runtime request: On Android 12 (API 31/32 or lower), requests `READ_EXTERNAL_STORAGE`. On Android 13+ (API 33+), requests the granular media permissions.
   - Graceful handling: If denied, logs denial; toggle and "Test Trigger" remain fully active.
3. **Dialog Confirmation without PendingIntent**:
   - Uses plain `AlertDialog.Builder(this).setMessage("Clear log?").setPositiveButton(...)` directly to avoid Android 12 `PendingIntent` mutability pitfalls.
4. **Synchronous `onStartJob` Contract**:
   - Returns `false` to indicate work is completed synchronously inline.
   - Re-arms the `JobScheduler` inline before returning.
5. **Strict Exclusion of Bloat**:
   - No `NotificationChannel`, no `startForeground`, no `startForegroundService`, no `POST_NOTIFICATIONS`, no `FOREGROUND_SERVICE` permission, no Compose, no Room, no Hilt, no Retrofit, no Firebase.

---

## 1. Overview & Core Concept

- **What It Does**: Wakes up silently when files are added or modified under `content://media/external/file`, reads the user's configured script path from `SharedPreferences` at fire time, dispatches the execution intent to Termux's `RunCommandService`, and immediately re-arms the job.
- **Target Audience**: Android automation workflows relying on Termux shell scripts.
- **Key Value**: Utmost reliability on Android 12 background restrictions, instant responsiveness, clean audit log, and zero notification noise.

---

## 2. User Experience & Visual Design

### Screen Layout Structure (`activity_main.xml`)

A single `ScrollView` containing a vertical `LinearLayout`:
- **State Indicator**: `TextView` displaying "Trigger: ON" or "Trigger: OFF".
- **Toggle Button**: `Button` labeled "Turn ON" or "Turn OFF".
- **Manual Test Button**: `Button` labeled "Test Trigger" that executes the exact Termux dispatch logic and logs `"manual test fired"`.
- **Script Path Section**:
  - `TextView` label: "Termux Script Path:"
  - `EditText` (single line, `inputType="textUri"`): Defaults to `/data/data/com.termux/files/home/file-bus.sh`.
- **Log Section**:
  - `TextView` label: "Event Log (last 100 entries):"
  - `TextView` (monospace, scrollable/multiline) displaying rolling timestamped events.
- **Log Actions**:
  - `Button` labeled "Clear Log", opening a confirmation `AlertDialog` ("Clear log?").

---

## 3. Key Technical Decisions & Trade-Offs

- **Dispatch Fallback (`startService` -> `bindService`)**:
  - *Chosen Approach*: Try `startService`, if blocked by Android 12 background restrictions catch exception and attempt `bindService`.
  - *Why*: Termux's `RunCommandService` can accept intents via `bindService` when background starts are restricted, avoiding background execution crashes.
- **Inline Job Re-Arming**:
  - *Chosen Approach*: Because `ContentUriTrigger` is one-shot, `onStartJob` calls `scheduleJob(context)` right before returning `false`.
  - *Why*: Ensures seamless continuity for the next file change.
- **On-Write Log Truncation**:
  - *Chosen Approach*: Read on startup loads all lines. On each new log entry, the new entry is appended and the file is truncated to the newest 100 lines.
  - *Why*: Guarantees disk usage stays minimal while reads remain pure and non-destructive.

---

## 4. Technical Architecture

### Component Diagram

```
                 ┌────────────────────────────────┐
                 │   content://media/external/file │
                 └───────────────┬────────────────┘
                                 │ Content URI Trigger
                                 ▼
                     ┌──────────────────────┐
                     │     JobScheduler     │
                     └───────────┬──────────┘
                                 │ onStartJob (returns false)
                                 ▼
                    ┌────────────────────────┐
                    │   TriggerJobService    │
                    │ 1. Read script path    │
                    │ 2. Try startService()  │
                    │    Catch -> bindService│
                    │ 3. Catch all & log     │
                    │ 4. Re-arm JobScheduler │
                    └───────┬────────┬───────┘
                            │        │
               Termux Intent│        │ Log messages
                            ▼        ▼
┌─────────────────────────────────┐ ┌─────────────────────────────────┐
│ com.termux.app.RunCommandService│ │           LogManager            │
│ com.termux.RUN_COMMAND          │ │ - trigger_log.txt in filesDir   │
│ RUN_COMMAND_PATH                │ │ - Writes timestamped entry      │
│ RUN_COMMAND_BACKGROUND: true    │ │ - Truncates to last 100 on write│
└─────────────────────────────────┘ └────────────────┬────────────────┘
                                                     │ Reads on startup &
                                                     │ live listener
                                                     ▼
                                            ┌──────────────────┐
                                            │   MainActivity   │
                                            └──────────────────┘
```

### Components to Generate

1. **`build.gradle.kts` (app)**: Remove Compose, Firebase, Room, KSP. Keep `androidx.core:core-ktx:1.18.0` and `androidx.appcompat:appcompat:1.7.0` (or `core-ktx` + activity). Set `minSdk = 26`, `targetSdk = 34`.
2. **`AndroidManifest.xml`**: Permissions, `<queries>`, `MainActivity`, `TriggerJobService` (with `android.permission.BIND_JOB_SERVICE`), and `BootReceiver` (with `RECEIVE_BOOT_COMPLETED`).
3. **`activity_main.xml`**: ScrollView with LinearLayout, text views, buttons, and editable path.
4. **`LogManager.kt`**: File logging utility with on-write truncation to 100 entries, thread safety, and UI callback support.
5. **`TriggerJobService.kt`**: JobService observing URI, dispatching Termux fallback chain, re-scheduling, returning false.
6. **`BootReceiver.kt`**: BroadcastReceiver restoring trigger if `trigger_enabled` is true.
7. **`MainActivity.kt`**: UI state, toggle button, test trigger button, script path persistence, permission handling, and log display.
8. **`README.md`**: Guide explaining build (`gradle assembleDebug`), APK installation, Termux setup, and `allow-external-apps = true`.
