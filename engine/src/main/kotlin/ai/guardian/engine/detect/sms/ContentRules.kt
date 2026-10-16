package ai.guardian.engine.detect.sms

import ai.guardian.engine.model.Family
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.Severity.MEDIUM
import ai.guardian.engine.model.Severity.STRONG
import ai.guardian.engine.model.Severity.WEAK

/** Pressure to act fast. Weak on its own: plenty of genuine messages have deadlines. */
class UrgencyRule : PatternTextRule("UrgencyRule", Family.URGENCY) {
    override val patterns = listOf(
        PatternSpec(
            "URGENT_LANGUAGE", WEAK, "Uses urgent language to rush you",
            Regex("\\b(urgent(ly)?|immediately|asap|right away|act now|hurry( up)?|final (notice|reminder|warning)|last (chance|warning)|limited time|expires? (today|soon|tonight)|without delay)\\b"),
        ),
        PatternSpec(
            "SHORT_DEADLINE", WEAK, "Sets a very short deadline",
            Regex("\\b(within|in (the )?next) \\d{1,2} ?(minutes?|mins?|hours?|hrs?)\\b|\\bbefore (midnight|tonight)\\b|\\btonight at \\d{1,2}(:\\d{2})?"),
        ),
    )
}

/** Threatens to block, suspend or disconnect something you rely on. */
class AccountThreatRule : PatternTextRule("AccountThreatRule", Family.ACCOUNT_THREAT) {
    override val patterns = listOf(
        PatternSpec(
            "ACCOUNT_BLOCK_THREAT", MEDIUM, "Threatens that your account, card or service will be blocked",
            Regex("\\b(account|a/c|card|kyc|sim|mobile number|wallet|upi( id)?|pan|aadhaa?r|connection|service)\\b${gap(40)}\\b(will be|has been|is being|shall be|getting) (blocked|suspended|deactivated|disabled|closed|frozen|terminated|disconnected|cancell?ed|restricted|locked|barred)\\b"),
        ),
        PatternSpec(
            "KYC_PRESSURE", MEDIUM, "Pressures you to update KYC — a very common bank-impersonation trick",
            Regex("\\b(kyc|e-?kyc|pan)\\b${gap(30)}\\b(pending|expired?|expiring|incomplete|not updated|update (now|immediately|required)|suspended)\\b|\\b(update|complete|re-?verify)\\b${gap(15)}\\b(e-?kyc|kyc)\\b"),
        ),
        PatternSpec(
            "UTILITY_DISCONNECTION", MEDIUM, "Threatens electricity or utility disconnection — a common bill-payment scam",
            Regex("\\b(electricity|power|light|bijli|gas)\\b${gap(60)}\\b(disconnect(ed|ion)?|cut off|will be cut)\\b"),
        ),
    )
}

/** Fake police / CBI / customs pressure. "Digital arrest" is not a real legal procedure. */
class AuthorityThreatRule : PatternTextRule("AuthorityThreatRule", Family.AUTHORITY_THREAT) {
    override val patterns = listOf(
        PatternSpec(
            "DIGITAL_ARREST", STRONG, "Mentions \"digital arrest\" — no Indian agency arrests people over video or phone calls",
            Regex("\\bdigital(ly)? arrest(ed)?\\b"),
        ),
        PatternSpec(
            "LEGAL_THREAT", MEDIUM, "Threatens arrest, legal action or a police case",
            Regex("\\b(arrest warrant|warrant (has been|is) issued|legal action|fir (has been|is) (filed|registered)|police case|court (notice|summons|case)|case (has been )?(registered|filed) against|money laundering|illegal (items|drugs)|drugs? (found|were found)|parcel (has been )?seized)\\b"),
        ),
    )
}

/** Asking for secrets. Genuine organisations never ask for OTP/PIN/CVV. */
class CredentialRequestRule : PatternTextRule("CredentialRequestRule", Family.CREDENTIAL_REQUEST, negationAware = true) {
    private val secret = "(otp|one[- ]time password|verification code|upi pin|m-?pin|atm pin|pin|cvv|cvc|password|passcode|net ?banking (password|credentials|details)|login (credentials|details)|card (number|details)|debit card details|credit card details)"

    override val patterns = listOf(
        PatternSpec(
            "SECRET_REQUEST", STRONG, "Asks you to share an OTP, PIN, CVV or password — genuine banks never do this",
            Regex("\\b(share|send|tell|give|provide|forward|reply( with)?|confirm|submit|read out|disclose)\\b${gap(30)}\\b$secret\\b"),
        ),
        PatternSpec(
            "SECRET_REQUEST", STRONG, "Asks you to share an OTP, PIN, CVV or password — genuine banks never do this",
            Regex("\\b$secret\\b${gap(40)}\\b(share|send|tell|give|forward|provide)( it| the same| them)? (with|to) (us|our|me|the (agent|executive|officer|caller))\\b"),
        ),
        PatternSpec(
            "VERIFY_ACCOUNT_REQUEST", MEDIUM, "Asks you to verify or update account details",
            Regex("\\b(verify|validate|confirm|update|re-?activate|unblock|unlock)\\b${gap(25)}\\b(your )?(account|a/c|bank details|kyc|identity|card details|credentials|pan|aadhaa?r)\\b"),
        ),
        PatternSpec(
            "ID_DOCUMENT_REQUEST", MEDIUM, "Asks for identity documents or bank statements",
            Regex("\\b(send|share|provide|upload|whatsapp)\\b${gap(30)}\\b(aadhaa?r( card)?|pan card|passport|bank statement|photo of (your )?card|selfie with)\\b"),
        ),
    )
}

