package ai.guardian

import ai.guardian.engine.Analyzer
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.SecurityEvent
import ai.guardian.ui.actionBullets
import ai.guardian.ui.shortTitle
import ai.guardian.ui.verdict
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The UI's plain-word layer over engine results, run against the real engine on a device. */
@RunWith(AndroidJUnit4::class)
class VerdictPresentationTest {
    private val analyzer = Analyzer()

    private fun analyze(type: EventType, input: String) =
        analyzer.analyze(SecurityEvent("t", type, input, EventSource.MANUAL, 0L))

    @Test
    fun noWarningSignsIsNeverCalledSafe() {
        val v = analyze(EventType.MANUAL_TEXT_EVENT, "See you at lunch tomorrow").verdict()
        assertEquals("No warning signs", v.title)
        assertFalse(v.title.contains("safe", ignoreCase = true))
        assertTrue(v.subtitle.contains("Not a guarantee"))
    }

    @Test
    fun scamGetsAStrongVerdictAndShortReasons() {
        val r = analyze(EventType.MANUAL_TEXT_EVENT, "Dear customer, your SBI account will be blocked today. Update your KYC immediately: http://sbi-kyc-update.xyz/login")
        assertEquals("Scam alert", r.verdict().title)
        // Every reason has a short title, never the long engine sentence.
        r.indicators.forEach { assertTrue("too long: ${it.shortTitle()}", it.shortTitle().length <= 40) }
        assertTrue(r.actionBullets().size >= 2)
    }
}
