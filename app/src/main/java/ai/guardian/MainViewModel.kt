package ai.guardian

import ai.guardian.data.prefs.ThemeMode
import ai.guardian.data.repo.ScanRepository
import ai.guardian.data.repo.SettingsRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState
    data class Ready(val onboardingDone: Boolean, val theme: ThemeMode, val appLock: Boolean) : AppState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
    scans: ScanRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = settings.settings
        .map { AppState.Ready(it.onboardingDone, it.theme, it.appLock) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    /** Kept here (not in the Activity) so rotating the screen doesn't re-lock the app. */
    var unlocked by mutableStateOf(false)
    var unlockError by mutableStateOf<String?>(null)
    var backgroundedAt = 0L

    init {
        // Also enforced daily by RetentionWorker; doing it on launch keeps history tidy right away.
        viewModelScope.launch { scans.enforceRetention() }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }
}
