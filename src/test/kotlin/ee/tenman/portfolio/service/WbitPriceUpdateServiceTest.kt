package ee.tenman.portfolio.service

import ee.tenman.portfolio.binance.BinanceClient
import ee.tenman.portfolio.domain.DailyPrice
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

class WbitPriceUpdateServiceTest {
  private val instrumentRepository = mockk<InstrumentRepository>()
  private val dailyPriceRepository = mockk<DailyPriceRepository>()
  private val instrumentService = mockk<InstrumentService>()
  private val binanceClient = mockk<BinanceClient>()
  private val fixedInstant = Instant.parse("2025-11-27T10:00:00Z")
  private val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
  private lateinit var service: WbitPriceUpdateService
  private lateinit var wbitInstrument: Instrument

  @BeforeEach
  fun setUp() {
    service =
      WbitPriceUpdateService(
      instrumentRepository,
      dailyPriceRepository,
      instrumentService,
      binanceClient,
      clock,
    )
    wbitInstrument =
      Instrument(
      symbol = "WBIT:GER:EUR",
      name = "WisdomTree Physical Bitcoin",
      category = "ETF",
      baseCurrency = "EUR",
      currentPrice = BigDecimal("100.00"),
      providerName = ProviderName.LIGHTYEAR,
    ).apply { id = 1L }
  }

