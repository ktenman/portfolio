package ee.tenman.portfolio.service

import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.domain.EtfPosition
import ee.tenman.portfolio.repository.EtfHoldingRepository
import ee.tenman.portfolio.repository.EtfPositionRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import ee.tenman.portfolio.wisdomtree.WisdomTreeHoldingsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class WisdomTreeUpdateService(
  private val wisdomTreeHoldingsService: WisdomTreeHoldingsService,
  private val instrumentRepository: InstrumentRepository,
  private val etfHoldingRepository: EtfHoldingRepository,
  private val etfPositionRepository: EtfPositionRepository,
  private val etfBreakdownService: EtfBreakdownService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun updateWtaiHoldings(): Map<String, Int> {
    log.info("Starting WTAI holdings update from WisdomTree")

    val wtaiInstrument = instrumentRepository.findBySymbol("WTAI:MIL:EUR")
    if (wtaiInstrument.isEmpty) {
      log.error("WTAI:MIL:EUR instrument not found in database")
      error("WTAI:MIL:EUR instrument not found")
    }
    val etf = wtaiInstrument.get()

    val today = LocalDate.now()
    val deletedCount = clearOldWtaiHoldings(etf.id, today)
    log.info("Deleted $deletedCount old WTAI holdings for $today")

    val holdings = wisdomTreeHoldingsService.fetchHoldings()
    log.info("Fetched ${holdings.size} holdings from WisdomTree")

    if (holdings.isEmpty()) {
      log.warn("No holdings fetched from WisdomTree")
      return mapOf("deleted" to deletedCount, "created" to 0)
    }

    var createdCount = 0
    var updatedCount = 0

    holdings.forEachIndexed { index, wisdomTreeHolding ->
      val tickerSymbol = wisdomTreeHoldingsService.extractTickerSymbol(wisdomTreeHolding.ticker)

      val holding = findOrCreateHolding(wisdomTreeHolding.name, tickerSymbol)

      val position =
        EtfPosition(
          etfInstrument = etf,
          holding = holding,
          snapshotDate = today,
          weightPercentage = wisdomTreeHolding.weight,
          positionRank = index + 1,
        )

      etfPositionRepository.save(position)
      createdCount++

      log.debug("Created position for ${holding.name} with weight ${wisdomTreeHolding.weight}%")
    }

    etfBreakdownService.evictBreakdownCache()

    log.info("Successfully updated WTAI holdings: deleted=$deletedCount, created=$createdCount")
    return mapOf("deleted" to deletedCount, "created" to createdCount, "updated" to updatedCount)
  }

  private fun clearOldWtaiHoldings(
    etfInstrumentId: Long,
    today: LocalDate,
  ): Int {
    val positions = etfPositionRepository.findByEtfInstrumentIdAndSnapshotDate(etfInstrumentId, today)
    if (positions.isNotEmpty()) {
      log.info("Deleting ${positions.size} existing WTAI positions for $today")
      etfPositionRepository.deleteAll(positions)
      etfPositionRepository.flush()
    }
    return positions.size
  }

  private fun findOrCreateHolding(
    name: String,
    ticker: String?,
  ): EtfHolding {
    val exactMatch = etfHoldingRepository.findByNameAndTicker(name, ticker)
    if (exactMatch.isPresent) {
      log.debug("Found exact match: name=$name, ticker=$ticker")
      return exactMatch.get()
    }

    if (ticker != null) {
      try {
        val byTicker = etfHoldingRepository.findByTicker(ticker)
        if (byTicker.isPresent) {
          log.debug("Found existing holding by ticker: $ticker (name may differ)")
          return byTicker.get()
        }
      } catch (e: Exception) {
        log.warn("Multiple holdings found for ticker $ticker, creating new entry for name=$name")
      }
    }

    log.info("Creating new holding: name=$name, ticker=$ticker")
    return etfHoldingRepository.save(EtfHolding(ticker = ticker, name = name, sector = null))
  }
}
