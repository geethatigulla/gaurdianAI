# Evaluation

```bash
./gradlew :engine:evaluate      # synthetic Indian dataset → results.md, per-sample.csv
python engine/eval/prepare_uci.py   # after downloading the UCI corpus (see the script)
./gradlew :engine:evaluateUci   # public UCI SMS Spam Collection → uci/results.md
```

## Headline results (threshold 40, "Suspicious" and above)

| Dataset | Samples | Precision | Recall | F1 | False-positive rate |
|---|---|---|---|---|---|
| Synthetic Indian scams (this project) | 224 (120 scam / 104 legit) | 1.000 | 0.708 | 0.829 | 0.0% (0/104) |
| UCI SMS Spam Collection (public) | 5,574 (747 spam / 4,827 ham) | 0.929 | 0.017 | 0.034 | 0.02% (1/4,827) |

**Interpretation.** On thousands of real personal messages Guardian almost never raises a false
alarm (1 of 4,827; the one case is a friend writing "Send me your id and password"). It detects
targeted Indian scam patterns well, 100% on link-based scams, but it is not a general spam filter:
the UCI spam is mostly 2011 UK marketing ("you have won a £1000 prize, call 09…"), which the rules
intentionally don't treat as dangerous, and Hindi/Telugu/Hinglish scams are missed because the rules
are English-only.

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

## UCI SMS Spam Collection

5,574 real English SMS (4,827 ham, 747 spam) collected in 2011–2012, mostly from UK sources.
Copyright T. A. Almeida and J. M. Gómez Hidalgo; free for research with citation, so it is **not
committed** here. Cite: T. A. Almeida, J. M. G. Hidalgo and A. Yamakami, "Contributions to the Study of
SMS Spam Filtering: New Collection and Results," Proc. ACM DocEng, 2011. UCI "spam" is labelled as
`scam` in our CSV only so the metric reads as "flagged"; spam is not the same as scam, so its recall
is a domain-mismatch figure, while the ham false-positive rate is directly meaningful.

## Caveats (state these in the paper)

1. **Same authors wrote the rules and the synthetic dataset**, so its results are likely optimistic.
   The UCI ham false-positive rate (0.02%) is the independent check on false alarms; an independent
   Indian *scam* dataset is still needed to validate recall.
2. **The rules were not tuned on this dataset after it was written.** Improving the rules must be
   checked against a *new, held-out* set, otherwise the numbers become meaningless.
3. The dataset is small (224 samples); per-type figures with 1–5 samples are illustrative only.
4. Thresholds are UI bands, not calibrated probabilities (see `docs/risk-scoring.md`).
5. Phone-number checks are not evaluated: without a reputation source, a number's format says
   little about whether its owner is a scammer.
