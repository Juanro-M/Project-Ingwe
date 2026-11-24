# Project Ingwe

**Glanceable OBD-II and UDS telemetry cluster for the Volkswagen Amarok 3.0 V6 TDI.**

Designed for 1280x720 landscape Android head units (Unisoc UIS8581A / TS18 / OYIN platform).
Built with Jetpack Compose, Kotlin, custom Canvas gauge rendering, and direct BLE/USB transport.

---

## Cockpit Display Overview

The instrument cluster features high-contrast analog dials rendered on hardware-accelerated Compose Canvas with sub-divided graduation tracks, sweep needles, peak indicators, and digital readouts.

### 1. Turbo Cluster Page
* **Tachometer**: 0 to 6,000 RPM range tailored for Amarok TDI engines with an alert redline zone starting at 4,500 RPM.
* **Precision Boost Gauge**:
  * Seamless one-touch toggle between **BAR** (`-1.0` to `+2.5 BAR`) and **PSI** (`-15.0` to `+35.0 PSI`).
  * Vacuum sector (`< 0`) highlighted in cyan, positive boost in amber, and overboost in red.
  * Digital readout with peak boost hold marker (`PK`).
* **Speedometer**: 0 to 220 km/h with center digital telemetry.
* **Thermal and Turbo Auxiliary Meters**:
  * **Pre-Turbo EGT**: Exhaust gas temperature before turbine inlet (`0` to `1000 °C`, warning at `720 °C`, alert at `780 °C`).
  * **Charge Air Temp**: Intercooler discharge temperature (`0` to `90 °C`).
  * **VGT Vane Position**: Variable turbine geometry actuator position (`0` to `100 %`).
  * **Engine Output Torque**: Delivered flywheel torque (`0` to `650 Nm`).

### 2. ZF 8HP Driveline Page
* **Current Gear**: Large digital indicator (`D1` through `D8`, `S1` through `S8`, `R`, `N`, `P`).
* **Torque Converter Lockup**: Real-time clutch engagement status (`LOCKED`, `SLIP`, `OPEN`).
* **Transmission Fluid Temp (ATF)**: Monitored via TCM address `0x7E1` (`40` to `150 °C`, warning at `105 °C`, critical at `115 °C`).
* **Engine Torque Delivery**: Live torque input into transmission (`Nm`).
* **Coolant and Oil Temperatures**: Operating baseline gauges.

### 3. 4Motion Off-Road Page
* **Steering Wheel Angle**: Front wheel angle in degrees (`L 0-540°` / `R 0-540°`).
* **Inclinometers**:
  * **Pitch Angle**: Vehicle ascent and descent grade (`-45°` to `+45°`).
  * **Roll Angle**: Lateral tilt with rollover caution (`-45°` to `+45°`).
* **Traction Wheel Slip**: Differential wheel speed percentage across the 4Motion system.
* **Alternator Battery Voltage**: Real-time electrical system monitoring (`10.0` to `16.0 V`).

### 4. Diagnostic Trouble Code (DTC) Page
* **MIL Status**: Live Malfunction Indicator Lamp (Check Engine Light) alert banner.
* **ECU Targeting**: Configured for ECU address `0x7E0` (Bosch EDC17 / MD1).
* **ECU Scanner**: OBD-II Mode 03 (Confirmed) and Mode 07 (Pending) fault interrogation.
* **Fault Code Reset**: OBD-II Mode 04 code clearing with confirmation safety dialog.
* **Freeze-Frame Inspection**: Engine load, RPM, target vs. actual boost, and temperature snapshots.

---

## Active Signal Definitions (`signals.json`)

