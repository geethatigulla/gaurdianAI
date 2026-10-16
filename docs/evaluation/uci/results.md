# Guardian engine — evaluation results

Dataset: `uci_sms.csv` — 5574 samples (747 scam, 4827 legitimate). Scoring version 0.1-local.
A sample is *flagged* when its risk score ≥ threshold. Primary threshold: 40 (Suspicious and above).

## Overall

| Threshold | TP | FP | TN | FN | Precision | Recall | F1 | False-positive rate | Accuracy |
|---|---|---|---|---|---|---|---|---|---|
| ≥ 20 | 51 | 13 | 4814 | 696 | 0.797 | 0.068 | 0.126 | 0.3% | 87.3% |
| ≥ 40 | 13 | 1 | 4826 | 734 | 0.929 | 0.017 | 0.034 | 0.0% | 86.8% |
| ≥ 60 | 0 | 1 | 4826 | 747 | 0.000 | 0.000 | 0.000 | 0.0% | 86.6% |

## By channel (threshold 40)

| Channel | Samples | Precision | Recall | F1 | False-positive rate |
|---|---|---|---|---|---|
| sms | 5574 | 0.929 | 0.017 | 0.034 | 0.0% |

## Recall by scam type (threshold 40)

| Scam type | Samples | Detected | Recall |
|---|---|---|---|
| uci_spam | 747 | 13 | 1.7% |

## False-positive rate by kind of legitimate message (threshold 40)

| Legitimate kind | Samples | Wrongly flagged | Rate |
|---|---|---|---|
| uci_ham | 4827 | 1 | 0.0% |

## Risk level distribution

| Level | Scam | Legitimate |
|---|---|---|
| LOW | 696 | 4814 |
| GUARDED | 38 | 12 |
| MODERATE | 13 | 0 |
| HIGH | 0 | 1 |
| CRITICAL | 0 | 0 |

## Latency (JVM, after warm-up)

Mean 0.076 ms · median 0.057 ms · p95 0.163 ms per sample.

## False negatives — scams scored below 40 (734)

Showing the first 40; see per-sample.csv for all.

