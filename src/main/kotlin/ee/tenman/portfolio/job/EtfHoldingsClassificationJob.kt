package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.IndustryClassificationService
import ee.tenman.portfolio.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class ClassificationResult(val success: Int = 0, val failure: Int = 0, val skipped: Int = 0)

@Component
@ConditionalOnProperty(
  prefix = "scheduling.jobs",
  name = ["etf-holdings-classification-enabled"],
  havingValue = "true",
  matchIfMissing = true,
)
class EtfHoldingsClassificationJob(
  private val etfHoldingRepository: EtfHoldingRepository,
  private val industryClassificationService: IndustryClassificationService,
  private val jobExecutionService: JobExecutionService,
) : Job {
  private val log = LoggerFactory.getLogger(javaClass)

  @Scheduled(initialDelay = 180000, fixedDelay = Long.MAX_VALUE)
  @Scheduled(cron = "\${scheduling.jobs.etf-holdings-classification-cron:0 0 3 * * *}")
  fun runJob() {
    log.info("Running ETF holdings classification job")
    jobExecutionService.executeJob(this)
    log.info("Completed ETF holdings classification job")
  }

  @Transactional
  override fun execute() {
    log.info("Starting ETF holdings classification")
    val holdings = etfHoldingRepository.findBySectorIsNullOrSectorEquals("")
    if (holdings.isEmpty()) {
      log.info("No unclassified holdings found")
      return
    }

    log.info("Found ${holdings.size} holdings without sector classification")
    val result = holdings.fold(ClassificationResult()) { acc, holding -> acc + classify(holding) }
    log.info("Classification complete. Success: ${result.success}, Failed: ${result.failure}, Skipped: ${result.skipped}, Total: ${holdings.size}")
  }

  private fun classify(holding: EtfHolding): ClassificationResult {
    if (holding.name.isBlank()) {
      log.warn("Skipping holding with blank name: id=${holding.id}")
      return ClassificationResult(skipped = 1)
    }

    return runCatching {
      log.info("Classifying: ${holding.name}")
      val sector = industryClassificationService.classifyCompany(holding.name)
      if (sector == null) {
        log.warn("Classification returned null for: ${holding.name}")
        return ClassificationResult(failure = 1)
      }

      holding.sector = sector.displayName
      etfHoldingRepository.save(holding)
      log.info("Successfully classified '${holding.name}' as '${sector.displayName}'")
      ClassificationResult(success = 1)
    }.getOrElse { e ->
      log.error("Error classifying holding: ${holding.name}", e)
      ClassificationResult(failure = 1)
    }
  }

  private operator fun ClassificationResult.plus(other: ClassificationResult) =
    ClassificationResult(success + other.success, failure + other.failure, skipped + other.skipped)
}
