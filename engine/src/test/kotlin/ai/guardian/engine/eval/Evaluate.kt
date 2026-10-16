package ai.guardian.engine.eval

import ai.guardian.engine.Analyzer
import ai.guardian.engine.model.EventSource
import ai.guardian.engine.model.EventType
import ai.guardian.engine.model.RiskLevel
import ai.guardian.engine.model.RiskResult
import ai.guardian.engine.model.SecurityEvent
import java.io.File
import java.util.Locale

/**
 * Offline evaluation of the engine on a labelled CSV dataset. Produces the tables used in the paper:
 * confusion matrix + precision/recall/F1/FPR at several score thresholds, recall per scam type,
 * results per channel, false positives/negatives, and per-message latency.
 *
 * Run: ./gradlew :engine:evaluate   (writes docs/evaluation/results.md and per-sample.csv)
 */
data class Sample(val id: String, val channel: String, val scam: Boolean, val scamType: String, val text: String)

data class Scored(val sample: Sample, val result: RiskResult, val micros: Double)

data class Metrics(val tp: Int, val fp: Int, val tn: Int, val fn: Int) {
    val precision get() = ratio(tp, tp + fp)
    val recall get() = ratio(tp, tp + fn)
    val f1 get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
    val fpr get() = ratio(fp, fp + tn)
    val accuracy get() = ratio(tp + tn, tp + tn + fp + fn)

    companion object {
        fun ratio(a: Int, b: Int) = if (b == 0) 0.0 else a.toDouble() / b
        fun of(rows: List<Scored>, threshold: Int) = Metrics(
            tp = rows.count { it.sample.scam && it.result.riskScore >= threshold },
            fp = rows.count { !it.sample.scam && it.result.riskScore >= threshold },
            tn = rows.count { !it.sample.scam && it.result.riskScore < threshold },
            fn = rows.count { it.sample.scam && it.result.riskScore < threshold },
        )
    }
}

object Dataset {
    private val channelTypes = mapOf(
        "sms" to EventType.SMS_EVENT,
        "app" to EventType.APP_MESSAGE_EVENT,
        "text" to EventType.MANUAL_TEXT_EVENT,
        "url" to EventType.URL_EVENT,
        "qr" to EventType.QR_EVENT,
        "domain" to EventType.DOMAIN_EVENT,
    )

    fun eventType(channel: String) = channelTypes[channel] ?: error("Unknown channel '$channel'")

    fun load(file: File): List<Sample> {
        val rows = parseCsv(file.readText(Charsets.UTF_8))
        val header = rows.first().map { it.trim() }
        require(header == listOf("id", "channel", "label", "scam_type", "text")) { "Unexpected header: $header" }
        return rows.drop(1).filter { it.size == 5 && it[0].isNotBlank() }.map { r ->
            require(r[2] == "scam" || r[2] == "legit") { "Bad label in ${r[0]}: ${r[2]}" }
            eventType(r[1])
            Sample(r[0], r[1], r[2] == "scam", r[3], r[4])
        }
    }

