package ee.tenman.portfolio.service.monitoring

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CollectionRerunServiceTest {
  @Test
  fun `should execute the job behind the collection in the background`() {
    val job = mockk<Job> { every { getName() } returns VanguardHoldingsRetrievalJob::class.java.simpleName }
    val executor = mockk<JobExecutionService>(relaxed = true)
    CollectionRerunService(listOf(job), executor).rerun(CollectionKey.VANGUARD_HOLDINGS)
    verify(timeout = 5000) { executor.executeJob(job) }
  }

  @Test
  fun `should skip a rerun of a collection whose previous rerun is still running`() {
    val job = mockk<Job> { every { getName() } returns VanguardHoldingsRetrievalJob::class.java.simpleName }
    val runs = Semaphore(0)
    val release = CountDownLatch(1)
    val executor =
      mockk<JobExecutionService> {
        every { executeJob(job) } answers {
          runs.release()
          release.await(5, TimeUnit.SECONDS)
        }
      }
    val service = CollectionRerunService(listOf(job), executor)
    service.rerun(CollectionKey.VANGUARD_HOLDINGS)
    runs.tryAcquire(5, TimeUnit.SECONDS)
    service.rerun(CollectionKey.VANGUARD_HOLDINGS)
    val duplicated = runs.tryAcquire(500, TimeUnit.MILLISECONDS)
    release.countDown()
    expect(duplicated).toEqual(false)
  }

  @Test
  fun `should rerun a collection again once its previous rerun finished`() {
    val job = mockk<Job> { every { getName() } returns VanguardHoldingsRetrievalJob::class.java.simpleName }
    val runs = Semaphore(0)
    val executor = mockk<JobExecutionService> { every { executeJob(job) } answers { runs.release() } }
    val service = CollectionRerunService(listOf(job), executor)
    service.rerun(CollectionKey.VANGUARD_HOLDINGS)
    runs.tryAcquire(5, TimeUnit.SECONDS)
    val restarted =
      (1..50).any {
        service.rerun(CollectionKey.VANGUARD_HOLDINGS)
        runs.tryAcquire(100, TimeUnit.MILLISECONDS)
      }
    expect(restarted).toEqual(true)
  }

  @Test
  fun `should reject a rerun when the job is not scheduled`() {
    val service = CollectionRerunService(emptyList(), mockk())
    expect { service.rerun(CollectionKey.FT_HISTORY) }.toThrow<IllegalStateException>().messageToContain("FtDataRetrievalJob")
  }
}
