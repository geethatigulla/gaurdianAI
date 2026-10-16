# Guardian AI

An Android app that checks messages and links for the warning signs of common Indian scams —
fake KYC alerts, OTP/UPI-PIN requests, look-alike bank links, "digital arrest" threats, APK lures —
entirely on the phone.

**v0.1 scope:** native Android (minSdk 29), rule-based detection only, no backend, no ML, no
network permission. Reputation checks are reported honestly as *unavailable*.

## What works now
- Onboarding (what it does, privacy, honest limits)
- **Check a message** — paste text → risk score, category, evidence list, checks, advice
- **Check a link** — paste a URL (never opened) → look-alike / IP / `@` / APK / shortener checks
- **Activity** — history with search, level/type filters, delete, clear all
- **Assistant (offline)** — explain last result + reviewed FAQ (1930 / cybercrime.gov.in)
- **Floating button** — a shield over every app; tap it to check the message, link or QR code on screen (accessibility service, reads the screen only on tap)
- **Automatic protection** (each opt-in; Android never lets Guardian block or delay messages, only warn):
  - **SMS** — every incoming SMS checked as it arrives
  - **WhatsApp, Telegram, Gmail, Signal…** — new messages checked from their notifications (needs notification access)
  - **Websites** — reads only the address bar of Chrome / Samsung Internet / Edge / Brave / Firefox; a risky site gets a full-screen warning with "Go back" (no browser switch needed)
  - Local/dev addresses (localhost, 192.168.x.x, *.local) are never flagged; users can mark their own sites as trusted
- **QR codes** — in-app scanner (camera or screenshot), Quick Settings tile, home-screen widget; shows what a QR does (e.g. "this makes you PAY ₹4999") before anything opens
- **From anywhere** — share text/links/QR screenshots to Guardian, select text → "Check with Guardian", app-icon shortcuts
- **Settings** — save-text opt-in (redacted), retention (off / 7 / 30 / 90 / 365 days), theme, About

## Project layout
```
engine/  pure Kotlin: extract → detect → intel(stub) → risk → explain   (132 JUnit tests)
core/    Compose design system
app/     Compose UI, Hilt, Room, DataStore
docs/    risk-scoring.md
```

## Build & run
Open the folder in Android Studio (it bundles a JDK), or from a terminal:

```bash
./gradlew :engine:test      # engine tests, ~2 s
./gradlew assembleDebug     # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # with a device/emulator connected
```

On Windows set `JAVA_HOME` to Android Studio's `jbr` folder first.

## How scoring works
See [docs/risk-scoring.md](docs/risk-scoring.md). Short version: indicators are grouped into
families, only the strongest per family counts, families combine with noisy-OR, weak-only results
are capped at 39. Scores are **not calibrated**.

## Limitations (v0.1)
- English-only rules; Hindi/Telugu/Hinglish scams may be missed
- No online reputation for links, numbers or UPI IDs
- Messaging-app checks read notifications: muted chats or hidden notification content are missed
- Can't hook into other QR-scanner apps; use Guardian's scanner/tile/widget or share a screenshot
- Android 13+ marks notification access as a "restricted setting" for sideloaded APKs (App info → ⋮ → Allow restricted settings)
- No phone-number lookup yet (Phase 6)
- All test messages are synthetic, written for this project
