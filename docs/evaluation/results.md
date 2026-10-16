# Guardian engine — evaluation results

Dataset: `dataset.csv` — 224 samples (120 scam, 104 legitimate). Scoring version 0.1-local.
A sample is *flagged* when its risk score ≥ threshold. Primary threshold: 40 (Suspicious and above).

## Overall

| Threshold | TP | FP | TN | FN | Precision | Recall | F1 | False-positive rate | Accuracy |
|---|---|---|---|---|---|---|---|---|---|
| ≥ 20 | 103 | 1 | 103 | 17 | 0.990 | 0.858 | 0.920 | 1.0% | 92.0% |
| ≥ 40 | 85 | 0 | 104 | 35 | 1.000 | 0.708 | 0.829 | 0.0% | 84.4% |
| ≥ 60 | 59 | 0 | 104 | 61 | 1.000 | 0.492 | 0.659 | 0.0% | 72.8% |

## By channel (threshold 40)

| Channel | Samples | Precision | Recall | F1 | False-positive rate |
|---|---|---|---|---|---|
| app | 65 | 1.000 | 0.545 | 0.706 | 0.0% |
| domain | 12 | 1.000 | 0.833 | 0.909 | 0.0% |
| qr | 14 | 1.000 | 0.833 | 0.909 | 0.0% |
| sms | 78 | 1.000 | 0.828 | 0.906 | 0.0% |
| text | 15 | 1.000 | 0.733 | 0.846 | 0.0% |
| url | 40 | 1.000 | 0.800 | 0.889 | 0.0% |

## Recall by scam type (threshold 40)

| Scam type | Samples | Detected | Recall |
|---|---|---|---|
| apk_download | 2 | 2 | 100.0% |
| apk_lure | 6 | 6 | 100.0% |
| brand_in_domain | 7 | 7 | 100.0% |
| courier_customs | 5 | 5 | 100.0% |
| credential_link | 5 | 5 | 100.0% |
| digital_arrest | 5 | 5 | 100.0% |
| electricity_bill | 5 | 4 | 80.0% |
| hidden_destination | 2 | 2 | 100.0% |
| hindi | 2 | 0 | 0.0% |
| hinglish | 7 | 3 | 42.9% |
| investment | 5 | 1 | 20.0% |
| ip_host | 1 | 1 | 100.0% |
| job_task | 7 | 1 | 14.3% |
| kyc_phishing | 8 | 8 | 100.0% |
| loan_app | 4 | 2 | 50.0% |
| lookalike_domain | 8 | 8 | 100.0% |
| otp_request | 6 | 5 | 83.3% |
| prize_lottery | 5 | 4 | 80.0% |
| punycode | 2 | 1 | 50.0% |
| qr_link | 1 | 1 | 100.0% |
| qr_payment_lure | 2 | 2 | 100.0% |
| qr_script | 1 | 1 | 100.0% |
| qr_sms | 1 | 0 | 0.0% |
| qr_text | 1 | 1 | 100.0% |
| refund_scam | 4 | 3 | 75.0% |
| remote_access | 4 | 3 | 75.0% |
| shortener_lure | 1 | 0 | 0.0% |
| subtle | 6 | 0 | 0.0% |
| suspicious_tld | 1 | 0 | 0.0% |
| telugu | 1 | 0 | 0.0% |
| upi_pin_receive | 5 | 4 | 80.0% |

## False-positive rate by kind of legitimate message (threshold 40)

| Legitimate kind | Samples | Wrongly flagged | Rate |
|---|---|---|---|
| appointment | 4 | 0 | 0.0% |
| bank_official_link | 6 | 0 | 0.0% |
| delivery | 6 | 0 | 0.0% |
| general | 11 | 0 | 0.0% |
| govt_official | 5 | 0 | 0.0% |
| international | 2 | 0 | 0.0% |
| kyc_reminder_official | 3 | 0 | 0.0% |
| local_dev | 3 | 0 | 0.0% |
| official | 11 | 0 | 0.0% |
| otp | 8 | 0 | 0.0% |
| personal_chat | 12 | 0 | 0.0% |
| promo_official | 6 | 0 | 0.0% |
| qr_contact | 1 | 0 | 0.0% |
| qr_link | 3 | 0 | 0.0% |
| qr_shop_upi | 2 | 0 | 0.0% |
| qr_text | 1 | 0 | 0.0% |
| qr_wifi | 1 | 0 | 0.0% |
| shortener | 1 | 0 | 0.0% |
| transaction_alert | 8 | 0 | 0.0% |
| utility_bill_official | 5 | 0 | 0.0% |
| work | 5 | 0 | 0.0% |

## Risk level distribution

| Level | Scam | Legitimate |
|---|---|---|
| LOW | 17 | 103 |
| GUARDED | 18 | 1 |
| MODERATE | 26 | 0 |
| HIGH | 48 | 0 |
| CRITICAL | 11 | 0 |

## Latency (JVM, after warm-up)

Mean 0.094 ms · median 0.093 ms · p95 0.187 ms per sample.

## False negatives — scams scored below 40 (35)

