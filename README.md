# Termux Storage Trigger

A lean, battery-efficient native Android utility (Kotlin) that silently listens for file creation and modifications in external storage via `JobScheduler` and automatically invokes a Termux script.

## Features

- **Silent Background Operation**: Uses `JobScheduler.addTriggerContentUri` on `content://media/external/file` (with `FLAG_NOTIFY_FOR_DESCENDANTS`). No foreground services, no status bar icons, and no notifications.
- **Android 12 Optimized**: Implements a robust dispatch fallback chain (`startService` -> `bindService`) to gracefully handle Android 12 background start restrictions without crashing.
- **Dynamic Script Path**: Pre-configured with `/data/data/com.termux/files/home/file-bus.sh` and editable on-device without rebuilding.
- **Manual "Test Trigger"**: Allows instantaneous testing of the Termux intent from the UI to verify connectivity.
- **Persistent Rolling Log**: Stores the last 100 events in internal app storage (`filesDir/trigger_log.txt`) with timestamps, persisted across restarts.
- **Boot Re-Arming**: Re-arms the trigger on device reboot via `BOOT_COMPLETED` if previously turned ON.

---

## Termux Setup (Required)

For Termux to receive commands from external apps, configure Termux as follows:

### 1. Grant Storage Access
Open Termux and run:
```bash
termux-setup-storage
```
Grant the Android storage permission prompt when requested.

### 2. Enable External App Command Execution
Termux requires explicit permission in `~/.termux/termux.properties` to accept `RUN_COMMAND` intents:
```bash
mkdir -p ~/.termux
echo "allow-external-apps = true" >> ~/.termux/termux.properties
termux-reload-settings
```

### 3. Create the Target Script
Create your script at the configured path (default: `~/file-bus.sh`):
```bash
cat << 'EOF' > ~/file-bus.sh
#!/data/data/com.termux/files/usr/bin/bash
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Trigger fired by Termux Storage Trigger" >> ~/file-bus.log
EOF
```

Ensure the script has executable permissions:
```bash
chmod +x ~/file-bus.sh
```

---

## Building the APK

To build the debug APK using Gradle:

```bash
gradle assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Installing the APK

Install the generated APK onto your Android device via `adb`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or transfer `app-debug.apk` directly to your phone and install via your file manager.

---

## First Run & Verification

1. Open **Termux Trigger**.
2. When prompted, grant media/storage read permissions.
3. Tap **Test Trigger** to verify connectivity with Termux. The log below will indicate `"manual test fired"` followed by `"Termux started via startService"` (or `bindService`).
4. Tap **Turn ON** to arm the automated background trigger.
5. Create, download, or take a picture with your device; within the 5–60 second observation window, `JobScheduler` will wake up, dispatch to Termux, and re-arm automatically.
