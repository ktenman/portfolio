package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.EtfHolding
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
      .findBySectorIsNullOrSectorEquals("")
      .mapNotNull { it.id }

  @Transactional(readOnly = true)
  fun findById(id: Long): EtfHolding? = etfHoldingRepository.findById(id).orElse(null)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateSector(
    holdingId: Long,
    sector: String,
  ) {
    etfHoldingRepository.findById(holdingId).ifPresent { holding ->
      holding.sector = sector
      etfHoldingRepository.save(holding)
    }
  }
}
