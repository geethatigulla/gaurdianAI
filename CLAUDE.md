# Guardian AI — repo rules

Native Android scam-checker. Architecture: `docs/architecture.md`. Scoring: `docs/risk-scoring.md`. v0.1 is
**Android-only, on-device rules only** — no backend, no ML, no network.

## Modules
- `engine/` — **pure Kotlin/JVM**. No `android.*` / `androidx.*` imports, ever. All detection, scoring
  and explanation lives here. `Analyzer.analyze(SecurityEvent): RiskResult` is the only entry point.
- `core/` — Compose design system (theme, risk colours, `RiskBadge`, `RiskGauge`, cards).
- `app/` — UI (Compose + Navigation), Hilt, Room, DataStore. Features under `app/.../feature/*`; OS integrations
  (SMS receiver, notification listener, accessibility overlay, QR, widget/tile) under `app/.../platform/*`.
- UI style: 3 tabs (Home / History / Settings). Short labels, icons, one verdict per result — long text goes in Help/Details.

## Non-negotiables
- The UI never computes risk. `RiskResult` is the only contract between engine and app.
- Never fake data: unavailable checks are `CheckStatus.UNAVAILABLE`, never a made-up score.
- "No indicators" must never be presented as "safe".
- Raw message text is not stored unless `storeRawText` is on, and then only via `Redactor.redact`.
- Changing any weight/cap/band in `ScoringConfig` requires bumping `version`.
- New rule ⇒ new/updated test in `engine/src/test`. Test fixtures are synthetic, written by us.
- No `INTERNET` permission in v0.1 (check the merged manifest after adding any dependency — this is why QR uses ZXing, not ML Kit).

## Commands (Windows, from repo root)
```
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :engine:test        # fast, pure JVM
.\gradlew.bat assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## Versions
AGP 8.11.1 · Kotlin 2.1.20 · KSP 2.1.20-2.0.1 · Hilt 2.56.2 · Compose BOM 2025.06.00 · Room 2.7.2 ·
compileSdk 36 · targetSdk 35 · minSdk 29 · JDK 17 bytecode.

## Build phases
1 Shell ✅ · 2 Engine ✅ · 3 Text analysis + history ✅ · 4 Share / text-selection link checks ✅ (default-browser mode removed: too hard to use) ·
4b Messaging-app checks via NotificationListenerService ✅ · 4c Floating scan button (AccessibilityService overlay: screen text + screenshot QR) ✅ · 4d Browser website guard (address bar via accessibility) + trusted domains + local-address skip ✅ · 5 QR (CameraX + ZXing, tile, widget) ✅ · 6 Number & IP/domain checks, call screening (unknown callers), reported numbers ✅ · 7 Settings/privacy (WorkManager retention, biometric lock) ·
8 SMS receiver + notifications ✅ (built, untested on device) · 9 Polish, UI tests, docs.
