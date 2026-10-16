package ai.guardian

import ai.guardian.data.prefs.ThemeMode
import ai.guardian.data.repo.ScanRepository
import ai.guardian.data.repo.SettingsRepository
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
    data class Ready(val onboardingDone: Boolean, val theme: ThemeMode) : AppState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
    scans: ScanRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = settings.settings
        .map { AppState.Ready(it.onboardingDone, it.theme) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    init {
        // Retention is also enforced here on every launch; a daily WorkManager job is Phase 7.
        viewModelScope.launch { scans.enforceRetention() }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }
}
