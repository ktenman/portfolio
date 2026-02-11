package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.PriceSnapshot
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.PriceSnapshotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.SortedMap

@Service
class PriceSnapshotService(
  private val priceSnapshotRepository: PriceSnapshotRepository,
  private val clock: Clock,
  private val deletionService: PriceSnapshotDeletionService,
) {
  @Transactional
  fun saveSnapshot(
    instrument: Instrument,
    price: BigDecimal,
    providerName: ProviderName,
  ) {
    val snapshotHour = Instant.now(clock).truncatedTo(ChronoUnit.HOURS)
    priceSnapshotRepository.upsert(
      instrumentId = instrument.id,
      providerName = providerName.name,
      snapshotHour = snapshotHour,
      price = price,
    )
  }

  @Transactional
  fun saveSnapshots(
    instrumentId: Long,
    providerName: ProviderName,
    hourlyPrices: SortedMap<Instant, BigDecimal>,
  ) {
    if (hourlyPrices.isEmpty()) return
    val entries = hourlyPrices.entries.toList()
    priceSnapshotRepository.upsertBatch(
      instrumentIds = Array(entries.size) { instrumentId },
      providerNames = Array(entries.size) { providerName.name },
      snapshotHours = entries.map { it.key }.toTypedArray(),
      prices = entries.map { it.value }.toTypedArray(),
      size = entries.size,
    )
  }

  @Transactional(readOnly = true)
  fun hasSnapshots(
    instrumentId: Long,
    providerName: ProviderName,
  ): Boolean = priceSnapshotRepository.existsByInstrumentIdAndProviderName(instrumentId, providerName.name)

  @Transactional(readOnly = true)
  fun findClosestAtOrBefore(
    instrumentId: Long,
    providerName: ProviderName,
    earliestHour: Instant,
    targetHour: Instant,
  ): PriceSnapshot? = priceSnapshotRepository.findClosestAtOrBefore(instrumentId, providerName, earliestHour, targetHour)

  fun deleteOlderThan(cutoff: Instant) {
    do {
      val deleted = deletionService.deleteBatch(cutoff)
    } while (deleted == PriceSnapshotDeletionService.DELETE_BATCH_SIZE)
  }
}