class PaymentLureRule : PatternTextRule("PaymentLureRule", Family.PAYMENT_LURE, negationAware = true) {
    override val patterns = listOf(
        PatternSpec(
            "PIN_TO_RECEIVE", STRONG, "Asks you to enter a UPI PIN or scan a QR code to receive money — you never need to do that to receive money",
            Regex("\\b(enter|use|type|put)\\b${gap(25)}\\b(upi )?pin\\b${gap(30)}\\b(receive|get|credit|claim|accept)|\\bscan\\b${gap(25)}\\b(qr|code)\\b${gap(40)}\\b(receive|get|claim|credit|refund|cashback)\\b"),
        ),
        PatternSpec(
            "COLLECT_REQUEST", MEDIUM, "Asks you to approve a payment or \"collect\" request",
            Regex("\\b(collect|payment) request\\b|\\bapprove\\b${gap(30)}\\b(request|payment|transaction)\\b"),
        ),
        PatternSpec(
            "ACCIDENTAL_TRANSFER", MEDIUM, "Claims money was sent to you by mistake and asks for it back",
            Regex("\\b(wrongly|mistakenly|by mistake|accidentally)\\b${gap(30)}\\b(sent|transferred|credited|paid)\\b"),
        ),
        PatternSpec(
            "ADVANCE_FEE", MEDIUM, "Asks for a small fee to release money, a parcel or a prize",
            Regex("\\b(processing|registration|delivery|customs|clearance|release|redelivery|verification|gst) (fee|charge|charges|amount)\\b"),
        ),
        PatternSpec(
            "REFUND_LURE", MEDIUM, "Offers a refund that requires you to click, pay or share details",
            Regex("\\brefund\\b${gap(40)}\\b(click|link|claim|initiate|approve|pending|process)\\b"),
        ),
    )

    // Paying to a UPI ID written in the message is only flagged when the message also asks to pay.
    private val payVerb = Regex("\\b(pay|transfer|send)\\b${gap(30)}(rs\\.?|inr|₹|rupees)")

    override fun evaluate(context: TextContext) = super.evaluate(context) + buildList {
        val upi = context.extracted.upiIds.firstOrNull()
        if (upi != null && payVerb.containsMatchIn(context.normalized)) {
            add(
                Indicator(
                    type = "PAY_TO_UPI_ID", family = Family.PAYMENT_LURE, severity = MEDIUM,
                    description = "Asks you to pay to a UPI ID given in the message",
                    source = "rule:$id", matchedText = upi,
                ),
            )
        }
    }
}

class RewardLureRule : PatternTextRule("RewardLureRule", Family.REWARD_LURE) {
    override val patterns = listOf(
        PatternSpec(
            "PRIZE_CLAIM", MEDIUM, "Claims you won a prize, lottery or reward",
            Regex("\\b(you('ve| have)? won|you are (the )?(lucky )?winner|lottery|lucky draw|jackpot|kbc|kaun banega|prize money|claim (your )?(reward|prize|gift|cashback|bonus)|free (gift|recharge|iphone|laptop)|won (rs\\.?|inr|₹))"),
        ),
        PatternSpec(
            "REWARD_POINTS_EXPIRY", MEDIUM, "Says reward points are expiring and must be redeemed via a link",
            Regex("\\b(reward|loyalty) points?\\b${gap(40)}\\b(expir(e|ing|y)|redeem)\\b"),
        ),
    )
}

class JobLureRule : PatternTextRule("JobLureRule", Family.JOB_LURE) {
    override val patterns = listOf(
        PatternSpec(
            "EASY_MONEY_JOB", MEDIUM, "Offers easy money for simple online tasks — a common task/job scam",
            Regex("\\b(work from home|part[- ]time (job|work)|daily (income|earning|payout|salary)|lik(e|ing) (youtube )?videos|rat(e|ing) (hotels|products|movies)|simple (online )?tasks?|prepaid tasks?|telegram task|earn (rs\\.?|inr|₹)? ?\\d[\\d,]*k? ?(per|a|/|daily|every) ?(day|daily|hour|task))\\b"),
        ),
    )
}

class ApkLureRule : PatternTextRule("ApkLureRule", Family.APK_LURE) {
    override val patterns = listOf(
        PatternSpec(
            "APK_FILE", STRONG, "Asks you to install an app file (.apk) from outside the Play Store — this can give criminals control of your phone",
            Regex("\\.(apk|xapk|apks)\\b"),
        ),
        PatternSpec(
            "INSTALL_FROM_LINK", MEDIUM, "Asks you to download or install an app from a link",
            Regex("\\b(download|install)\\b${gap(30)}\\b(app|application)\\b${gap(40)}\\b(link|below|given|attached|this)\\b"),
        ),
    )
}

class RemoteAccessRule : PatternTextRule("RemoteAccessRule", Family.REMOTE_ACCESS_LURE) {
    override val patterns = listOf(
        PatternSpec(
            "REMOTE_ACCESS_APP", STRONG, "Mentions a remote-access app — scammers use these to take over your phone and bank apps",
            Regex("\\b(anydesk|any desk|teamviewer|team viewer|quick ?support|rustdesk|airdroid|ultraviewer|alpemix)\\b"),
        ),
        PatternSpec(
            "SCREEN_SHARE_REQUEST", MEDIUM, "Asks you to share your screen",
            Regex("\\b(share|sharing)\\b${gap(15)}\\bscreen\\b|\\bscreen ?shar(e|ing)\\b"),
        ),
    )
}
