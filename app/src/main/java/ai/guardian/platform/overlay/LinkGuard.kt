package ai.guardian.platform.overlay

import ai.guardian.data.repo.ScanRecord
import ai.guardian.data.repo.ScanRepository
import ai.guardian.data.repo.SettingsRepository
import ai.guardian.di.AppClock
import ai.guardian.engine.Analyzer
import ai.guardian.engine.extract.DomainUtils
import ai.guardian.engine.extract.UrlExtractor
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.SecurityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the website open in the user's normal browser, read from its address bar. Safe-looking
 * sites are checked silently and not saved; only risky ones are saved and warned about.
 */
@Singleton
class LinkGuard @Inject constructor(
    private val analyzer: Analyzer,
    private val repository: ScanRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    private var lastHost: String? = null

    /** Returns a record only when the site should be warned about. */
    suspend fun check(addressBarText: String): ScanRecord? {
        val text = addressBarText.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null // Search text, not an address.
        val url = UrlExtractor.parseSingle(text) ?: return null
        if (DomainUtils.isLocalOrPrivate(url.host) || url.host == lastHost) return null
        lastHost = url.host

        val prefs = settings.current()
        val event = SecurityEvent(UUID.randomUUID().toString(), EventType.URL_EVENT, text, EventSource.BROWSER, clock.now())
        val result = analyzer.analyze(event, prefs.trustedDomains)
        if (result.riskScore < prefs.alertThreshold) return null
        return repository.save(event, result)
    }

    companion object {
        /** Address-bar view IDs for common Android browsers. */
        val ADDRESS_BAR_IDS = mapOf(
            "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
            "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
            "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
            "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
            "com.kiwibrowser.browser" to listOf("com.kiwibrowser.browser:id/url_bar"),
            "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
            "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),
            "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
            "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
        )

        /** The address shown in the browser, or null while the user is typing in it. */
        fun readAddressBar(root: AccessibilityNodeInfo, packageName: String): String? {
            val ids = ADDRESS_BAR_IDS[packageName] ?: return null
            for (id in ids) {
                val node = root.findAccessibilityNodeInfosByViewId(id).firstOrNull() ?: continue
                if (node.isFocused) return null
                return node.text?.toString()
            }
            return null
        }
    }
}
