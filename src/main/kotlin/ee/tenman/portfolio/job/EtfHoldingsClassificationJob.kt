package ee.tenman.portfolio.job

import ee.tenman.portfolio.domain.IndustrySector
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.service.IndustryClassificationService
import ee.tenman.portfolio.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

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

    val unclassifiedHoldings = etfHoldingRepository.findBySectorIsNullOrSectorEquals("")

    if (unclassifiedHoldings.isEmpty()) {
      log.info("No unclassified holdings found")
      return
    }

    log.info("Found ${unclassifiedHoldings.size} holdings without sector classification")

    var successCount = 0
    var failureCount = 0
    var skippedCount = 0

    unclassifiedHoldings.forEach { holding ->
      try {
        val companyName = holding.name

        if (companyName.isBlank()) {
          log.warn("Skipping holding with blank name: id=${holding.id}")
          skippedCount++
          return@forEach
        }

        log.info("Classifying: $companyName")

        val sector: IndustrySector? = industryClassificationService.classifyCompany(companyName)

        if (sector != null) {
          holding.sector = sector.displayName
          etfHoldingRepository.save(holding)
          log.info("Successfully classified '$companyName' as '${sector.displayName}'")
          successCount++
        } else {
          log.warn("Classification returned null for: $companyName")
          failureCount++
        }
      } catch (e: Exception) {
        log.error("Error classifying holding: ${holding.name}", e)
        failureCount++
      }
    }

    log.info(
      "Classification complete. Success: $successCount, Failed: $failureCount, Skipped: $skippedCount, Total: ${unclassifiedHoldings.size}",
    )
  }
}
