package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.common.orNotFound
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.GicsIndustry
import ee.tenman.portfolio.repository.EtfHoldingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class EtfHoldingIndustryService(
  private val etfHoldingRepository: EtfHoldingRepository,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional(readOnly = true)
  fun findUnclassifiedByIndustry(): List<EtfHolding> = etfHoldingRepository.findUnclassifiedIndustryHoldings(MAX_INDUSTRY_FETCH_ATTEMPTS)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateIndustry(
    holdingId: Long,
    industry: GicsIndustry,
    classifiedByModel: AiModel?,
  ) {
    val holding = etfHoldingRepository.findById(holdingId).orNotFound(holdingId)
    holding.industry = industry
    holding.industryClassifiedByModel = classifiedByModel
    etfHoldingRepository.save(holding)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun incrementIndustryFetchAttempts(holdingId: Long) {
    val holding = etfHoldingRepository.findById(holdingId).orNotFound(holdingId)
    holding.industryFetchAttempts++
    etfHoldingRepository.save(holding)
    log.info("Incremented industry fetch attempts for holding id=$holdingId to ${holding.industryFetchAttempts}")
  }

  companion object {
    const val MAX_INDUSTRY_FETCH_ATTEMPTS = 3
  }
}