| Signal ID | Label | ECU | Service | PID / DID | Formula | Unit | Range |
|---|---|---|---|---|---|---|---|
| `rpm` | Engine RPM | 7E0 | 01 | 0C | `((A*256)+B)/4` | rpm | 0 - 5000 |
| `vehicle_speed` | Vehicle Speed | 7E0 | 01 | 0D | `A` | km/h | 0 - 200 |
| `engine_load` | Engine Load | 7E0 | 01 | 04 | `A*100/255` | % | 0 - 100 |
| `coolant_temp` | Coolant Temp | 7E0 | 01 | 05 | `A-40` | °C | -40 - 130 |
| `intake_air_temp` | Intake Air Temp | 7E0 | 01 | 0F | `A-40` | °C | -40 - 100 |
| `oil_temp` | Engine Oil Temp | 7E0 | 01 | 5C | `A-40` | °C | -40 - 160 |
| `battery_voltage` | Battery Voltage | 7E0 | 01 | 42 | `((A*256)+B)/1000` | V | 10 - 16 |
| `fuel_rate` | Fuel Rate | 7E0 | 01 | 5E | `((A*256)+B)*0.05` | L/h | 0 - 50 |
| `boost_actual` | Actual Boost | 7E0 | 01 | 6F | `B` | kPa abs | 0 - 400 |
| `boost_target` | Target Boost | 7E0 | 01 | 70 | `B` | kPa abs | 0 - 400 |
| `egt_pre_turbo` | Pre-Turbo EGT | 7E0 | 01 | 78 | `((C*256)+D)*0.1-40` | °C | 0 - 1000 |
| `charge_air_temp` | Intercooler Temp | 7E0 | 01 | 77 | `B-40` | °C | -20 - 120 |
| `turbo_vgt_duty` | VGT Vane Position | 7E0 | 01 | 7D | `A*100/255` | % | 0 - 100 |
| `engine_torque` | Engine Torque | 7E0 | 01 | 62 | `((A*256)+B)-500` | Nm | 0 - 750 |
| `trans_fluid_temp` | ATF Fluid Temp | 7E1 | 22 | 11A8 | `A-40` | °C | 0 - 150 |
| `trans_gear` | Current Gear | 7E1 | 22 | 1002 | `A` | gear | 1 - 8 |
| `trans_lockup` | Lockup State | 7E1 | 22 | 1003 | `A` | state | 0 - 2 |
| `steering_angle` | Steering Angle | 7E2 | 22 | 0250 | `((A*256)+B)*0.1-540` | ° | -540 - 540 |
| `wheel_slip` | Wheel Slip | 7E2 | 22 | 0251 | `A*0.5` | % | 0 - 50 |
| `pitch_angle` | Pitch Angle | 7E2 | 22 | 0252 | `A-45` | ° | -45 - 45 |
| `roll_angle` | Roll Angle | 7E2 | 22 | 0253 | `A-45` | ° | -45 - 45 |

---

## Head Unit Installation (UIS8581A / TS18)

### Direct USB Sideload (No ADB Required)
1. Build the APK using `./gradlew assembleDebug` (outputs to `app/build/outputs/apk/debug/app-debug.apk`).
2. Copy `app-debug.apk` onto a FAT32-formatted USB flash drive.
3. Plug the drive into the head unit's USB port.
4. Open the pre-installed **File Manager** on the head unit, locate the APK on the USB drive, and tap to install.
5. Allow installation from unknown sources when prompted.

### Factory Settings Reference Passwords
Common passwords for the UIS8581A / OYIN / TS18 platform if prompted by system menus:
* `8888`
* `3368`
* `1234` or `123456`
* `1617` or `16176699`
* `0000`

---

## Recommended Hardware Adapters

* **Vgate vLinker MC+ (Bluetooth 4.0 / BLE)**: Fully compatible with the Amarok CAN network, supports high-speed baud rates and multi-frame UDS polling.
* **Vgate vLinker FS (Wired USB)**: Connects directly to the head unit USB port for continuous wired communication with zero Bluetooth latency.
* **OBDLink CX / MX+ (BLE)**: High throughput and stable command pipelining.
