# Project Ingwe 🐆

**A glanceable OBD2 dashboard for the 2018 VW Amarok 3.0 V6 TDI.**

Dark theme · Jetpack Compose · Kotlin · BLE ELM327/STN · No Play Services

---

## Quick Start (Windows)

### Prerequisites
1. Install [Android Studio](https://developer.android.com/studio) OR just the
   [Android command-line tools](https://developer.android.com/studio#command-line-tools-only)
2. Set `ANDROID_HOME` to your SDK path, e.g.:
   ```powershell
   $env:ANDROID_HOME = "C:\Users\YourName\AppData\Local\Android\Sdk"
   ```
3. Java 17+ in PATH (bundled with Android Studio)

### Build & Test
```powershell
cd AmarokDash
.\gradlew test                    # Run all JVM unit tests (no device needed)
.\gradlew assembleDebug           # Build debug APK
.\gradlew installDebug            # Install on connected phone
```

### Install on Head Unit
```powershell
# Enable ADB over TCP/IP on the head unit first (Settings > Developer Options)
adb connect <HEAD_UNIT_IP>:5555
adb -s <HEAD_UNIT_IP>:5555 install app\build\outputs\apk\debug\app-debug.apk
```

---

## Milestone Status

| Milestone | Status | Description |
|-----------|--------|-------------|
| **M1** | ✅ Done | Skeleton, MockTransport, ISO-TP parser, unit tests |
| M2 | 🔲 | ELM driver, Mode 01 polling |
| M3 | 🔲 | BLE transport, live readout |
| M4 | 🔲 | Scheduler, full gauge UI |
| M5 | 🔲 | DTC reading + screen |
| M6 | 🔲 | UDS DID scanner, logging |
| M7 | 🔲 | Foreground service, reconnect |

---

## Project Structure

```
app/src/main/java/io/ingwe/dash/
├── data/
│   ├── transport/      Transport interface, MockTransport, BleTransport (stub), Allowlist
│   ├── isotp/          IsoTpAssembler — multi-frame reassembly
│   ├── obd/            ObdResponseParser, SimpleExprEval, formula engine
│   └── signals/        SignalDefinition (Kotlin model for signals.json)
├── service/            OBDForegroundService (stub, M7)
└── ui/
    ├── dashboard/      Dashboard screen (mock data, M1)
    ├── terminal/       Raw AT command terminal
    ├── navigation/     NavHost, Screen routes
    └── theme/          Dark theme, colours, typography

app/src/main/assets/
├── signals.json        Signal definitions (add PIDs/DIDs here, no code needed)
└── dtc_descriptions.json  (added in M5)

app/src/test/java/io/ingwe/dash/
├── TransportAllowlistTest  ← SAFETY TEST (must always pass)
├── MockTransportTest
├── IsoTpAssemblerTest
└── ObdResponseParserTest
```

---

## Safety Constraints

- **Read-only**: Only OBD services `0x01 03 07 09 0A 19 22` are allowed.
- **Clear codes** (`0x04`/`0x14`) are blocked by default and require explicit Settings toggle + confirmation dialog.
- Enforced at `TransportGuard.validateCommand()` — called by every Transport implementation.
- Proven by `TransportAllowlistTest` — a CI requirement.

---

## Adapter Support (M3)

| Adapter | BLE UUID Profile |
|---------|-----------------|
| Generic (most BLE adapters) | FFF0/FFF1/FFF2 |
| HM-10 based | FFE0/FFE1 |
| Nordic UART (nRF) | 6E400001 service |
| Vgate vLinker MC+ / iCar Pro | BEF0 (tentative, confirm in M3) |

---

## Signals (signals.json)

Add a new gauge by appending to `app/src/main/assets/signals.json`:
```json
{
  "id": "my_signal",
  "label": "My Signal",
  "ecu": "7E0",
  "service": "01",
  "pid": "XX",
  "formula": "A-40",
  "unit": "°C",
  "min": 0, "max": 200,
  "fastHz": false,
  "verified": false
}
```
No code changes needed.
