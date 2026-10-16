package ai.guardian.data.repo

import ai.guardian.data.db.CheckEntity
import ai.guardian.data.db.EvidenceEntity
import ai.guardian.data.db.ScanDao
import ai.guardian.data.db.ScanEventEntity
import ai.guardian.data.db.ScanWithDetails
import ai.guardian.di.AppClock
import ai.guardian.engine.model.Check
import ai.guardian.engine.model.CheckStatus
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.EvidenceQuality
import ai.guardian.engine.model.Indicator
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.model.RiskResult
import ai.guardian.engine.model.SecurityEvent
import ai.guardian.engine.model.Severity
import ai.guardian.engine.privacy.Redactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val dao: ScanDao,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    // Recent results kept in memory so a result can always be shown, even with history turned off.
    private val cacheLock = Mutex()
    private val cache = object : LinkedHashMap<String, ScanRecord>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ScanRecord>?) = size > CACHE_SIZE
    }

    suspend fun save(event: SecurityEvent, result: RiskResult): ScanRecord {
        val prefs = settings.current()
        val keep = prefs.retentionDays > 0
        val record = ScanRecord(
            result = result,
            source = event.source,
            displayLabel = DisplayLabel.forEvent(event),
            rawInputRedacted = if (prefs.storeRawText) Redactor.redact(event.rawInput) else null,
            userFeedback = null,
            savedToHistory = keep,
            openableUrl = OpenableUrl.forEvent(event),
            linkDomains = LinkDomains.forEvent(event),
        )
        cacheLock.withLock { cache[result.eventId] = record }
        if (keep) {
            dao.insertScan(record.toEntity(), result.indicators.toEvidence(result.eventId), result.checks.toCheckEntities(result.eventId))
        }
        return record
    }

    // Memory first: it carries the openable link, which is never written to disk.
    suspend fun get(id: String): ScanRecord? =
        cacheLock.withLock { cache[id] } ?: dao.getScan(id)?.toRecord()

    fun observeHistory(): Flow<List<ScanSummary>> = dao.observeAll().map { list -> list.map { it.toSummary() } }

    fun observeDashboard(): Flow<DashboardStats> {
        val since = clock.now() - TimeUnit.DAYS.toMillis(30)
        return combine(
            dao.observeCountSince(since),
            dao.observeCountAtLeast(RiskLevel.MODERATE.minScore, since),
            dao.observeRecent(3),
        ) { total, warnings, recent -> DashboardStats(total, warnings, recent.map { it.toSummary() }) }
    }

    suspend fun setFeedback(id: String, feedback: UserFeedback?) {
        dao.setFeedback(id, feedback?.name)
        cacheLock.withLock { cache[id]?.let { cache[id] = it.copy(userFeedback = feedback) } }
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        cacheLock.withLock { cache.remove(id) }
    }

    suspend fun clearAll() {
        dao.deleteAll()
        cacheLock.withLock { cache.clear() }
    }

    /** Applies the retention setting. Returns the number of rows removed. */
    suspend fun enforceRetention(): Int {
        val days = settings.current().retentionDays
        if (days <= 0) {
            dao.deleteAll()
            return 0
        }
        return dao.deleteOlderThan(clock.now() - TimeUnit.DAYS.toMillis(days.toLong()))
    }

    private companion object {
        const val CACHE_SIZE = 20
    }
}

// ---- Mapping --------------------------------------------------------------------------------

private inline fun <reified T : Enum<T>> safeEnum(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

private fun ScanRecord.toEntity() = ScanEventEntity(
    id = result.eventId,
    eventType = result.eventType.name,
    source = source.name,
    riskScore = result.riskScore,
    riskLevel = result.riskLevel.name,
    category = result.category,
    evidenceQuality = result.evidenceQuality.name,
    scoringVersion = result.scoringVersion,
    summary = result.summary,
    recommendation = result.recommendation,
    createdAt = result.analyzedAt,
    displayLabel = displayLabel,
    rawInputRedacted = rawInputRedacted,
    userFeedback = userFeedback?.name,
)

private fun List<Indicator>.toEvidence(eventId: String) = mapIndexed { i, it ->
    EvidenceEntity(
        scanEventId = eventId, indicatorType = it.type, family = it.family, severity = it.severity.name,
        description = it.description, source = it.source, matchedText = it.matchedText, position = i,
    )
}

private fun List<Check>.toCheckEntities(eventId: String) = mapIndexed { i, it ->
    CheckEntity(scanEventId = eventId, name = it.name, status = it.status.name, note = it.note, position = i)
}

private fun ScanEventEntity.toSummary() = ScanSummary(
    id = id,
    eventType = safeEnum(eventType, EventType.MANUAL_TEXT_EVENT),
    riskScore = riskScore,
    riskLevel = safeEnum(riskLevel, RiskLevel.fromScore(riskScore)),
    category = category,
    displayLabel = displayLabel,
    createdAt = createdAt,
)

private fun ScanWithDetails.toRecord(): ScanRecord {
    val e = event
    val result = RiskResult(
        eventId = e.id,
        eventType = safeEnum(e.eventType, EventType.MANUAL_TEXT_EVENT),
        riskScore = e.riskScore,
        riskLevel = safeEnum(e.riskLevel, RiskLevel.fromScore(e.riskScore)),
        category = e.category,
        evidenceQuality = safeEnum(e.evidenceQuality, EvidenceQuality.LOW),
        summary = e.summary,
        indicators = evidence.sortedBy { it.position }.map {
            Indicator(it.indicatorType, it.family, safeEnum(it.severity, Severity.WEAK), it.description, it.source, it.matchedText)
        },
        checks = checks.sortedBy { it.position }.map { Check(it.name, safeEnum(it.status, CheckStatus.FAILED), it.note) },
        recommendation = e.recommendation,
        scoringVersion = e.scoringVersion,
        analyzedAt = e.createdAt,
    )
    return ScanRecord(
        result = result,
        source = safeEnum(e.source, EventSource.MANUAL),
        displayLabel = e.displayLabel,
        rawInputRedacted = e.rawInputRedacted,
        userFeedback = e.userFeedback?.let { runCatching { UserFeedback.valueOf(it) }.getOrNull() },
        savedToHistory = true,
    )
}
