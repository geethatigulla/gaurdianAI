# Risk scoring — `0.1-local`

Source: `engine/src/main/kotlin/ai/guardian/engine/risk/`. Tests: `RiskEngineTest`.

## Pipeline

```
SecurityEvent ─► extract (URLs, phones, UPI IDs, e-mails)
              ─► detect  (TextRulesDetector, UrlRulesDetector)  → Indicators + Checks
              ─► intel   (NoopIntelProvider)                     → Checks = UNAVAILABLE
              ─► risk    (RiskEngine)                            → score, level, category, evidence quality
              ─► explain (TemplateExplainer)                     → summary, recommendation
              ─► RiskResult
```

## Method

1. **Families.** Every indicator has a `family` (see `model/Families.kt`). Only the strongest
   indicator per family counts. Correlated signals ("urgent" + "immediately" + "today") are one hit.
2. **Weights.** `WEAK 0.15`, `MEDIUM 0.35`, `STRONG 0.60`.
3. **Noisy-OR.** `p = 1 − Π(1 − w_f)` over families; `score = round(p × 100)`.

   | Families present | Score | Level |
   |---|---|---|
   | one WEAK | 15 | LOW |
   | one MEDIUM | 35 | GUARDED |
   | one STRONG | 60 | HIGH |
   | STRONG + MEDIUM | 74 | HIGH |
   | STRONG + MEDIUM + MEDIUM | 83 | CRITICAL |

4. **Weak-only cap.** If every indicator is WEAK the score is capped at **39** (never above GUARDED).
5. **Bands.** 0–19 LOW · 20–39 GUARDED · 40–59 MODERATE · 60–79 HIGH · 80–100 CRITICAL.
6. **Zero indicators** → score 0, LOW, category `NO_KNOWN_INDICATORS`, summary states this is *not proof of safety*.
7. **Evidence quality** is independent of the score. Over all checks except `SKIPPED` (not applicable):
   all `COMPLETED` → HIGH; ≥ 50 % → MODERATE; otherwise LOW. In v0.1 reputation checks are
   `UNAVAILABLE`, so most results are MODERATE and the UI says "External reputation not checked".
8. **Category** comes from the most serious family present (MEDIUM+ families first), in this priority:
   remote access → APK/malware → authority threat → payment lure → credential request /
   URL impersonation / impersonation / account threat (phishing) → job → reward → link/QR
   structure → number → network address.
9. **Versioning.** `ScoringConfig.version` is stored on every history row. Any change to weights, caps
   or bands must bump it.

## Families

| Family | Examples | Typical severity |
|---|---|---|
| `URGENCY` | "urgent", "within 2 hours" | WEAK |
| `ACCOUNT_THREAT` | "account will be blocked", KYC pressure, electricity disconnection | MEDIUM |
| `AUTHORITY_THREAT` | "digital arrest" (STRONG), arrest warrant / legal action | MEDIUM–STRONG |
| `CREDENTIAL_REQUEST` | share OTP/PIN/CVV (STRONG, negation-aware), verify account | MEDIUM–STRONG |
| `IMPERSONATION` | brand + non-official link, bank SMS from personal number, authority mention (WEAK) | WEAK–MEDIUM |
| `PAYMENT_LURE` | PIN/QR to *receive* money (STRONG), collect request, advance fee | MEDIUM–STRONG |
| `REWARD_LURE`, `JOB_LURE` | lottery / KBC, "earn ₹3000 daily" | MEDIUM |
| `APK_LURE` | `.apk` link or file | STRONG |
| `REMOTE_ACCESS_LURE` | AnyDesk / TeamViewer | STRONG |
| `URL_OBFUSCATION` | IP host, `@` in URL, shortener, encoded IP | WEAK–STRONG |
| `URL_IMPERSONATION` | look-alike domain (edit distance + homoglyphs), brand in unrelated domain, punycode | MEDIUM–STRONG |
| `URL_STRUCTURE` | suspicious TLD, long URL, many subdomains, odd port, lure keywords | WEAK |
| `URL_TRANSPORT` | plain `http://` | WEAK |

## Calibration status

**Not calibrated.** The weights and bands are hand-chosen UI categories, validated only against the
synthetic fixtures in `AnalyzerTest`. They are not probabilities of fraud. Calibrating them needs a
labelled, consented dataset, which this project does not have.