| id | type | score | indicators | text |
|---|---|---|---|---|
| s012 | otp_request | 0 | — | Amazon delivery executive here. Please share the 6 digit code you received so I can mark y |
| s018 | upi_pin_receive | 35 | COLLECT_REQUEST | Army officer here, buying your bike. I will pay through Google Pay, just approve the payme |
| s025 | job_task | 35 | EASY_MONEY_JOB | Part time job! Earn Rs 3000 daily by liking YouTube videos. Contact on telegram t.me/earnf |
| s026 | job_task | 35 | EASY_MONEY_JOB | Hello, we are hiring for work from home. Simple tasks, rate hotels on Google, earn Rs 500  |
| s027 | job_task | 35 | EASY_MONEY_JOB | Amazon is hiring part-time workers. Daily income Rs 8000. Join now: wa.me/919812345678 |
| s028 | job_task | 35 | EASY_MONEY_JOB | Dear candidate your profile is selected for online job. Earn 2k-5k daily. Prepaid tasks wi |
| s029 | job_task | 0 | — | Hi! I am HR from a digital marketing firm. Like and subscribe channels and earn Rs 150 per |
| s031 | job_task | 35 | EASY_MONEY_JOB | Congrats you are shortlisted. Complete 3 simple tasks today and earn Rs 2100 daily payout. |
| s034 | prize_lottery | 35 | PRIZE_CLAIM | Dear customer your mobile number has won 2 crore in the international lottery. Send your b |
| s046 | remote_access | 35 | SCREEN_SHARE_REQUEST | Please share your screen on the video call so I can guide you to cancel the unauthorised p |
| s047 | electricity_bill | 35 | UTILITY_DISCONNECTION | Dear consumer, your electricity power will be disconnected tonight because your previous m |
| s060 | refund_scam | 35 | ADVANCE_FEE, REFUND_LURE | Dear user your GST refund is pending approval. Pay verification fee of Rs 299 to process t |
| s061 | loan_app | 35 | ADVANCE_FEE | Instant loan approved Rs 50,000 without documents. Download our app and pay processing fee |
| s064 | loan_app | 35 | ID_DOCUMENT_REQUEST | Congratulations loan sanctioned! Send photo of your Aadhaar card and PAN card on WhatsApp  |
| s065 | investment | 35 | PAY_TO_UPI_ID | Join our VIP stock tips group. Guaranteed 300 percent returns in one week. Pay Rs 5000 to  |
| s067 | investment | 15 | SUSPICIOUS_TLD | Hello dear, I earned 40 lakh in a month with this trading app. I can teach you. Download f |
| s068 | investment | 15 | URGENT_LANGUAGE | Double your money in 30 days with our gold scheme. Transfer Rs 25000 to start. Hurry, offe |
| s069 | investment | 35 | PRIZE_CLAIM | SEBI registered advisor here. Pay Rs 2999 to unlock today's jackpot stock. Winner guarante |
| s076 | hinglish | 0 | — | Bhai ghar baithe paise kamao, roz 3000 rupaye, bas YouTube video like karna hai. Telegram  |
| s078 | hinglish | 0 | — | Main bijli vibhag se bol raha hoon, aaj raat aapki light kat jayegi, abhi bill update karo |
| s079 | hinglish | 0 | — | Sir aapke account mein galti se paise aa gaye hain, please wapas bhej do, OTP bata do conf |
| s080 | hindi | 0 | — | आपका बैंक खाता ब्लॉक कर दिया जाएगा। तुरंत केवाईसी अपडेट करें। |
| s081 | hindi | 0 | — | बधाई हो! आपने 10 लाख रुपये जीते हैं। इनाम पाने के लिए फीस भेजें। |
| s082 | telugu | 0 | — | మీ బ్యాంక్ ఖాతా ఈరోజు బ్లాక్ అవుతుంది. వెంటనే KYC అప్‌డేట్ చేయండి. |
| s084 | hinglish | 35 | REFUND_LURE | OTP aaya hoga sir, wo bata dijiye taaki aapka refund process ho sake. |
| s085 | subtle | 15 | URGENT_LANGUAGE | Hi Mom, this is my new number, my phone broke. Can you send Rs 8000 for an urgent bill? Wi |
| s086 | subtle | 15 | URL_SHORTENER | Is this you in this video? Everyone is sharing it: tinyurl.com/x7vid |
| s087 | subtle | 0 | — | Hello, sorry wrong number. Anyway nice to meet you, I work in crypto investment, can we be |
| s088 | subtle | 15 | SUSPICIOUS_TLD | Your friend has sent you a gift card. Open to accept: giftcard-receive.shop |
| u017 | shortener_lure | 39 | URL_SHORTENER, LURE_KEYWORDS_IN_URL, NO_HTTPS | http://bit.ly/sbi-kyc-update |
| u018 | suspicious_tld | 15 | SUSPICIOUS_TLD, LURE_KEYWORDS_IN_URL | https://free-recharge-offer.click/claim-reward |
| u019 | subtle | 15 | LURE_KEYWORDS_IN_URL | https://secure-payments-portal.com/verify |
| u020 | subtle | 15 | LURE_KEYWORDS_IN_URL | https://rewards-india.net/claim |
| q005 | qr_sms | 35 | QR_SENDS_SMS | SMSTO:56789:SUBSCRIBE PREMIUM |
| d005 | punycode | 35 | PUNYCODE_HOST | xn--pple-43d.com |

## False positives — legitimate items scored 40 or above (0)

None.

