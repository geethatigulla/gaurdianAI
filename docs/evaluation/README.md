# Evaluation

```bash
./gradlew :engine:evaluate     # writes results.md and per-sample.csv in this folder
```

## Dataset (`engine/eval/dataset.csv`)

224 labelled samples: 120 scams and 104 legitimate items across six channels: SMS, messaging-app
messages, pasted text, URLs, QR payloads and domains. Columns: `id, channel, label, scam_type, text`.

- **All samples are synthetic**, written for this project and modelled on publicly reported Indian
  scam patterns (KYC, OTP, UPI-PIN-to-receive, digital arrest, job/task, lottery, APK, remote access,
  electricity, courier/customs, refund, loan, investment). No real messages, people or phone numbers.
- **Hard legitimate cases** are included on purpose: real-style OTPs, bank transaction alerts, genuine
  KYC reminders, official bank/government links, delivery updates, local development URLs.
- **Out-of-scope scams** are included on purpose to measure known limits: Hindi, Telugu and
  Hinglish messages, and subtle social engineering with no tell-tale wording.

## Method

Each sample becomes a `SecurityEvent` of the matching type and runs through the full `Analyzer`
pipeline (default detectors, no user trust lists). A sample counts as **flagged** when its risk score
is at or above a threshold. 40 (Suspicious and above) is the primary threshold; 20 and 60 are also
reported. Latency is measured on the JVM after warm-up (mean of 20 runs per sample).

## Caveats (state these in the paper)

1. **Same authors wrote the rules and the dataset**, so results are likely optimistic, especially the
   0% false-positive rate. An independent or public dataset is needed for external validity.
2. **The rules were not tuned on this dataset after it was written.** Improving the rules must be
   checked against a *new, held-out* set, otherwise the numbers become meaningless.
3. The dataset is small (224 samples); per-type figures with 1–5 samples are illustrative only.
4. Thresholds are UI bands, not calibrated probabilities (see `docs/risk-scoring.md`).
5. Phone-number checks are not evaluated: without a reputation source, a number's format says
   little about whether its owner is a scammer.
