package ee.tenman.portfolio.testing.fixture

import ee.tenman.portfolio.model.CollectionRun
import ee.tenman.portfolio.model.CollectionRunResult
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import io.mockk.every
import io.mockk.mockk

fun monitorForTests(results: MutableList<CollectionRunResult> = mutableListOf()): CollectionMonitorService =
  mockk<CollectionMonitorService>().also { monitor ->
    every { monitor.collect<Any?>(any(), any(), any()) } answers {
      val run = CollectionRun(secondArg())
      try {
        thirdArg<(CollectionRun) -> Any?>().invoke(run)
      } finally {
        results += run.result()
      }
    }
  }
