package ee.tenman.portfolio.service.pricing

import ee.tenman.portfolio.binance.BinanceService
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.PriceSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PriceSnapshotBackfillService(
  private val binanceService: BinanceService,
  private val priceSnapshotRepository: PriceSnapshotRepository,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun backfillFromBinance(instrument: Instrument) {
    log.info("Backfilling hourly snapshots for ${instrument.symbol} from Binance API")
    val hourlyPrices = binanceService.getHourlyPrices(instrument.symbol)
    hourlyPrices.forEach { (snapshotHour, price) ->
      priceSnapshotRepository.upsert(
        instrumentId = instrument.id,
        providerName = ProviderName.BINANCE.name,
        snapshotHour = snapshotHour,
        price = price,
      )
    }
    log.info("Backfilled ${hourlyPrices.size} snapshots for ${instrument.symbol}")
  }
}
