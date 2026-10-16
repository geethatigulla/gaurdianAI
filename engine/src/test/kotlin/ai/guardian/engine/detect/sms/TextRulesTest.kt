package ai.guardian.engine.detect.sms

import ai.guardian.engine.TestEvents
import ai.guardian.engine.model.Severity
import ai.guardian.engine.types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UrgencyRuleTest {
    private val rule = UrgencyRule()

    @Test
    fun `flags urgent wording as weak`() {
        val hits = rule.evaluate(TestEvents.textContext("URGENT: act now, your offer expires today"))
        assertEquals(setOf("URGENT_LANGUAGE"), hits.types())
        assertTrue(hits.all { it.severity == Severity.WEAK })
    }

    @Test
    fun `flags short deadlines`() {
        assertEquals(setOf("SHORT_DEADLINE"), rule.evaluate(TestEvents.textContext("Pay within 2 hours to avoid charges")).types())
    }

    @Test
    fun `ignores calm messages`() {
        assertTrue(rule.evaluate(TestEvents.textContext("See you at lunch tomorrow")).isEmpty())
    }
}

class AccountThreatRuleTest {
    private val rule = AccountThreatRule()

    @Test
    fun `flags block threats and KYC pressure`() {
        val hits = rule.evaluate(TestEvents.textContext("Your SBI account will be blocked today as your KYC is pending"))
        assertTrue("ACCOUNT_BLOCK_THREAT" in hits.types())
        assertTrue("KYC_PRESSURE" in hits.types())
    }

    @Test
    fun `flags electricity disconnection scam`() {
        val hits = rule.evaluate(TestEvents.textContext("Dear consumer your electricity power will be disconnected tonight"))
        assertTrue("UTILITY_DISCONNECTION" in hits.types())
    }
}

class AuthorityThreatRuleTest {
    private val rule = AuthorityThreatRule()

    @Test
    fun `digital arrest is strong`() {
        val hit = rule.evaluate(TestEvents.textContext("You are under digital arrest")).single()
        assertEquals(Severity.STRONG, hit.severity)
    }

    @Test
    fun `legal threats are medium`() {
        val hits = rule.evaluate(TestEvents.textContext("An arrest warrant has been issued and legal action will follow"))
        assertEquals(setOf("LEGAL_THREAT"), hits.types())
        assertEquals(Severity.MEDIUM, hits.single().severity)
    }
}

class CredentialRequestRuleTest {
    private val rule = CredentialRequestRule()

    @Test
    fun `asking to share an OTP is strong`() {
        val hit = rule.evaluate(TestEvents.textContext("Kindly share the OTP you received to cancel the transaction")).single()
        assertEquals("SECRET_REQUEST", hit.type)
        assertEquals(Severity.STRONG, hit.severity)
    }

    @Test
    fun `OTP mentioned first then asked for is also strong`() {
        val hits = rule.evaluate(TestEvents.textContext("You will receive an OTP now, please share it with our executive"))
        assertEquals(setOf("SECRET_REQUEST"), hits.types())
    }

    @Test
    fun `a genuine do-not-share warning is not flagged`() {
        val hits = rule.evaluate(TestEvents.textContext("482913 is your OTP. Do not share this OTP with anyone. Bank never asks you to share OTP."))
        assertTrue(hits.isEmpty(), "unexpected $hits")
    }

    @Test
    fun `verify account requests are medium`() {
        val hits = rule.evaluate(TestEvents.textContext("Click below to verify your account details"))
        assertEquals(setOf("VERIFY_ACCOUNT_REQUEST"), hits.types())
    }
}

class ImpersonationRuleTest {
    private val rule = ImpersonationRule()

    @Test
    fun `bank name with a foreign link is flagged`() {
        val hit = rule.evaluate(TestEvents.textContext("HDFC Bank: update PAN at http://hdfc-pan-update.top/x")).single()
        assertEquals("BRAND_LINK_MISMATCH", hit.type)
        assertEquals("hdfc-pan-update.top", hit.matchedText)
    }

