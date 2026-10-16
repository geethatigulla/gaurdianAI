package ai.guardian.feature.history

import ai.guardian.core.designsystem.EmptyState
import ai.guardian.data.repo.ScanRepository
import ai.guardian.data.repo.ScanSummary
import ai.guardian.engine.model.Category
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.RiskLevel
import ai.guardian.ui.ScanRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LevelFilter(val label: String, val matches: (RiskLevel) -> Boolean) {
    ALL("All", { true }),
    HIGH("High & critical", { it >= RiskLevel.HIGH }),
    MODERATE("Moderate", { it == RiskLevel.MODERATE }),
    LOW("Low & guarded", { it <= RiskLevel.GUARDED }),
}

enum class TypeFilter(val label: String, val matches: (EventType) -> Boolean) {
    ALL("All types", { true }),
    MESSAGES("Messages", { it == EventType.SMS_EVENT || it == EventType.APP_MESSAGE_EVENT || it == EventType.MANUAL_TEXT_EVENT }),
    LINKS("Links", { it == EventType.URL_EVENT }),
    QR("QR codes", { it == EventType.QR_EVENT }),
}

data class ActivityFilters(val query: String = "", val level: LevelFilter = LevelFilter.ALL, val type: TypeFilter = TypeFilter.ALL)

data class ActivityUiState(
    val filters: ActivityFilters = ActivityFilters(),
    val items: List<ScanSummary>? = null,
    val totalCount: Int = 0,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(private val repository: ScanRepository) : ViewModel() {
    private val filters = MutableStateFlow(ActivityFilters())

    val state: StateFlow<ActivityUiState> = combine(repository.observeHistory(), filters) { all, f ->
        val q = f.query.trim().lowercase()
        val filtered = all.filter { s ->
            f.level.matches(s.riskLevel) && f.type.matches(s.eventType) &&
                (q.isEmpty() || s.displayLabel.lowercase().contains(q) || Category.displayName(s.category).lowercase().contains(q))
        }
        ActivityUiState(f, filtered, all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun setLevel(l: LevelFilter) = filters.update { it.copy(level = l) }
    fun setType(t: TypeFilter) = filters.update { it.copy(type = t) }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
    fun clearAll() = viewModelScope.launch { repository.clearAll() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onOpenResult: (String) -> Unit, viewModel: ActivityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                actions = {
                    if (state.totalCount > 0) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.totalCount > 0) {
                OutlinedTextField(
                    value = state.filters.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search by type, link or category") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LevelFilter.entries.forEach { f -> FilterChip(state.filters.level == f, { viewModel.setLevel(f) }, label = { Text(f.label) }) }
                    TypeFilter.entries.drop(1).forEach { f ->
                        val selected = state.filters.type == f
                        FilterChip(selected, { viewModel.setType(if (selected) TypeFilter.ALL else f) }, label = { Text(f.label) })
                    }
                }
            }
            val items = state.items
            when {
                items == null -> Unit
                state.totalCount == 0 -> EmptyState(
                    icon = Icons.Outlined.History,
                    title = "No history",
                    body = "Scans you run are listed here and deleted automatically based on your retention setting.",
                )
                items.isEmpty() -> EmptyState(icon = Icons.Outlined.Search, title = "No matches", body = "Try a different search or filter.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { scan ->
                        ScanRow(scan, onClick = { onOpenResult(scan.id) }) {
                            IconButton(onClick = { viewModel.delete(scan.id) }) { Icon(Icons.Outlined.Close, contentDescription = "Delete") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("All ${state.totalCount} saved results will be permanently deleted from this phone.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearAll() }) { Text("Clear all") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
