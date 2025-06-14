package ee.tenman.portfolio.service

import ee.tenman.portfolio.configuration.IntegrationTest
import ee.tenman.portfolio.domain.JobStatus
import ee.tenman.portfolio.job.Job
import ee.tenman.portfolio.repository.JobExecutionRepository
import jakarta.annotation.Resource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@IntegrationTest
class JobExecutionServiceIT {
  @Resource
  private lateinit var jobExecutionService: JobExecutionService

  @Resource
  private lateinit var jobExecutionRepository: JobExecutionRepository

  @MockitoBean
  private lateinit var clock: Clock

  private lateinit var fixedClock: Clock

  @BeforeEach
  fun setup() {
    jobExecutionRepository.deleteAll()
  }

  @Test
  fun `should save job execution`() {
    val startInstant = Instant.parse("2024-07-06T16:23:00Z")
    val endInstant = startInstant.plusSeconds(2)
    fixedClock = Clock.fixed(startInstant, ZoneId.of("UTC"))

    whenever(clock.instant()).thenAnswer { fixedClock.instant() }
    whenever(clock.zone).thenReturn(fixedClock.zone)

    val testJob =
      object : Job {
        override fun execute() {
          fixedClock = Clock.fixed(endInstant, ZoneId.of("UTC"))
        }

        override fun getName(): String = "TestJob"
      }

    jobExecutionService.executeJob(testJob)

    val savedExecution = jobExecutionRepository.findAll().firstOrNull()
    assertThat(savedExecution).isNotNull
    assertThat(savedExecution?.jobName).isEqualTo("TestJob")
    assertThat(savedExecution?.startTime).isEqualTo(startInstant)
    assertThat(savedExecution?.endTime).isEqualTo(endInstant)
    assertThat(savedExecution?.durationInMillis).isEqualTo(2000)
    assertThat(savedExecution?.status).isEqualTo(JobStatus.SUCCESS)
  }

  @Test
  fun `should handle job execution failure`() {
    val startInstant = Instant.parse("2024-07-06T16:23:00Z")
    val endInstant = startInstant.plusSeconds(1)
    fixedClock = Clock.fixed(startInstant, ZoneId.of("UTC"))

    whenever(clock.instant()).thenAnswer { fixedClock.instant() }
    whenever(clock.zone).thenReturn(fixedClock.zone)

    val failingJob =
      object : Job {
        override fun execute() {
          fixedClock = Clock.fixed(endInstant, ZoneId.of("UTC"))
          throw TestJobException("Test exception")
        }

        override fun getName(): String = "FailingJob"
      }

    try {
      jobExecutionService.executeJob(failingJob)
    } catch (ignored: TestJobException) {
      // Expected exception, do nothing
    }

    val savedExecution = jobExecutionRepository.findAll().firstOrNull()
    assertThat(savedExecution).isNotNull
    assertThat(savedExecution?.jobName).isEqualTo("FailingJob")
    assertThat(savedExecution?.startTime).isEqualTo(startInstant)
    assertThat(savedExecution?.endTime).isEqualTo(endInstant)
    assertThat(savedExecution?.durationInMillis).isEqualTo(1000)
    assertThat(savedExecution?.status).isEqualTo(JobStatus.FAILURE)
    assertThat(savedExecution?.message).isEqualTo("Test exception")
  }

  class TestJobException(
    message: String,
  ) : Exception(message)
}
