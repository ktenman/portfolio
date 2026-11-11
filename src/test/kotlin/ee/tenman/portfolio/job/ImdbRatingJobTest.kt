package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.model.CollectionRunResult
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import ee.tenman.portfolio.service.monitoring.CollectionMonitorService
import ee.tenman.portfolio.service.poff.ImdbRatingService
import ee.tenman.portfolio.testing.fixture.monitorForTests
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ImdbRatingJobTest {
  @Test
  fun `should mark the ratings dataset persisted after a refresh`() {
    val collections = mutableListOf<CollectionRunResult>()
    job(monitorForTests(collections)).execute()
    expect(collections.single().persisted).toContainExactly(ImdbRatingJob.DATASET)
  }

  @Test
  fun `should expect no dataset while no film is matched`() {
    val collections = mutableListOf<CollectionRunResult>()
    job(monitorForTests(collections), mockk(relaxed = true) { every { matched() } returns false }).execute()
    expect(collections.single().expected).toBeEmpty()
  }

  @Test
  fun `should mark the ratings dataset failed when the download fails`() {
    val collections = mutableListOf<CollectionRunResult>()
    val ratings =
      mockk<ImdbRatingService> {
        every { matched() } returns true
        every { refresh() } throws IllegalStateException("IMDb dataset answered 503")
      }
    runCatching { job(monitorForTests(collections), ratings).execute() }
    expect(collections.single().failed).toContainExactly(ImdbRatingJob.DATASET)
  }

  @Test
  fun `should not announce new prices after refreshing ratings`() {
    val collections = mutableListOf<CollectionRunResult>()
    job(monitorForTests(collections)).execute()
    expect(collections.single().changed).toBeEmpty()
  }

  @Test
  fun `should refresh at startup when the last download is stale`() {
    val jobs = mockk<JobExecutionService>(relaxed = true)
    val job = job(startup(fresh = false), jobs = jobs)
    job.runStartupImport()
    verify { jobs.executeJob(job) }
  }

  @Test
  fun `should skip the startup refresh while the last download is current`() {
    val jobs = mockk<JobExecutionService>(relaxed = true)
    val job = job(startup(fresh = true), jobs = jobs)
    job.runStartupImport()
    verify(exactly = 0) { jobs.executeJob(any()) }
  }

  private fun startup(fresh: Boolean) =
    mockk<CollectionMonitorService> {
      every { current(CollectionKey.IMDB_RATINGS, listOf(ImdbRatingJob.DATASET)) } returns fresh
    }

  private fun job(
    monitor: CollectionMonitorService,
    ratings: ImdbRatingService = mockk(relaxed = true) { every { matched() } returns true },
    jobs: JobExecutionService = mockk(relaxed = true),
  ) = ImdbRatingJob(ratings, jobs, monitor)
}
