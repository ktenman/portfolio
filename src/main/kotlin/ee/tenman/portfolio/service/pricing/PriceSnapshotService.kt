package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceSnapshot
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.PriceSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class PriceSnapshotService(
  private val priceSnapshotRepository: PriceSnapshotRepository,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun saveSnapshot(
    instrument: Instrument,
    price: BigDecimal,
    providerName: ProviderName,
  ) {
    val snapshotHour = Instant.now(clock).truncatedTo(ChronoUnit.HOURS)
    if (isDuplicate(instrument.id, providerName, price)) {
      log.debug("Skipping duplicate snapshot for ${instrument.symbol} ($providerName)")
      return
    }
    priceSnapshotRepository.upsert(
      instrumentId = instrument.id,
      providerName = providerName.name,
      snapshotHour = snapshotHour,
      price = price,
    )
    log.debug("Saved price snapshot for ${instrument.symbol} ($providerName): $price")
  }

  @Transactional(readOnly = true)
  fun findClosestBefore(
    instrumentId: Long,
    targetHour: Instant,
  ): PriceSnapshot? = priceSnapshotRepository.findClosestBefore(instrumentId, targetHour)

  @Transactional
  fun deleteOlderThan(cutoff: Instant) {
    priceSnapshotRepository.deleteOlderThan(cutoff)
  }

  private fun isDuplicate(
    instrumentId: Long,
    providerName: ProviderName,
    price: BigDecimal,
  ): Boolean {
    val latest =
      priceSnapshotRepository.findLatestByInstrumentAndProvider(instrumentId, providerName.name)
      ?: return false
    return latest.price.compareTo(price) == 0
  }
}
