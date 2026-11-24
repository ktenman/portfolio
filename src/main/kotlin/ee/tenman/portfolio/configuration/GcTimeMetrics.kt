package ee.tenman.portfolio.configuration

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory

@Component
class GcTimeMetrics : MeterBinder {
  override fun bindTo(registry: MeterRegistry) {
    ManagementFactory.getGarbageCollectorMXBeans().forEach { bind(registry, it) }
  }

  private fun bind(
    registry: MeterRegistry,
    collector: GarbageCollectorMXBean,
  ) {
    FunctionCounter
      .builder("jvm.gc.collection.time", collector) { it.collectionTime / MILLIS_PER_SECOND }
      .baseUnit("seconds")
      .description("Time this collector has spent collecting, which a native image reports without GC notifications")
      .tag("gc", collector.name)
      .register(registry)
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1000.0
  }
}
