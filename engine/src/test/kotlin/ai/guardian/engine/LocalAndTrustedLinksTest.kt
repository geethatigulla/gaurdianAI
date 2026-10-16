package ai.guardian.engine

import ai.guardian.engine.extract.DomainUtils
import ai.guardian.engine.model.CheckStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Developers' local links and user-trusted sites must not be reported as scams. */
class LocalAndTrustedLinksTest {

    @ParameterizedTest
    @ValueSource(strings = ["localhost", "127.0.0.1", "10.0.0.5", "192.168.1.20", "172.16.4.1", "172.31.255.1", "169.254.1.1", "myapp.local", "api.test", "db.internal", "::1", "fe80::1"])
    fun `local and private hosts are recognised`(host: String) {
        assertTrue(DomainUtils.isLocalOrPrivate(host))
    }

    @ParameterizedTest
    @ValueSource(strings = ["8.8.8.8", "172.32.0.1", "45.67.89.10", "example.com", "localhost.evil.com", "sbi-kyc.xyz"])
    fun `public hosts are not treated as local`(host: String) {
        assertFalse(DomainUtils.isLocalOrPrivate(host))
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://192.168.1.20:8080/login", "http://localhost:3000/verify-account", "http://10.0.2.2:5000/api/kyc", "http://myapp.local/admin"])
    fun `local development links score zero`(url: String) {
        val r = analyzer.analyze(TestEvents.url(url))
        assertEquals(0, r.riskScore, "indicators: ${r.indicators}")
        assertTrue(r.checks.any { it.name == "Local & trusted links" && it.status == CheckStatus.COMPLETED })
        assertTrue(r.checks.none { it.status == CheckStatus.FAILED })
    }

    @Test
    fun `public IP link is still flagged`() {
        assertTrue(analyzer.analyze(TestEvents.url("http://45.67.89.10/EB-bill.apk")).riskScore >= 60)
    }

    @Test
    fun `trusted domain and its subdomains are skipped`() {
        val url = "http://staging.mycompany-dev.xyz:8443/login"
        assertTrue(analyzer.analyze(TestEvents.url(url)).riskScore > 0)
        val trusted = analyzer.analyze(TestEvents.url(url), trustedDomains = setOf("mycompany-dev.xyz"))
        assertEquals(0, trusted.riskScore)
        assertTrue(trusted.checks.any { it.note?.contains("trusted") == true })
    }

    @Test
    fun `trusting a site does not hide scam wording in the message`() {
        val r = analyzer.analyze(TestEvents.text("Share the OTP you received at https://mycompany.com/x"), trustedDomains = setOf("mycompany.com"))
        assertTrue("SECRET_REQUEST" in r.indicators.types())
    }

    @Test
    fun `trust is per domain, not a substring match`() {
        val r = analyzer.analyze(TestEvents.url("http://hdfcbnak.com/login"), trustedDomains = setOf("bnak.com"))
        assertTrue(r.riskScore >= 60)
    }
}
