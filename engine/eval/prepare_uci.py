"""Converts the UCI SMS Spam Collection into the evaluation CSV format.

The corpus is copyright its authors and is NOT committed to this repository. Download it, then run:

    curl -L -o engine/eval/external/sms_spam_collection.zip \
        "https://archive.ics.uci.edu/static/public/228/sms+spam+collection.zip"
    python engine/eval/prepare_uci.py

Cite: T. A. Almeida, J. M. G. Hidalgo and A. Yamakami, "Contributions to the Study of SMS Spam
Filtering: New Collection and Results," Proc. ACM Symposium on Document Engineering (DocEng), 2011.
"""
import csv
import io
import os
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
EXTERNAL = os.path.join(HERE, "external")
ZIP_PATH = os.path.join(EXTERNAL, "sms_spam_collection.zip")
OUT_PATH = os.path.join(EXTERNAL, "uci_sms.csv")

with zipfile.ZipFile(ZIP_PATH) as z:
    raw = z.read("SMSSpamCollection").decode("utf-8", errors="replace")

rows = []
for n, line in enumerate(io.StringIO(raw), start=1):
    line = line.rstrip("\r\n")
    if not line:
        continue
    label, _, text = line.partition("\t")
    # UCI "spam" is mostly marketing spam, not necessarily a scam; labelled scam here so the
    # metric reads as "flagged", and reported separately from the Indian scam dataset.
    rows.append([f"uci{n:04d}", "sms", "scam" if label == "spam" else "legit", f"uci_{label}", text])

with open(OUT_PATH, "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(["id", "channel", "label", "scam_type", "text"])
    w.writerows(rows)

print(f"Wrote {len(rows)} rows to {OUT_PATH}")
