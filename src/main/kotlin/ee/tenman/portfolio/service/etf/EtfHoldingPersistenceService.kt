package ee.tenman.portfolio.service.etf

import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.SectorSource
import ee.tenman.portfolio.repository.EtfHoldingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class EtfHoldingPersistenceService(
  private val etfHoldingRepository: EtfHoldingRepository,
) {
  @Transactional(readOnly = true)
  fun findUnclassifiedHoldingIds(): List<Long> =
    etfHoldingRepository
      .findUnclassifiedSectorHoldingsForCurrentPortfolio()
      .mapNotNull { it.id }

  @Transactional(readOnly = true)
  fun findUnclassifiedByCountryHoldingIds(): List<Long> =
    etfHoldingRepository
      .findUnclassifiedCountryHoldingsForCurrentPortfolio()
      .mapNotNull { it.id }

  @Transactional(readOnly = true)
  fun findById(id: Long): EtfHolding? = etfHoldingRepository.findById(id).orElse(null)

  @Transactional(readOnly = true)
  fun findEtfNamesForHolding(holdingId: Long): List<String> = etfHoldingRepository.findEtfNamesForHolding(holdingId)

  @Transactional(readOnly = true)
  fun findEtfNamesForHoldings(holdingIds: List<Long>): Map<Long, List<String>> {
    if (holdingIds.isEmpty()) return emptyMap()
    return etfHoldingRepository
      .findEtfNamesForHoldings(holdingIds)
      .groupBy({ (it[0] as Long) }, { it[1] as String })
      .mapValues { it.value.distinct() }
  }

  @Transactional(readOnly = true)
  fun findAllByIds(ids: List<Long>): List<EtfHolding> = etfHoldingRepository.findAllById(ids)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateSector(
    holdingId: Long,
    sector: String,
    classifiedByModel: AiModel? = null,
  ) {
    val holding =
      etfHoldingRepository.findById(holdingId).orElseThrow {
        IllegalStateException("EtfHolding not found with id=$holdingId")
      }
    holding.sector = sector
    holding.classifiedByModel = classifiedByModel
    holding.sectorSource = SectorSource.LLM
    etfHoldingRepository.save(holding)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateCountry(
    holdingId: Long,
    countryCode: String,
    countryName: String,
    classifiedByModel: AiModel? = null,
  ) {
    val holding =
      etfHoldingRepository.findById(holdingId).orElseThrow {
        IllegalStateException("EtfHolding not found with id=$holdingId")
      }
    holding.countryCode = countryCode
    holding.countryName = countryName
    holding.countryClassifiedByModel = classifiedByModel
    etfHoldingRepository.save(holding)
  }
}
