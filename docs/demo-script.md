# Demo script (about 8 minutes)

For a live conference or viva demo on one Android phone. Every sample below is synthetic.

## Before the demo

- Phone on the same Wi-Fi as the laptop if you'll mirror the screen (e.g. scrcpy over Wireless debugging).
- Guardian installed; onboarding done; Settings → Protection: Floating button, SMS, WhatsApp & other
  apps, Websites, Calls all **on**; notifications allowed.
- A second phone (a teammate's) to send the SMS/WhatsApp samples.
- Do-not-disturb **off**, so alerts show.

## 1. The problem (30 s)

"Digital-payment scams in India arrive by SMS, WhatsApp, links, QR codes and calls. Existing apps
mostly check one channel, need the cloud, or give a score with no explanation. Guardian checks all of
them on the phone, explains why, and never uploads your messages."

## 2. Home (30 s)

Show the shield ("Fully protected", 5 of 5 on) and the five check tiles.

## 3. Manual check: message (1 min)

Message → **Try example → KYC phishing** → Check.
Point out: **Scam alert** verdict · short reasons (fake bank link, KYC pressure, threatens to block
account) · *What to do* · tap a reason to show the full explanation and the exact matched text.
Then **Try example → Genuine OTP** → "No warning signs — not a guarantee it's safe". Stress honesty.

## 4. Automatic SMS + WhatsApp (1.5 min)

Teammate sends by SMS:
`Dear customer, your SBI account will be blocked today. Update your KYC immediately: http://sbi-kyc-update.xyz/login`
→ "Suspicious SMS" alert appears. Tap it → result.

Teammate sends on WhatsApp:
`Part time job! Earn Rs 3000 daily by liking YouTube videos. Contact on telegram t.me/earnfast`
→ "Suspicious WhatsApp message" alert.

## 5. Floating button (1 min)

Open any chat or a web page showing a QR code → tap the floating shield → result card slides up over
the app. Long-press the shield to show the menu.

## 6. QR code (1 min)

Scan → QR code. Show a QR encoding
`upi://pay?pa=claimdesk@ybl&pn=Cashback%20Refund&am=4999`
→ "Scanning this QR makes you PAY ₹4999 … labelled *refund*, but it takes money from you."

## 7. Website warning (45 s)

In Chrome open `http://203.0.113.10/kyc-update.apk` (a reserved test address that never loads a real
site) → red **Risky website** warning → **Go back**.
Then open `http://192.168.1.1` → no warning (local addresses are never flagged).

## 8. Number check + calls (45 s)

Number → `+92 300 1234567` → "Foreign number" (Pakistan). Report a number from a result, then show
Settings → *Block reported numbers*.

## 9. Privacy (30 s)

Settings: no internet permission, message text not saved by default, history auto-deletes, app lock.

## Likely questions

- **Can it block a scam message?** No app can; Android only lets the default SMS app hold messages. Guardian warns within about a second.
- **Why rules, not AI?** Rules are explainable, offline and private. LLM/ML is planned as a second opinion, mainly for Hindi/Telugu messages.
- **How accurate is it?** See the evaluation section of the paper (precision/recall on a labelled test set).
- **False alarms?** Local/dev addresses are skipped; users can trust their own sites; weak signs alone can never exceed "Be careful".
