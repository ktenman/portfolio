package ee.tenman.portfolio.configuration

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactlyElementsOf
import ch.tutteli.atrium.api.verbs.expect
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory

class GcTimeMetricsTest {
  @Test
  fun `registers a collection time counter for every garbage collector`() {
    val registry = SimpleMeterRegistry().also { GcTimeMetrics().bindTo(it) }
    val counted = registry.find("jvm.gc.collection.time").functionCounters().map { it.id.getTag("gc") }
    val collectors = ManagementFactory.getGarbageCollectorMXBeans().map { it.name }
    expect(counted.sortedBy { it }).toContainExactlyElementsOf(collectors.sorted())
  }
}