| id | type | score | indicators | text |
|---|---|---|---|---|
| uci0003 | uci_spam | 0 | — | Free entry in 2 a wkly comp to win FA Cup final tkts 21st May 2005. Text FA to 87121 to re |
| uci0006 | uci_spam | 0 | — | FreeMsg Hey there darling it's been 3 week's now and no word back! I'd like some fun you u |
| uci0009 | uci_spam | 0 | — | WINNER!! As a valued network customer you have been selected to receivea £900 prize reward |
| uci0010 | uci_spam | 0 | — | Had your mobile 11 months or more? U R entitled to Update to the latest colour mobiles wit |
| uci0012 | uci_spam | 0 | — | SIX chances to win CASH! From 100 to 20,000 pounds txt> CSH11 and send to 87575. Cost 150p |
| uci0016 | uci_spam | 15 | NO_HTTPS | XXXMobileMovieClub: To use your credit, click the WAP link in the next txt message or clic |
| uci0020 | uci_spam | 0 | — | England v Macedonia - dont miss the goals/team news. Txt ur national team to 87077 eg ENGL |
| uci0035 | uci_spam | 0 | — | Thanks for your subscription to Ringtone UK your mobile will be charged £5/month Please co |
| uci0043 | uci_spam | 0 | — | 07732584351 - Rodger Burns - MSG = We tried to call you re your reply to our sms for a fre |
| uci0055 | uci_spam | 0 | — | SMS. ac Sptv: The New Jersey Devils and the Detroit Red Wings play Ice Hockey. Correct or  |
| uci0057 | uci_spam | 0 | — | Congrats! 1 year special cinema pass for 2 is yours. call 09061209465 now! C Suprman V, Ma |
| uci0066 | uci_spam | 0 | — | As a valued customer, I am pleased to advise you that following recent review of your Mob  |
| uci0068 | uci_spam | 15 | URGENT_LANGUAGE | Urgent UR awarded a complimentary trip to EuroDisinc Trav, Aco&Entry41 Or £1000. To claim  |
| uci0069 | uci_spam | 0 | — | Did you hear about the new "Divorce Barbie"? It comes with all of Ken's stuff! |
| uci0094 | uci_spam | 35 | PRIZE_CLAIM | Please call our customer service representative on 0800 169 6031 between 10am-9pm as you h |
| uci0096 | uci_spam | 0 | — | Your free ringtone is waiting to be collected. Simply text the password "MIX" to 85069 to  |
| uci0115 | uci_spam | 35 | PRIZE_CLAIM | GENT! We are trying to contact you. Last weekends draw shows that you won a £1000 prize GU |
| uci0118 | uci_spam | 0 | — | You are a winner U have been specially selected 2 receive £1000 or a 4* holiday (flights i |
| uci0121 | uci_spam | 0 | — | PRIVATE! Your 2004 Account Statement for 07742676969 shows 786 unredeemed Bonus Points. To |
| uci0122 | uci_spam | 15 | URGENT_LANGUAGE | URGENT! Your Mobile No. was awarded £2000 Bonus Caller Prize on 5/9/03 This is our final t |
| uci0124 | uci_spam | 0 | — | Todays Voda numbers ending 7548 are selected to receive a $350 award. If you have a match  |
| uci0135 | uci_spam | 0 | — | Sunshine Quiz Wkly Q! Win a top Sony DVD player if u know which country the Algarve is in? |
| uci0136 | uci_spam | 0 | — | Want 2 get laid tonight? Want real Dogging locations sent direct 2 ur mob? Join the UK's l |
| uci0140 | uci_spam | 0 | — | You'll not rcv any more msgs from the chat svc. For FREE Hardcore services text GO to: 699 |
| uci0148 | uci_spam | 0 | — | FreeMsg Why haven't you replied to my text? I'm Randy, sexy, female and live local. Luv to |
| uci0160 | uci_spam | 0 | — | Customer service annoncement. You have a New Years delivery waiting for you. Please call 0 |
| uci0161 | uci_spam | 0 | — | You are a winner U have been specially selected 2 receive £1000 cash or a 4* holiday (flig |
| uci0165 | uci_spam | 0 | — | -PLS STOP bootydelious (32/F) is inviting you to be her friend. Reply YES-434 or NO-434 Se |
| uci0166 | uci_spam | 0 | — | BangBabes Ur order is on the way. U SHOULD receive a Service Msg 2 download UR content. If |
| uci0189 | uci_spam | 35 | PRIZE_CLAIM | Please call our customer service representative on FREEPHONE 0808 145 4742 between 9am-11p |
| uci0192 | uci_spam | 0 | — | Are you unique enough? Find out from 30th August. www.areyouunique.co.uk |
| uci0226 | uci_spam | 0 | — | 500 New Mobiles from 2004, MUST GO! Txt: NOKIA to No: 89545 & collect yours today!From ONL |
| uci0228 | uci_spam | 0 | — | Will u meet ur dream partner soon? Is ur career off 2 a flyng start? 2 find out free, txt  |
| uci0236 | uci_spam | 0 | — | Text & meet someone sexy today. U can find a date or even flirt its up to U. Join 4 just 1 |
| uci0241 | uci_spam | 0 | — | U 447801259231 have a secret admirer who is looking 2 make contact with U-find out who the |
| uci0251 | uci_spam | 0 | — | Congratulations ur awarded 500 of CD vouchers or 125gift guaranteed & Free entry 2 100 wkl |
| uci0260 | uci_spam | 0 | — | We tried to contact you re your reply to our offer of a Video Handset? 750 anytime network |
| uci0265 | uci_spam | 0 | — | Hey I am really horny want to chat or see me naked text hot to 69698 text charged at 150pm |
| uci0269 | uci_spam | 0 | — | Ur ringtone service has changed! 25 Free credits! Go to club4mobiles.com to choose content |
| uci0271 | uci_spam | 0 | — | Ringtone Club: Get the UK singles chart on your mobile each week and choose any top qualit |

## False positives — legitimate items scored 40 or above (1)

| id | type | score | indicators | text |
|---|---|---|---|---|
| uci2778 | uci_ham | 60 | SECRET_REQUEST | Send me your id and password |

