package ai.guardian.engine.eval

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Keeps the evaluation dataset well-formed; the metrics themselves come from `:engine:evaluate`. */
class DatasetTest {
    @Test
    fun `csv parser handles quotes, commas and doubled quotes`() {
        val rows = Dataset.parseCsv("a,b\n1,\"x, \"\"y\"\"\"\n")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "x, \"y\"")), rows)
    }

    @Test
    fun `dataset loads, ids are unique and both classes are present`() {
        val samples = Dataset.load(File("eval/dataset.csv"))
        assertTrue(samples.size >= 150, "only ${samples.size} samples")
        assertEquals(samples.size, samples.map { it.id }.toSet().size, "duplicate ids")
        assertTrue(samples.count { it.scam } >= 50)
        assertTrue(samples.count { !it.scam } >= 50)
    }

    @Test
    fun `metrics arithmetic`() {
        val m = Metrics(tp = 8, fp = 2, tn = 18, fn = 2)
        assertEquals(0.8, m.precision, 1e-9)
        assertEquals(0.8, m.recall, 1e-9)
        assertEquals(0.8, m.f1, 1e-9)
        assertEquals(0.1, m.fpr, 1e-9)
    }
}
