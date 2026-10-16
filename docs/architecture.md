# Guardian AI — Architecture (v0.1, Android-only / local rules)

**Scope of this build:** Native Android, minSdk 29, targetSdk 35. No backend, no ML, no external threat-intel calls. Every "reputation" check reports `UNAVAILABLE` honestly. Designed so a FastAPI backend and an ML classifier slot in later without touching UI.

---

## 1. Key decisions (and why)

| Decision | Choice | Why |
|---|---|---|
| Module layout | 3 Gradle modules: `app`, `core`, `engine` | Spec asks for modularity; 15 feature modules is overkill for one student + Claude Code. Features live as packages under `app/feature/*`. Split later if needed. |
| `engine` is pure Kotlin/JVM | No Android deps | Detectors + risk engine run as plain JUnit tests in <2s. This is where the marks are — make it testable. |
| minSdk 29 | Android 10 | `CallScreeningService` + `RoleManager` available. Covers ~95% of active Indian devices. |
| Detection | Rule engine only (Layer 1) | Layers 2–3 (ML, threat intel) stubbed behind interfaces. No fake scores, no fake datasets. |
| Storage | Room (history) + DataStore (settings) | Local-first. History retention is a user setting. Raw message text stored only if user opts in; default stores redacted indicators + score. |
| DI | Hilt | Standard, works with Compose + ViewModel. |
| QR | CameraX + ML Kit Barcode (bundled) | Decodes offline. Never auto-opens destination. |
| Phone numbers | libphonenumber-android | Format/region validity only. Reputation = UNAVAILABLE. |
| Auth | None in v0.1 | Spec says don't require account for local features. Optional biometric app-lock in Phase 7. |
| Navigation | Navigation Compose, 5 bottom tabs | Home / Scan / Activity / Assistant / Settings. Assistant tab shows "offline: rule explanations only" state — no LLM in this build. |

---

## 2. Module map

```
guardian-ai/
├── app/                          # Android app (Compose, Hilt, Room, DataStore, CameraX)
│   └── src/main/java/ai/guardian/
│       ├── GuardianApp.kt        # @HiltAndroidApp
│       ├── MainActivity.kt
│       ├── navigation/           # NavHost, bottom bar, routes
│       ├── feature/
│       │   ├── onboarding/       # welcome, privacy, permission explainer
│       │   ├── home/             # dashboard (real counts from Room, empty states)
│       │   ├── scan/             # unified scan hub
│       │   ├── sms/              # manual text analysis, SMS monitor status
│       │   ├── url/              # URL scanner + share-intent entry
│       │   ├── qr/               # CameraX preview, decode, classify
│       │   ├── caller/           # number lookup
│       │   ├── ipdomain/         # format/DNS-less checks only in v0.1
│       │   ├── result/           # ThreatDetails screen (shared by all types)
│       │   ├── history/          # list, search, filter, delete
│       │   ├── assistant/        # rule-grounded explanation view (no LLM)
│       │   ├── learning/         # static tips + quizzes (JSON in assets)
│       │   └── settings/         # privacy, retention, monitoring toggles
│       ├── data/
│       │   ├── db/               # Room: ScanEventEntity, EvidenceEntity, DAOs
│       │   ├── prefs/            # DataStore: UserSettings
│       │   └── repo/             # ScanRepository, SettingsRepository
│       ├── platform/
│       │   ├── sms/SmsReceiver.kt         # Phase 8, RECEIVE_SMS
│       │   ├── share/ShareIntentHandler.kt
│       │   ├── notify/ThreatNotifier.kt
│       │   └── permissions/
│       └── di/                   # Hilt modules
│
├── core/                         # Android library: design system only
│   └── designsystem/             # Color tokens, Theme, RiskBadge, GuardianCard, etc.
│
├── engine/                       # PURE KOTLIN — no Android imports allowed
│   └── src/main/kotlin/ai/guardian/engine/
│       ├── model/                # SecurityEvent, Indicator, Check, RiskResult, RiskLevel
│       ├── extract/              # UrlExtractor, PhoneExtractor, UpiExtractor
│       ├── detect/
│       │   ├── Detector.kt       # interface Detector { fun detect(event): DetectorOutput }
│       │   ├── sms/              # UrgencyRule, CredentialRequestRule, ImpersonationRule, ApkLureRule...
│       │   ├── url/              # IpAsHostRule, ShortenerRule, TyposquatRule, PunycodeRule, LengthRule...
│       │   ├── qr/               # QrContentClassifier (URL / UPI / WIFI / VCARD / TEXT)
│       │   └── caller/           # NumberFormatDetector
│       ├── intel/
│       │   ├── ThreatIntelProvider.kt   # interface; returns Check(UNAVAILABLE) in v0.1
│       │   └── NoopIntelProvider.kt
│       ├── risk/
│       │   ├── RiskEngine.kt     # combine → score → level → evidence quality
│       │   └── ScoringConfig.kt  # weights, families, version "0.1-local"
│       ├── explain/
│       │   └── TemplateExplainer.kt     # deterministic plain-English from indicators
│       └── Analyzer.kt           # facade: analyze(event) -> RiskResult
│   └── src/test/kotlin/...       # JUnit5 — one test file per rule + engine + fixtures
│
├── docs/
├── CLAUDE.md                     # master prompt / repo rules
└── README.md
```

