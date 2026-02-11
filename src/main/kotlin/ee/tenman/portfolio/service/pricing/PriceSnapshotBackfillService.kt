package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.Instrument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PriceSnapshotBackfillService(
  private val binanceService: BinanceService,
  private val priceSnapshotService: PriceSnapshotService,
  private val snapshotBackfillCacheService: SnapshotBackfillCacheService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun backfillFromBinance(instrument: Instrument) {
    if (snapshotBackfillCacheService.isBackfilled(instrument.id) == true) return
    if (priceSnapshotService.hasSnapshots(instrument.id, instrument.providerName)) {
      snapshotBackfillCacheService.markBackfilled(instrument.id)
      return
    }
    log.info("Backfilling hourly snapshots for ${instrument.symbol} from Binance API")
    val hourlyPrices = binanceService.getHourlyPrices(instrument.symbol)
    if (hourlyPrices.isEmpty()) return
    priceSnapshotService.saveSnapshots(instrument.id, instrument.providerName, hourlyPrices)
    snapshotBackfillCacheService.markBackfilled(instrument.id)
    log.info("Backfilled ${hourlyPrices.size} snapshots for ${instrument.symbol}")
  }
}
