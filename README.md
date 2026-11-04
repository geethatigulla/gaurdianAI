# Guardian AI

An Android app that checks messages, links, QR codes, phone numbers and websites for the warning
signs of common Indian scams (fake KYC alerts, OTP/UPI-PIN requests, look-alike bank links,
"digital arrest" threats, APK lures, fake refund QR codes), **entirely on the phone**.

**v0.1 scope:** native Android (minSdk 29), rule-based detection, no backend, no ML, **no internet
permission**. Online reputation checks are reported honestly as *unavailable*, never guessed.

## Features

**Check anything manually**: message, link, QR code (camera or screenshot), phone number, IP address
or domain. Every result shows one plain verdict ("Scam alert", "Suspicious", "No warning signs"), short
reasons you can tap for detail, and what to do.

**Automatic protection** (each one opt-in; Android never lets an app block or delay other apps'
messages, so Guardian warns):

| Channel | How it works |
|---|---|
| Floating button | A shield over every app; tap it to check the visible screen (text + any QR code) |
| SMS | Every incoming SMS is checked as it arrives |
| WhatsApp, Telegram, Gmail… | New messages are checked from their notifications (notification access) |
| Websites | Reads only the browser's address bar; a risky site gets a full-screen warning with "Go back" |
| Calls | Unknown incoming callers are checked before the phone rings (call-screening role); optional blocking of numbers you reported |

**Other ways in:** share text, links or QR screenshots to Guardian · select text → "Check with
Guardian" · home-screen widget · Quick Settings tile · app-icon shortcuts.

**Privacy:** nothing leaves the phone · message text is not stored unless you turn it on (then
redacted) · history auto-deletes daily (off / 7 / 30 / 90 / 365 days) · optional app lock
(fingerprint/face/screen lock) · local and development addresses (localhost, 192.168.x.x, *.local)
are never flagged · you can mark your own sites as trusted and report scam numbers.

## Project layout

```
engine/  pure Kotlin/JVM: extract → detect → intel (stub) → risk → explain   (168 JUnit tests)
core/    Compose design system (theme, risk colours, badges, gauge)           (3 UI tests)
app/     Compose UI, Hilt, Room, DataStore, WorkManager, Android integrations (8 unit + 4 UI tests)
docs/    architecture.md · risk-scoring.md · demo-script.md · evaluation/
```

## Build & run

Open the folder in Android Studio (it bundles a JDK), or from a terminal (on Windows set
`JAVA_HOME` to Android Studio's `jbr` folder first):

```bash
./gradlew :engine:test             # engine tests, a few seconds
./gradlew :app:testDebugUnitTest   # app unit tests
./gradlew assembleDebug            # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug             # install on a connected phone
./gradlew connectedDebugAndroidTest  # UI tests, needs a connected phone
./gradlew :engine:evaluate         # precision/recall/F1 on the labelled dataset → docs/evaluation/
```

Installing the APK from a file on Android 13+ may be blocked by Play Protect because the app asks for
SMS/notification access; install over USB or Wireless debugging instead. Accessibility and
notification access then need App info → ⋮ → *Allow restricted settings* once.

**Cloud builds (no local Android setup):** every push to `master` builds the debug APK on GitHub
Actions ([workflow](.github/workflows/android-apk.yml)) and attaches it to a private pre-release
(`build-N`) under Releases, so it can be downloaded straight onto a phone. Builds are signed with the
debug key in the `DEBUG_KEYSTORE_B64` repo secret (base64 PKCS#12, alias `androiddebugkey`, password
`android`), so each new APK installs over the last one.

## Permissions

| Permission | Why | When asked |
|---|---|---|
| RECEIVE_SMS | Check incoming SMS | Turning on SMS protection |
| POST_NOTIFICATIONS | Scam alerts | Turning on any automatic protection |
| CAMERA | QR scanner | Opening the scanner |
| USE_BIOMETRIC | App lock | Install time (no prompt) |
| Notification access, Accessibility, Call-screening role | Messaging-app checks, floating button + website warnings, call checks | Turning each on, in system settings |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE | Added by WorkManager for the daily cleanup job | Install time (no prompt) |

There is **no INTERNET permission**.

## How scoring works

See [docs/risk-scoring.md](docs/risk-scoring.md). In short: warning signs belong to families, only
the strongest sign per family counts, families combine with noisy-OR, weak-only results are capped at
39, and evidence quality is reported separately from risk. Scores are **not calibrated**.

## Limitations (v0.1)

- English-only rules; Hindi/Telugu/Hinglish scams may be missed
- No online reputation for links, numbers or UPI IDs (no backend in v0.1)
- Messaging-app checks read notifications: muted chats or hidden notification content are missed
- Website warnings appear about a second after a page starts loading; Android can't stop a browser earlier
- Can't listen to calls; call checks judge the number only
- Screens that block screenshots (some banking apps) hide QR codes from the floating button
- Play Store release would need policy approval for SMS, notification access and accessibility use
- All test messages are synthetic, written for this project