---

## 3. Data flow (every event type, same pipeline)

```
User input / share intent / QR decode / SmsReceiver
        │
        ▼
 SecurityEvent(type, rawInput, source, timestamp)      ← app builds this
        │
        ▼
 engine.Analyzer.analyze(event)
   1. extract   → urls, phones, upiIds, emails
   2. detect    → run all Detectors registered for event.type
                  each returns List<Indicator> + List<Check>
   3. intel     → ThreatIntelProvider.lookup(indicators)   [v0.1: UNAVAILABLE]
   4. risk      → RiskEngine.score(indicators, checks)     → score, level, quality
   5. explain   → TemplateExplainer(result)                → summary + recommendation
        │
        ▼
 RiskResult (typed, versioned)
        │
        ├──► ThreatDetails screen (ResultViewModel)
        ├──► ScanRepository.save(result, settings.retention)   → Room
        └──► ThreatNotifier (only for SMS_EVENT ≥ threshold, Phase 8)
```

Rules: the UI never computes risk. `engine` never touches Android. `RiskResult` is the only contract between them.

---

## 4. Core types (engine/model)

```kotlin
enum class EventType { SMS_EVENT, MANUAL_TEXT_EVENT, URL_EVENT, QR_EVENT, CALL_EVENT, IP_EVENT, DOMAIN_EVENT }
enum class RiskLevel { LOW, GUARDED, MODERATE, HIGH, CRITICAL }       // 0-19,20-39,40-59,60-79,80-100
enum class EvidenceQuality { LOW, MODERATE, HIGH }
enum class CheckStatus { COMPLETED, UNAVAILABLE, SKIPPED, FAILED }
enum class Severity { WEAK, MEDIUM, STRONG }

data class Indicator(val type: String, val family: String, val severity: Severity,
                     val description: String, val source: String, val matchedText: String? = null)
data class Check(val name: String, val status: CheckStatus, val note: String? = null)

data class RiskResult(
  val eventId: String, val eventType: EventType, val riskScore: Int, val riskLevel: RiskLevel,
  val category: String, val evidenceQuality: EvidenceQuality, val summary: String,
  val indicators: List<Indicator>, val checks: List<Check>, val recommendation: String,
  val scoringVersion: String, val analyzedAt: Long
)
```

---

## 5. Risk engine method (v0.1-local) — documented, testable

**Problem the spec calls out:** don't sum correlated signals; don't let one weak signal go high; separate risk from confidence.

**Method:**
1. Each `Indicator` has a `family` (e.g. `URGENCY`, `CREDENTIAL_REQUEST`, `IMPERSONATION`, `SUSPICIOUS_URL`, `PAYMENT_LURE`, `APK_LURE`, `NUMBER_FORMAT`). Within a family take only the **max** severity weight. Kills double-counting ("urgent" + "immediately" + "today" = one URGENCY hit).
2. Severity → weight: `WEAK 0.15, MEDIUM 0.35, STRONG 0.60`.
3. Combine families with noisy-OR: `p = 1 − Π(1 − w_f)`. Score = `round(p × 100)`.
   - One WEAK alone → 15 (LOW). One STRONG alone → 60 (HIGH). STRONG + MEDIUM → 74. STRONG + MEDIUM + MEDIUM → 83 (CRITICAL).
4. **Cap rule:** if the only indicators are WEAK, cap score at 39 (never above GUARDED on weak signals alone).
5. **Evidence quality** is independent of score: `HIGH` if all registered checks COMPLETED; `MODERATE` if ≥50% completed; `LOW` otherwise. In v0.1 reputation checks are UNAVAILABLE so most results are MODERATE — shown honestly in UI as "External reputation not checked".
6. Zero indicators → score 0, level LOW, category `NO_KNOWN_INDICATORS`, summary explicitly says "no known warning signs found — this is not proof the item is safe".
7. `ScoringConfig` holds weights + `version = "0.1-local"`. Stored on every record. Changing weights bumps the version.

