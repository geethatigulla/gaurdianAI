package ai.guardian.engine.extract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UrlParserTest {
    @Test
    fun `parses all components`() {
        val u = requireNotNull(UrlParser.parse("HTTPS://user:pw@Login.Example.co.in:8443/a/b?x=1#frag"))
        assertEquals("https", u.scheme)
        assertEquals("user:pw", u.userInfo)
        assertEquals("login.example.co.in", u.host)
        assertEquals(8443, u.port)
        assertEquals("/a/b", u.path)
        assertEquals("x=1", u.query)
        assertEquals("frag", u.fragment)
        assertEquals("example.co.in", u.registrableDomain)
        assertEquals(listOf("login"), u.subdomainLabels)
    }

    @Test
    fun `parses bare domains, IPv4 and IPv6 hosts`() {
        assertEquals("sbi-kyc.xyz", UrlParser.parse("sbi-kyc.xyz/login")?.host)
        assertTrue(UrlParser.parse("http://10.0.0.1/x")!!.isIpv4Host)
        val v6 = UrlParser.parse("http://[2001:db8::1]:8080/")!!
        assertTrue(v6.isIpv6Host)
        assertEquals(8080, v6.port)
    }

    @Test
    fun `rejects empty, spaced or bad-port input`() {
        assertNull(UrlParser.parse(""))
        assertNull(UrlParser.parse("hello world.com"))
        assertNull(UrlParser.parse("http://example.com:99999/"))
    }

    @ParameterizedTest
    @CsvSource(
        "www.onlinesbi.sbi, onlinesbi.sbi",
        "a.b.sbi.co.in, sbi.co.in",
        "evil.github.io, evil.github.io",
        "x.s3.amazonaws.com, x.s3.amazonaws.com",
        "paypal.com.secure-login.ru, secure-login.ru",
        "hdfcbank.com, hdfcbank.com",
    )
    fun `registrable domain respects multi-label suffixes`(host: String, expected: String) {
        assertEquals(expected, DomainUtils.registrableDomain(host))
    }
}

class UrlExtractorTest {
    @Test
    fun `finds scheme, www and bare links and trims punctuation`() {
        val text = "Visit https://example.com/path. Or www.test.in, or pay at sbi-kyc.xyz/login!"
        val hosts = UrlExtractor.extract(text).map { it.host }
        assertEquals(listOf("example.com", "www.test.in", "sbi-kyc.xyz"), hosts)
        assertEquals("/path", UrlExtractor.extract(text).first().path)
    }

    @Test
    fun `does not treat e-mail addresses or amounts as links`() {
        val urls = UrlExtractor.extract("Mail care@gmail.com about Rs.500.00 debit")
        assertTrue(urls.isEmpty(), "found $urls")
    }

    @Test
    fun `finds bare IP links with a path`() {
        val urls = UrlExtractor.extract("Pay now 45.67.89.10/bill.apk before 9pm")
        assertEquals("45.67.89.10", urls.single().host)
    }

    @Test
    fun `parseSingle accepts a user-typed address without scheme but rejects plain words`() {
        assertEquals("example.com", UrlExtractor.parseSingle("example.com")?.host)
        assertNull(UrlExtractor.parseSingle("hello"))
    }
}

class EntityExtractorTest {
    private val extractor = EntityExtractor()

    @Test
    fun `extracts Indian phone numbers but not OTPs or amounts`() {
        val e = extractor.extractFromText("Your OTP is 482913. Call +91 98765 43210 or 18001234567. Amount Rs 25000")
        assertEquals(listOf("+919876543210", "18001234567"), e.phones)
    }

    @Test
    fun `distinguishes UPI IDs from e-mail addresses`() {
        val e = extractor.extractFromText("Pay to refund.help@ybl or write to support@bank.com.")
        assertEquals(listOf("refund.help@ybl"), e.upiIds)
        assertEquals(listOf("support@bank.com"), e.emails)
    }

    @Test
    fun `digits inside links are not read as phone numbers`() {
        val e = extractor.extractFromText("Track https://example.com/track/98765432101")
        assertTrue(e.phones.isEmpty())
        assertFalse(e.urls.isEmpty())
    }
}