    /** Minimal RFC 4180 parser: quoted fields, doubled quotes, newlines inside quotes. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}

object Evaluator {
    val thresholds = listOf(20, 40, 60)
    const val PRIMARY_THRESHOLD = 40 // "Suspicious" (MODERATE) and above counts as flagged.
    const val MAX_LISTED = 40

    fun score(samples: List<Sample>, analyzer: Analyzer = Analyzer(clock = { 0L })): List<Scored> {
        fun event(s: Sample) = SecurityEvent(s.id, Dataset.eventType(s.channel), s.text, EventSource.MANUAL, 0L)
        repeat(3) { samples.forEach { analyzer.analyze(event(it)) } } // JIT warm-up so latency isn't dominated by class loading.
        return samples.map { s ->
            val runs = 20
            val start = System.nanoTime()
            var result: RiskResult? = null
            repeat(runs) { result = analyzer.analyze(event(s)) }
            Scored(s, result!!, (System.nanoTime() - start) / 1000.0 / runs)
        }
    }

    fun report(rows: List<Scored>, datasetName: String): String = buildString {
        fun pct(x: Double) = String.format(Locale.ROOT, "%.1f%%", x * 100)
        fun f3(x: Double) = String.format(Locale.ROOT, "%.3f", x)
        val scams = rows.count { it.sample.scam }
        val legit = rows.size - scams

        appendLine("# Guardian engine — evaluation results")
        appendLine()
        appendLine("Dataset: `$datasetName` — ${rows.size} samples ($scams scam, $legit legitimate). Scoring version ${rows.first().result.scoringVersion}.")
        appendLine("A sample is *flagged* when its risk score ≥ threshold. Primary threshold: $PRIMARY_THRESHOLD (Suspicious and above).")
        appendLine()
        appendLine("## Overall")
        appendLine()
        appendLine("| Threshold | TP | FP | TN | FN | Precision | Recall | F1 | False-positive rate | Accuracy |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|")
        thresholds.forEach { t ->
            val m = Metrics.of(rows, t)
            appendLine("| ≥ $t | ${m.tp} | ${m.fp} | ${m.tn} | ${m.fn} | ${f3(m.precision)} | ${f3(m.recall)} | ${f3(m.f1)} | ${pct(m.fpr)} | ${pct(m.accuracy)} |")
        }
        appendLine()

        appendLine("## By channel (threshold $PRIMARY_THRESHOLD)")
        appendLine()
        appendLine("| Channel | Samples | Precision | Recall | F1 | False-positive rate |")
        appendLine("|---|---|---|---|---|---|")
        rows.groupBy { it.sample.channel }.toSortedMap().forEach { (channel, group) ->
            val m = Metrics.of(group, PRIMARY_THRESHOLD)
            appendLine("| $channel | ${group.size} | ${f3(m.precision)} | ${f3(m.recall)} | ${f3(m.f1)} | ${pct(m.fpr)} |")
        }
        appendLine()

        appendLine("## Recall by scam type (threshold $PRIMARY_THRESHOLD)")
        appendLine()
        appendLine("| Scam type | Samples | Detected | Recall |")
        appendLine("|---|---|---|---|")
        rows.filter { it.sample.scam }.groupBy { it.sample.scamType }.toSortedMap().forEach { (type, group) ->
            val hit = group.count { it.result.riskScore >= PRIMARY_THRESHOLD }
            appendLine("| $type | ${group.size} | $hit | ${pct(Metrics.ratio(hit, group.size))} |")
        }
        appendLine()

        appendLine("## False-positive rate by kind of legitimate message (threshold $PRIMARY_THRESHOLD)")
        appendLine()
        appendLine("| Legitimate kind | Samples | Wrongly flagged | Rate |")
        appendLine("|---|---|---|---|")
        rows.filter { !it.sample.scam }.groupBy { it.sample.scamType }.toSortedMap().forEach { (type, group) ->
            val fp = group.count { it.result.riskScore >= PRIMARY_THRESHOLD }
            appendLine("| $type | ${group.size} | $fp | ${pct(Metrics.ratio(fp, group.size))} |")
        }
        appendLine()

        appendLine("## Risk level distribution")
        appendLine()
        appendLine("| Level | Scam | Legitimate |")
        appendLine("|---|---|---|")
        RiskLevel.entries.forEach { level ->
            appendLine("| ${level.name} | ${rows.count { it.sample.scam && it.result.riskLevel == level }} | ${rows.count { !it.sample.scam && it.result.riskLevel == level }} |")
        }
        appendLine()

        val micros = rows.map { it.micros }.sorted()
        appendLine("## Latency (JVM, after warm-up)")
        appendLine()
        appendLine("Mean ${f3(micros.average() / 1000)} ms · median ${f3(micros[micros.size / 2] / 1000)} ms · p95 ${f3(micros[(micros.size * 95 / 100).coerceAtMost(micros.size - 1)] / 1000)} ms per sample.")
        appendLine()

        fun listing(title: String, items: List<Scored>) {
            appendLine("## $title (${items.size})")
            appendLine()
            if (items.isEmpty()) { appendLine("None."); appendLine(); return }
            if (items.size > MAX_LISTED) {
                appendLine("Showing the first $MAX_LISTED; see per-sample.csv for all.")
                appendLine()
            }
            appendLine("| id | type | score | indicators | text |")
            appendLine("|---|---|---|---|---|")
            items.take(MAX_LISTED).forEach { s ->
                val ind = s.result.indicators.joinToString(", ") { it.type }.ifEmpty { "—" }
                appendLine("| ${s.sample.id} | ${s.sample.scamType} | ${s.result.riskScore} | $ind | ${s.sample.text.replace("|", "/").replace("\n", " ").take(90)} |")
            }
            appendLine()
        }
        listing("False negatives — scams scored below $PRIMARY_THRESHOLD", rows.filter { it.sample.scam && it.result.riskScore < PRIMARY_THRESHOLD })
        listing("False positives — legitimate items scored $PRIMARY_THRESHOLD or above", rows.filter { !it.sample.scam && it.result.riskScore >= PRIMARY_THRESHOLD })
    }

    fun perSampleCsv(rows: List<Scored>): String = buildString {
        appendLine("id,channel,label,scam_type,score,level,category,indicators")
        rows.forEach { s ->
            val r = s.result
            appendLine(listOf(s.sample.id, s.sample.channel, if (s.sample.scam) "scam" else "legit", s.sample.scamType, r.riskScore, r.riskLevel, r.category, r.indicators.joinToString(";") { it.type }).joinToString(","))
        }
    }
}

fun main(args: Array<String>) {
    val dataset = File(args.getOrElse(0) { "eval/dataset.csv" })
    val outDir = File(args.getOrElse(1) { "../docs/evaluation" }).apply { mkdirs() }
    val samples = Dataset.load(dataset)
    val rows = Evaluator.score(samples)
    File(outDir, "results.md").writeText(Evaluator.report(rows, dataset.name))
    File(outDir, "per-sample.csv").writeText(Evaluator.perSampleCsv(rows))
    val m = Metrics.of(rows, Evaluator.PRIMARY_THRESHOLD)
    println(String.format(Locale.ROOT, "%d samples | threshold %d | precision %.3f recall %.3f F1 %.3f FPR %.3f", rows.size, Evaluator.PRIMARY_THRESHOLD, m.precision, m.recall, m.f1, m.fpr))
    println("Wrote ${File(outDir, "results.md").canonicalPath}")
}