  @Test
  fun `should update WBIT price based on BTC coefficient`() {
    val lastUpdateTime = Instant.parse("2025-11-27T08:00:00Z")
    val dailyPrice = createDailyPrice(wbitInstrument, lastUpdateTime)
    every { instrumentRepository.findBySymbol("WBIT:GER:EUR") } returns Optional.of(wbitInstrument)
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        wbitInstrument,
        LocalDate.now(clock).minusYears(10),
        LocalDate.now(clock),
      )
    } returns dailyPrice
    every {
      binanceClient.getKlines("BTCEUR", "1m", lastUpdateTime.toEpochMilli(), lastUpdateTime.toEpochMilli() + 60000, 1)
    } returns listOf(listOf("0", "1", "2", "3", "90000.00", "5"))
    every {
      binanceClient.getKlines("BTCEUR", "1m", fixedInstant.minusSeconds(120).toEpochMilli(), fixedInstant.toEpochMilli(), 1)
    } returns listOf(listOf("0", "1", "2", "3", "91800.00", "5"))
    every { instrumentService.saveInstrument(any()) } returns wbitInstrument

    service.updateWbitPrice()

    verify { instrumentService.saveInstrument(match { it.currentPrice?.toDouble() == 102.00 }) }
  }

  @Test
  fun `should skip update when WBIT instrument not found`() {
    every { instrumentRepository.findBySymbol("WBIT:GER:EUR") } returns Optional.empty()

    service.updateWbitPrice()

    verify(exactly = 0) { instrumentService.saveInstrument(any()) }
  }

  @Test
  fun `should skip update when current WBIT price is null`() {
    wbitInstrument.currentPrice = null
    every { instrumentRepository.findBySymbol("WBIT:GER:EUR") } returns Optional.of(wbitInstrument)

    service.updateWbitPrice()

    verify(exactly = 0) { instrumentService.saveInstrument(any()) }
  }

  @Test
  fun `should skip update when no daily price exists`() {
    every { instrumentRepository.findBySymbol("WBIT:GER:EUR") } returns Optional.of(wbitInstrument)
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        wbitInstrument,
        LocalDate.now(clock).minusYears(10),
        LocalDate.now(clock),
      )
    } returns null

    service.updateWbitPrice()

    verify(exactly = 0) { instrumentService.saveInstrument(any()) }
  }

  @Test
  fun `should skip update when previous BTC price retrieval fails`() {
    val lastUpdateTime = Instant.parse("2025-11-27T08:00:00Z")
    val dailyPrice = createDailyPrice(wbitInstrument, lastUpdateTime)
    every { instrumentRepository.findBySymbol("WBIT:GER:EUR") } returns Optional.of(wbitInstrument)
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        wbitInstrument,
        LocalDate.now(clock).minusYears(10),
        LocalDate.now(clock),
      )
    } returns dailyPrice
    every {
      binanceClient.getKlines("BTCEUR", "1m", lastUpdateTime.toEpochMilli(), lastUpdateTime.toEpochMilli() + 60000, 1)
    } returns emptyList()

    service.updateWbitPrice()

    verify(exactly = 0) { instrumentService.saveInstrument(any()) }
  }

  @Test
  fun `should skip update when current BTC price retrieval fails`() {
    val lastUpdateTime = Instant.parse("2025-11-27T08:00:00Z")
    val dailyPrice = createDailyPrice(wbitInstrument, lastUpdateTime)
    every { instrumentRepository.findBySymbol("WBIT:GER:EUR") } returns Optional.of(wbitInstrument)
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        wbitInstrument,
        LocalDate.now(clock).minusYears(10),
        LocalDate.now(clock),
      )
    } returns dailyPrice
    every {
      binanceClient.getKlines("BTCEUR", "1m", lastUpdateTime.toEpochMilli(), lastUpdateTime.toEpochMilli() + 60000, 1)
    } returns listOf(listOf("0", "1", "2", "3", "90000.00", "5"))
    every {
      binanceClient.getKlines("BTCEUR", "1m", fixedInstant.minusSeconds(120).toEpochMilli(), fixedInstant.toEpochMilli(), 1)
    } returns emptyList()

    service.updateWbitPrice()

    verify(exactly = 0) { instrumentService.saveInstrument(any()) }
  }

  @Test
  fun `should handle BTC price decrease correctly`() {
    val lastUpdateTime = Instant.parse("2025-11-27T08:00:00Z")
    val dailyPrice = createDailyPrice(wbitInstrument, lastUpdateTime)
    every { instrumentRepository.findBySymbol("WBIT:GER:EUR") } returns Optional.of(wbitInstrument)
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        wbitInstrument,
        LocalDate.now(clock).minusYears(10),
        LocalDate.now(clock),
      )
    } returns dailyPrice
    every {
      binanceClient.getKlines("BTCEUR", "1m", lastUpdateTime.toEpochMilli(), lastUpdateTime.toEpochMilli() + 60000, 1)
    } returns listOf(listOf("0", "1", "2", "3", "100000.00", "5"))
    every {
      binanceClient.getKlines("BTCEUR", "1m", fixedInstant.minusSeconds(120).toEpochMilli(), fixedInstant.toEpochMilli(), 1)
    } returns listOf(listOf("0", "1", "2", "3", "95000.00", "5"))
    every { instrumentService.saveInstrument(any()) } returns wbitInstrument

    service.updateWbitPrice()

    verify { instrumentService.saveInstrument(match { it.currentPrice?.toDouble() == 95.00 }) }
  }

  @Test
  fun `should handle Binance API exception gracefully`() {
    val lastUpdateTime = Instant.parse("2025-11-27T08:00:00Z")
    val dailyPrice = createDailyPrice(wbitInstrument, lastUpdateTime)
    every { instrumentRepository.findBySymbol("WBIT:GER:EUR") } returns Optional.of(wbitInstrument)
    every {
      dailyPriceRepository.findFirstByInstrumentAndEntryDateBetweenOrderByEntryDateDesc(
        wbitInstrument,
        LocalDate.now(clock).minusYears(10),
        LocalDate.now(clock),
      )
    } returns dailyPrice
    every {
      binanceClient.getKlines("BTCEUR", "1m", lastUpdateTime.toEpochMilli(), lastUpdateTime.toEpochMilli() + 60000, 1)
    } throws RuntimeException("API error")

    service.updateWbitPrice()

    verify(exactly = 0) { instrumentService.saveInstrument(any()) }
  }

  private fun createDailyPrice(
    instrument: Instrument,
    updatedAt: Instant,
  ): DailyPrice =
    DailyPrice(
      instrument = instrument,
      entryDate = LocalDate.now(clock),
      providerName = ProviderName.LIGHTYEAR,
      openPrice = BigDecimal("100.00"),
      highPrice = BigDecimal("105.00"),
      lowPrice = BigDecimal("99.00"),
      closePrice = BigDecimal("102.00"),
      volume = 1000L,
    ).apply {
      this.updatedAt = updatedAt
    }
}
