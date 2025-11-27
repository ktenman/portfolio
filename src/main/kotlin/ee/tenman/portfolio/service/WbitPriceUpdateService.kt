package ee.tenman.portfolio.service

import ee.tenman.portfolio.binance.BinanceClient
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class WbitPriceUpdateService(
  private val instrumentRepository: InstrumentRepository,
  private val dailyPriceRepository: DailyPriceRepository,
  private val instrumentService: InstrumentService,
  private val binanceClient: BinanceClient,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  companion object {
    const val WBIT_SYMBOL = "WBIT:GER:EUR"
    const val BTCEUR_SYMBOL = "BTCEUR"
  }

  @Transactional
  fun updateWbitPrice() {
    val priceData = getWbitPriceData() ?: return
    val previousBtcPrice = getBtcPriceAtTime(priceData.lastUpdateTime)
    val currentBtcPrice = getCurrentBtcPrice()
    if (previousBtcPrice == BigDecimal.ZERO || currentBtcPrice == BigDecimal.ZERO) {
      log.warn("Could not retrieve BTC prices: previous=$previousBtcPrice, current=$currentBtcPrice")
      return
    }
    val coefficient = currentBtcPrice.divide(previousBtcPrice, 10, RoundingMode.HALF_UP)
    val newWbitPrice = priceData.currentPrice.multiply(coefficient).setScale(2, RoundingMode.HALF_UP)
    log.info("Updating WBIT price: {} -> {} (BTC coefficient: {})", priceData.currentPrice, newWbitPrice, coefficient)
    priceData.instrument.currentPrice = newWbitPrice
    instrumentService.saveInstrument(priceData.instrument)
  }

  private fun getWbitPriceData(): WbitPriceData? {
    val wbitInstrument =
      instrumentRepository.findBySymbol(WBIT_SYMBOL).orElseGet {
      log.warn("WBIT instrument not found")
      null
    } ?: return null
    val currentWbitPrice =
      wbitInstrument.currentPrice ?: run {
      log.warn("WBIT current price is null")
      return null
    }
    val lastDailyPrice =
      findLastWbitDailyPrice(wbitInstrument) ?: run {
      log.warn("No daily price found for WBIT")
      return null
    }
    return WbitPriceData(wbitInstrument, currentWbitPrice, lastDailyPrice.updatedAt)
  }

  private data class WbitPriceData(
    val instrument: Instrument,
    val currentPrice: BigDecimal,
    val lastUpdateTime: Instant,
  )

  private fun findLastWbitDailyPrice(instrument: Instrument): DailyPrice? =
    dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
      instrument,
      LocalDate.now(clock).minusYears(10),
      LocalDate.now(clock),
    )

  private fun getBtcPriceAtTime(timestamp: Instant): BigDecimal {
    val startTime = timestamp.toEpochMilli()
    val endTime = startTime + 60000
    return runCatching {
      binanceClient
        .getKlines(BTCEUR_SYMBOL, "1m", startTime, endTime, 1)
        .firstOrNull()
        ?.get(4)
        ?.toBigDecimal() ?: BigDecimal.ZERO
    }.getOrElse {
      log.error("Failed to get BTC price at {}: {}", timestamp, it.message)
      BigDecimal.ZERO
    }
  }

  private fun getCurrentBtcPrice(): BigDecimal {
    val now = Instant.now(clock)
    val startTime = now.minusSeconds(120).toEpochMilli()
    return runCatching {
      binanceClient
        .getKlines(BTCEUR_SYMBOL, "1m", startTime, now.toEpochMilli(), 1)
        .lastOrNull()
        ?.get(4)
        ?.toBigDecimal() ?: BigDecimal.ZERO
    }.getOrElse {
      log.error("Failed to get current BTC price: {}", it.message)
      BigDecimal.ZERO
    }
  }
}
