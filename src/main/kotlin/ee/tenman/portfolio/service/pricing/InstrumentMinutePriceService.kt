package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.repository.InstrumentMinutePriceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class InstrumentMinutePriceService(
  private val instrumentMinutePriceRepository: InstrumentMinutePriceRepository,
  private val clock: Clock,
) {
  @Transactional
  fun record() {
    val capturedAt = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES)
    instrumentMinutePriceRepository.insertChangedPrices(capturedAt)
  }

  @Transactional
  fun deleteOlderThan(cutoff: Instant) {
    instrumentMinutePriceRepository.deleteOlderThan(cutoff)
  }
}
