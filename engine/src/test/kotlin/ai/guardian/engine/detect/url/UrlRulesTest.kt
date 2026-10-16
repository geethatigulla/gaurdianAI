package ai.guardian.engine.detect.url

import ai.guardian.engine.TestEvents.parsed
import ai.guardian.engine.model.Severity
import ai.guardian.engine.types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UrlRulesTest {

    @Test
    fun `IP host is medium, encoded IP is strong`() {
        assertEquals(Severity.MEDIUM, IpHostRule().evaluate(parsed("http://192.168.10.5/pay")).single().severity)
        assertEquals(Severity.STRONG, IpHostRule().evaluate(parsed("http://3232235777/")).single().severity)
        assertTrue(IpHostRule().evaluate(parsed("https://example.com")).isEmpty())
    }

    @Test
    fun `at-sign trick is strong and names the real host`() {
        val hit = UserInfoRule().evaluate(parsed("https://sbi.co.in@evil-site.xyz/login")).single()
        assertEquals(Severity.STRONG, hit.severity)
        assertTrue(hit.description.contains("evil-site.xyz"))
    }

    @Test
    fun `shorteners are weak`() {
        assertEquals(Severity.WEAK, ShortenerRule().evaluate(parsed("https://bit.ly/3xYz")).single().severity)
        assertTrue(ShortenerRule().evaluate(parsed("https://amzn.to/abc")).isEmpty())
    }

    @Test
    fun `punycode and mixed scripts`() {
        assertEquals(setOf("PUNYCODE_HOST"), PunycodeRule().evaluate(parsed("https://xn--amazn-mye.com")).types())
        // Cyrillic "а" (U+0430) mixed with Latin letters.
        assertEquals(setOf("MIXED_SCRIPT_HOST"), PunycodeRule().evaluate(parsed("http://аmazon.com")).types())
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://hdfcbnak.com/login", "https://paytrn.in", "https://goog1e.com", "https://hdfc-bank.com", "https://flipkarrt.com", "https://incometaxx.in"])
    fun `look-alike brand domains are strong`(url: String) {
        val hit = LookalikeDomainRule().evaluate(parsed(url)).single()
        assertEquals(Severity.STRONG, hit.severity)
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://www.hdfcbank.com/personal", "https://www.google.com", "https://apply.example.com", "https://cloud.com", "https://www.onlinesbi.sbi"])
    fun `real or unrelated domains are not look-alikes`(url: String) {
        assertTrue(LookalikeDomainRule().evaluate(parsed(url)).isEmpty())
    }

    @Test
    fun `brand inside an unrelated domain is medium`() {
        val rule = BrandInDomainRule()
        assertEquals(Severity.MEDIUM, rule.evaluate(parsed("http://sbi-kyc-update.xyz/login")).single().severity)
        assertEquals(1, rule.evaluate(parsed("https://paypal.com.secure-login.ru/verify")).size)
        assertTrue(rule.evaluate(parsed("https://retail.onlinesbi.sbi/")).isEmpty())
        assertTrue(rule.evaluate(parsed("https://pay.google.com/")).isEmpty())
    }

    @Test
    fun `government brands accept any gov-in host`() {
        assertTrue(BrandInDomainRule().evaluate(parsed("https://eportal.incometax.gov.in/")).isEmpty())
    }

    @Test
    fun `suspicious TLD is weak`() {
        assertEquals(setOf("SUSPICIOUS_TLD"), SuspiciousTldRule().evaluate(parsed("https://prize-win.top")).types())
        assertTrue(SuspiciousTldRule().evaluate(parsed("https://example.in")).isEmpty())
    }

    @Test
    fun `shape rule flags long URLs, deep subdomains and odd ports`() {
        val long = "https://example.com/" + "a".repeat(130)
        assertEquals(setOf("LONG_URL"), UrlShapeRule().evaluate(parsed(long)).types())
        assertEquals(setOf("MANY_SUBDOMAINS"), UrlShapeRule().evaluate(parsed("https://login.verify.account.secure-update.com")).types())
        assertEquals(setOf("UNUSUAL_PORT"), UrlShapeRule().evaluate(parsed("http://example.com:8081/")).types())
    }

    @Test
    fun `lure keywords are weak and skipped on official domains`() {
        assertEquals(setOf("LURE_KEYWORDS_IN_URL"), LureKeywordRule().evaluate(parsed("https://example.top/kyc-verify")).types())
        assertTrue(LureKeywordRule().evaluate(parsed("https://www.onlinesbi.sbi/login")).isEmpty())
    }

    @Test
    fun `plain http is weak`() {
        assertEquals(Severity.WEAK, InsecureTransportRule().evaluate(parsed("http://example.com")).single().severity)
        assertTrue(InsecureTransportRule().evaluate(parsed("https://example.com")).isEmpty())
    }

    @Test
    fun `apk download is strong`() {
        val hit = ApkLinkRule().evaluate(parsed("https://files.example.com/downloads/PM-Kisan.apk")).single()
        assertEquals(Severity.STRONG, hit.severity)
        assertEquals("PM-Kisan.apk", hit.matchedText)
    }

    @Test
    fun `edit distance handles transpositions`() {
        assertEquals(1, LookalikeDomainRule.editDistance("hdfcbnak", "hdfcbank"))
        assertEquals(0, LookalikeDomainRule.editDistance("sbi", "sbi"))
        assertEquals(3, LookalikeDomainRule.editDistance("abc", ""))
        assertEquals("paytm", LookalikeDomainRule.normalizeHomoglyphs("paytrn"))
    }
}
