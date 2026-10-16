package ai.guardian.data.repo

import ai.guardian.data.prefs.ThemeMode
import ai.guardian.data.prefs.UserSettings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme")
        val storeRawText = booleanPreferencesKey("store_raw_text")
        val retentionDays = intPreferencesKey("retention_days")
        val smsMonitoring = booleanPreferencesKey("sms_monitoring")
        val appMessageMonitoring = booleanPreferencesKey("app_message_monitoring")
        val floatingButton = booleanPreferencesKey("floating_button")
        val linkGuard = booleanPreferencesKey("link_guard")
        val trustedDomains = stringSetPreferencesKey("trusted_domains")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val alertThreshold = intPreferencesKey("alert_threshold")
        val lockScreenPrivacy = booleanPreferencesKey("lock_screen_privacy")
        val appLock = booleanPreferencesKey("app_lock")
    }

    val settings: Flow<UserSettings> = dataStore.data.map { p ->
        val defaults = UserSettings()
        UserSettings(
            onboardingDone = p[Keys.onboardingDone] ?: defaults.onboardingDone,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.theme,
            storeRawText = p[Keys.storeRawText] ?: defaults.storeRawText,
            retentionDays = p[Keys.retentionDays] ?: defaults.retentionDays,
            smsMonitoring = p[Keys.smsMonitoring] ?: defaults.smsMonitoring,
            appMessageMonitoring = p[Keys.appMessageMonitoring] ?: defaults.appMessageMonitoring,
            floatingButton = p[Keys.floatingButton] ?: defaults.floatingButton,
            linkGuard = p[Keys.linkGuard] ?: defaults.linkGuard,
            trustedDomains = p[Keys.trustedDomains] ?: defaults.trustedDomains,
            notificationsEnabled = p[Keys.notificationsEnabled] ?: defaults.notificationsEnabled,
            alertThreshold = p[Keys.alertThreshold] ?: defaults.alertThreshold,
            lockScreenPrivacy = p[Keys.lockScreenPrivacy] ?: defaults.lockScreenPrivacy,
            appLock = p[Keys.appLock] ?: defaults.appLock,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = dataStore.edit { it[Keys.onboardingDone] = done }
    suspend fun setTheme(mode: ThemeMode) = dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setStoreRawText(enabled: Boolean) = dataStore.edit { it[Keys.storeRawText] = enabled }
    suspend fun setRetentionDays(days: Int) = dataStore.edit { it[Keys.retentionDays] = days }
    suspend fun setSmsMonitoring(enabled: Boolean) = dataStore.edit { it[Keys.smsMonitoring] = enabled }
    suspend fun setAppMessageMonitoring(enabled: Boolean) = dataStore.edit { it[Keys.appMessageMonitoring] = enabled }
    suspend fun setFloatingButton(enabled: Boolean) = dataStore.edit { it[Keys.floatingButton] = enabled }
    suspend fun setLinkGuard(enabled: Boolean) = dataStore.edit { it[Keys.linkGuard] = enabled }
    suspend fun trustDomain(domain: String) = dataStore.edit { it[Keys.trustedDomains] = (it[Keys.trustedDomains] ?: emptySet()) + domain.lowercase() }
    suspend fun untrustDomain(domain: String) = dataStore.edit { it[Keys.trustedDomains] = (it[Keys.trustedDomains] ?: emptySet()) - domain }
    suspend fun setNotificationsEnabled(enabled: Boolean) = dataStore.edit { it[Keys.notificationsEnabled] = enabled }
    suspend fun setAlertThreshold(score: Int) = dataStore.edit { it[Keys.alertThreshold] = score.coerceIn(0, 100) }
    suspend fun setLockScreenPrivacy(enabled: Boolean) = dataStore.edit { it[Keys.lockScreenPrivacy] = enabled }
    suspend fun setAppLock(enabled: Boolean) = dataStore.edit { it[Keys.appLock] = enabled }
}
