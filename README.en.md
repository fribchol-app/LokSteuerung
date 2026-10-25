# Loksteuerung

[Deutsch](README.md) | **English**

Android app for controlling model locomotives over WiFi, for locos running the
[PiedPiperS](https://github.com/jorail/PiedPiperS) firmware (ESP32).

Scan the loco's QR code, connect, drive. No command station, no cables, no browser.

> The idea and the locomotive side come from the project
> [PiedPiperS](https://github.com/jorail/PiedPiperS) by jorail: an ESP32 inside the loco opens
> its own WiFi network and can be controlled through a web page. This app is the matching
> Android remote control for it.

<p align="center">
  <img src="source/docs/screenshots/startseite.png" alt="Start screen" width="260">
  &nbsp;&nbsp;
  <img src="source/docs/screenshots/steuerung.png" alt="Control panel" width="260">
</p>

> The app itself is in German.

## Download

**[Download LokSteuerungV1.0.0.apk](LokSteuerungV1.0.0.apk)** (Android 10 or newer)

Or simply scan the QR code with your phone:

<p align="center">
  <img src="source/docs/download-qr.png" alt="QR code to download the app" width="220">
</p>

Tap the APK on your phone and confirm "Allow installation from this source".

The QR code is also available as a printable
[A4 sheet (PDF)](source/docs/Loksteuerung-Download-QR.pdf), for example to put next to the layout.

---

## Features

- **Connect via QR code:** The app reads a WiFi QR code and joins the loco's WiFi network on its own.
- **Demo mode:** "Ohne Lok ausprobieren" (try without a loco) lets you test the controls without any hardware.
- **Center-zero throttle:** left for reverse, center for stop, right for forward, 32 speed steps. Every step gives a noticeable vibration.
- **HALTEN (halt):** The loco slows down step by step until it stops smoothly.
- **STOPP (stop):** Emergency brake, the motor is switched off immediately.
- **Speedometer and trip recorder:** scale speed, distance travelled on the layout and at scale, and driving time. Everything is calculated from the speed step, no sensors required.
- **Headlight** on and off.
- **One driver per loco:** If a loco is already connected to another device, the app reports it as busy. If someone sends commands anyway, a warning is shown.
- **Safe exit:** Leaving the control panel brings the loco to a smooth stop automatically.

## Requirements

| | |
|---|---|
| Smartphone | Android 10 or newer |
| Locomotive | ESP32 with [PiedPiperS](https://github.com/jorail/PiedPiperS) firmware |
| Development | [Android Studio](https://developer.android.com/studio) (current version) |

---

## Guide: Building the app with Android Studio

### 1. Download the project

Click **Code → Download ZIP** at the top of this page and extract the ZIP file.
Alternatively, directly in Android Studio: **File → New → Project from Version Control**
and paste the address of this repository.

### 2. Open in Android Studio

1. Choose **File → Open**.
2. Select the **`source`** folder (it contains `settings.gradle.kts` and the `app` folder).
3. Confirm with **OK** and choose **Trust Project** if asked.

> If extracting creates a `Loksteuerung` folder inside another `Loksteuerung` folder,
> open the **inner** one. Otherwise the Build menu stays greyed out.

### 3. Wait for the Gradle sync

On first open, Android Studio downloads all libraries. A progress bar runs in the bottom right.
It is done when "Sync finished" is shown.

If an upgrade of the Android Gradle Plugin is offered, you can skip it with "Remind me later".

### 4. Run on your own phone

1. On the phone, open **Settings → About phone → Software information** and tap **Build number** seven times.
2. Turn on **USB debugging** in the **Developer options**.
3. Connect the phone via USB and confirm "Allow USB debugging".
4. In Android Studio, select the phone at the top and click **Run**.

### 5. Build an APK

**Test APK** (for your own phone):

**Build → Generate App Bundles or APKs → Generate APKs**

The file is then located at `source/app/build/outputs/apk/debug/app-debug.apk`.

**Signed release APK** (for sharing):

1. Choose **Build → Generate Signed App Bundle or APK…** and then **APK**.
2. Select an existing keystore or create a new one with **Create new…**.
3. Choose **release** and click **Create**.

The file is then located at `source/app/release/app-release.apk`.

> Keep the keystore and its passwords safe and **never** upload them to the repository.
> Without it you can no longer sign updates.

### 6. Install the APK

Copy the APK to the phone (USB, cloud or email) and tap it in the file manager.
Android asks once whether apps from this source may be installed.

---

## Usage

### Connecting to a locomotive

1. Switch on the loco. It opens its own WiFi network.
2. In the app, tap **QR-Code scannen** (scan QR code) and scan the loco's code.
3. Confirm the Android dialog "Connect to device".
4. The control panel opens.

If the phone is already connected to the loco's WiFi, use
**"Schon im Lok-WLAN? Direkt verbinden"** (already in the loco WiFi? connect directly).

### Driving

| Control | Function |
|---|---|
| Drag the throttle to the right | drive forward |
| Drag the throttle to the left | drive in reverse |
| Tap next to the throttle knob | one step faster or slower |
| **HALTEN** | slow down smoothly to a stop |
| **STOPP** | emergency brake |
| Light bulb, top right | headlight on and off |
| Gear, top right | set scale and top speed |
| Arrow, top left | stop the loco and disconnect |

Changing direction while driving is safe: the loco first slows down, then reverses and
accelerates again.

### Several locomotives

Each loco has its own WiFi network, and a phone can only be in one of them at a time.
To switch, tap the arrow at the top left and scan the next loco's QR code.

Important: Each loco needs its **own WiFi name** (SSID). It is set in the firmware's
`lok.ini` file:

```ini
[wifi]
ssid = _gruene_Dampflok_3915
password =
```

---

## Creating a QR code for the locomotive

The app expects a **WiFi QR code** in the standard format:

```text
WIFI:T:nopass;S:<loco WiFi name>;;
```

If the loco has a password:

```text
WIFI:T:WPA;S:<loco WiFi name>;P:<password>;;
```

Such codes can be created with any WiFi QR code generator. Ideally print it and stick it on the
loco or the layout.

An example for the firmware's default SSID is located at
[`source/extras/beispiel-lok-wlan-qr.png`](source/extras/beispiel-lok-wlan-qr.png).

The QR code from the PiedPiperS repository (`http://192.168.4.1`) works as well.
It contains no WiFi credentials though, so the phone must already be connected to the loco's WiFi.

---

## Speed and distance

The loco has no sensors, so the app calculates all values from the speed step:

- **Scale speed:** speed step 32 equals 160 km/h by default.
- **Distance on the layout:** scale speed divided by the scale (default H0, 1:87).
- **Distance at scale:** the distance a real train would have covered.

Both values can be adjusted via the gear icon. The displays are estimates, not measurements.

---

## Technical details

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Connection | WebSocket via OkHttp |
| QR scanner | Google Code Scanner (no camera permission) |
| Minimum version | Android 10 (API 29) |

The app speaks the same protocol as the firmware's web interface:

| Command | Meaning |
|---|---|
| `=N` | set speed step N (0 to 32), the loco moves there smoothly |
| `00` | emergency brake |
| `<` | reverse direction |
| `L` | toggle headlight |
| `?` | request status |

Connection: `ws://192.168.4.1/ws`

### Project structure

```text
source/app/src/main/java/com/fribchol/loksteuerung/
├── MainActivity.kt      entry point, QR scanner, permissions
├── LocoViewModel.kt     app state, driving logic, speed calculation
├── LocoLink.kt          connection to the real loco and the demo loco
├── FirmwareModel.kt     replica of the firmware's command processing
├── LocoWifi.kt          joining the loco's WiFi network
├── LocoQr.kt            QR code parsing
└── ui/
    ├── StartScreen.kt   start screen
    ├── ControlScreen.kt control panel
    ├── SpeedGauge.kt    speedometer
    ├── DirectionSlider.kt throttle
    └── Theme.kt         colors and design
```

---

## Acknowledgements and origin of the idea

The original idea for this project comes from
[PiedPiperS](https://github.com/jorail/PiedPiperS) by jorail. It shows how to equip a model
locomotive with an ESP32, a motor driver and a USB power bank and control it over WiFi from a
smartphone. The complete firmware, the build instructions and the hardware come from that project.

This Android app was built on top of it. It replaces the browser-based web interface with a
dedicated app featuring a throttle, a speedometer, a demo mode and QR code pairing.

The app contains no code from PiedPiperS, it only uses the same control protocol. For building
the loco hardware and for the firmware, please use the PiedPiperS repository directly.

Many thanks to jorail for this great project.

## License

This project is licensed under the [MIT License](LICENSE). The code may be freely used,
modified and distributed as long as the license notice is retained.