Calibration status: **not calibrated**. Bands are UI categories. Document this in the app's About screen and in `docs/risk-scoring.md`.

---

## 6. Platform constraints & honest fallbacks

| Capability | v0.1 behaviour |
|---|---|
| Auto SMS reading | Phase 8: `BroadcastReceiver` on `SMS_RECEIVED` with `RECEIVE_SMS` runtime permission. Fine for sideloaded academic APK. Play Store would require default-SMS-handler or exemption — documented, not claimed. Paste/share fallback always available. |
| Intercepting links in other apps | Not possible. Provide: share-to-Guardian, paste, and optional "open links with Guardian" via `http/https` intent filter (user picks Guardian as handler; shows warning then hands off to browser). |
| Caller reputation | Number format/region validity via libphonenumber. Reputation = `UNAVAILABLE`. `CallScreeningService` deferred to a later phase. |
| Call audio | Out of scope. Not mentioned as a feature. |
| QR interception in other apps | Not possible. In-app camera + pick-image-from-gallery only. |
| IP/domain | Format validation, public/private classification, IPv4/6. DNS/ASN/reputation = `UNAVAILABLE`. |
| Assistant | No LLM. Shows the TemplateExplainer output + static FAQ ("I clicked a link — what now?"). Tab labelled "Assistant (offline mode)". |

---

## 7. Local persistence

**Room**
- `scan_events(id TEXT PK, event_type, source, risk_score, risk_level, category, evidence_quality, scoring_version, summary, recommendation, created_at, raw_input_redacted TEXT NULL, user_feedback TEXT NULL)`
- `scan_evidence(id, scan_event_id FK ON DELETE CASCADE, indicator_type, family, severity, description, source, matched_text NULL)`
- `scan_checks(id, scan_event_id FK, name, status, note NULL)`
- Indexes: `created_at`, `event_type`, `risk_level`.

**DataStore (UserSettings)**: `onboardingDone, theme, storeRawText(false), retentionDays(30), smsMonitoring(false), notificationsEnabled, alertThreshold(60), lockScreenPrivacy(true), appLock(false)`.

Retention enforced by a WorkManager periodic job (daily) deleting rows older than `retentionDays`.

---

## 8. Build phases (one Claude Code prompt each)

| # | Phase | Deliverable | Verify |
|---|---|---|---|
| 1 | Shell | 3 modules, theme, 5-tab nav, onboarding, empty dashboard, Room+DataStore wired, README | `./gradlew assembleDebug` green, app runs, tabs switch |
| 2 | Engine | `engine` module: models, extractors, SMS + URL rules, RiskEngine, TemplateExplainer, ≥40 JUnit tests | `./gradlew :engine:test` green |
| 3 | Text analysis + history | Manual paste screen → Analyzer → ThreatDetails → Room save → Activity list | Paste a phishing sample, see score, see it in history, delete it |
| 4 | URL scanner | URL screen, share-intent entry, http intent-filter warning flow | Share a link from Chrome to Guardian |
| 5 | QR | CameraX + ML Kit, classify, reuse URL rules, gallery image | Scan a UPI QR and a URL QR on real phone |
| 6 | Caller + IP/domain | libphonenumber lookup, IP validator, UNAVAILABLE states | Enter +91 number, enter 10.0.0.1 |
| 7 | Settings + privacy | Retention worker, clear history, toggles, theme, biometric lock, About (scoring version, limits) | Toggle each, confirm effect |
| 8 | SMS receiver + notifications | `RECEIVE_SMS` runtime perm, receiver → Analyzer → notification (no content on lock screen) | Send yourself a test SMS on real phone |
| 9 | Polish + docs + tests | UI tests for 3 flows, docs/*.md, demo script | Full demo run-through |

Backend / threat intel / ML / LLM = **v0.2**, each behind the interfaces already defined (`ThreatIntelProvider`, `Detector`, `Explainer`).

---

## 9. Assumptions

- Kotlin 2.0+, AGP 8.5+, Compose BOM 2024.09+, JDK 17.
- Sideloaded debug APK for demo; Play policy compliance documented, not targeted.
- English-only rules in v0.1; Telugu/Hindi/transliterated patterns explicitly listed as a limitation.
- All sample scam messages used in tests are synthetic, written by us — no scraped datasets.