    @Test
    fun `bank name with its official link is not flagged`() {
        assertTrue(rule.evaluate(TestEvents.textContext("HDFC Bank: view statement at https://netbanking.hdfcbank.com/")).isEmpty())
    }

    @Test
    fun `bank message from a personal mobile number is flagged`() {
        val hits = rule.evaluate(TestEvents.textContext("SBI: your account is on hold, call us", sender = "+919812345678"))
        assertTrue("PERSONAL_NUMBER_SENDER" in hits.types())
    }

    @Test
    fun `bank message from an alphanumeric header is not flagged for sender`() {
        val hits = rule.evaluate(TestEvents.textContext("SBI: your account is on hold, call us", sender = "VM-SBIINB"))
        assertFalse("PERSONAL_NUMBER_SENDER" in hits.types())
    }

    @Test
    fun `authority mention is weak`() {
        val hit = rule.evaluate(TestEvents.textContext("This is the CBI calling about your case")).single()
        assertEquals(Severity.WEAK, hit.severity)
    }
}

class PaymentLureRuleTest {
    private val rule = PaymentLureRule()

    @Test
    fun `entering PIN to receive money is strong`() {
        val hit = rule.evaluate(TestEvents.textContext("Enter your UPI PIN to receive Rs 5000 cashback")).single()
        assertEquals("PIN_TO_RECEIVE", hit.type)
        assertEquals(Severity.STRONG, hit.severity)
    }

    @Test
    fun `scanning a QR to receive money is strong`() {
        assertEquals(setOf("PIN_TO_RECEIVE"), rule.evaluate(TestEvents.textContext("Scan this QR code to receive your refund")).types())
    }

    @Test
    fun `accidental transfer and advance fee are flagged`() {
        val hits = rule.evaluate(TestEvents.textContext("I sent Rs 2000 to you by mistake. Also pay the delivery fee."))
        assertTrue("ADVANCE_FEE" in hits.types())
        assertEquals(1, rule.evaluate(TestEvents.textContext("Money was mistakenly transferred to your account")).size)
    }

    @Test
    fun `pay request to a UPI ID in the message is flagged`() {
        val hit = rule.evaluate(TestEvents.textContext("Pay Rs 499 to claimdesk@ybl to activate")).single()
        assertEquals("PAY_TO_UPI_ID", hit.type)
        assertEquals("claimdesk@ybl", hit.matchedText)
    }

    @Test
    fun `a normal debit alert is not flagged`() {
        assertTrue(rule.evaluate(TestEvents.textContext("Rs.500 debited from A/c XX1234 to VPA shop@okaxis")).isEmpty())
    }
}

class LureRulesTest {
    @Test
    fun `reward lure`() {
        assertEquals(setOf("PRIZE_CLAIM"), RewardLureRule().evaluate(TestEvents.textContext("Congrats! You have won a KBC lottery")).types())
    }

    @Test
    fun `job lure`() {
        assertEquals(setOf("EASY_MONEY_JOB"), JobLureRule().evaluate(TestEvents.textContext("Part-time job, earn Rs 3000 per day")).types())
    }

    @Test
    fun `apk lure is strong`() {
        val hits = ApkLureRule().evaluate(TestEvents.textContext("Download the app from the link below: rto-challan.apk"))
        assertTrue("APK_FILE" in hits.types())
        assertTrue("INSTALL_FROM_LINK" in hits.types())
    }

    @Test
    fun `remote access app is strong`() {
        val hit = RemoteAccessRule().evaluate(TestEvents.textContext("Please install AnyDesk so I can help")).single()
        assertEquals(Severity.STRONG, hit.severity)
    }
}

class TextNormalizerTest {
    @Test
    fun `strips zero-width characters, lowercases and collapses spaces`() {
        assertEquals("share otp now", TextNormalizer.normalize("SH​ARE   OTP\nnow"))
    }
}
