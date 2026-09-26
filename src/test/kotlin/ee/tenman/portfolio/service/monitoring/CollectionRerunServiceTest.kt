package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.CollectionKey
import ee.tenman.portfolio.job.Job
import ee.tenman.portfolio.job.VanguardHoldingsRetrievalJob
import ee.tenman.portfolio.service.infrastructure.JobExecutionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CollectionRerunServiceTest {
  @Test
  fun `should execute the job behind the collection in the background`() {
    val job = mockk<Job> { every { getName() } returns VanguardHoldingsRetrievalJob::class.java.simpleName }
    val executor = mockk<JobExecutionService>(relaxed = true)
    CollectionRerunService(listOf(job), executor).rerun(CollectionKey.VANGUARD_HOLDINGS)
    verify(timeout = 5000) { executor.executeJob(job) }
  }

  @Test
  fun `should reject a rerun when the job is not scheduled`() {
    val service = CollectionRerunService(emptyList(), mockk())
    expect { service.rerun(CollectionKey.FT_HISTORY) }.toThrow<IllegalStateException>().messageToContain("FtDataRetrievalJob")
  }
}
