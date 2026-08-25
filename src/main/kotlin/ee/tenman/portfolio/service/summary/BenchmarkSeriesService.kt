package ee.tenman.portfolio.service.summary

import ee.tenman.portfolio.domain.BenchmarkIndex
import ee.tenman.portfolio.domain.TimeRange
import ee.tenman.portfolio.dto.BenchmarkPointDto
import ee.tenman.portfolio.repository.DailyPriceRepository
import ee.tenman.portfolio.repository.InstrumentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class BenchmarkSeriesService(
  private val instrumentRepository: InstrumentRepository,
  private val dailyPriceRepository: DailyPriceRepository,
  private val clock: Clock,
) {
  companion object {
    private val BENCHMARK_SYMBOLS =
      mapOf(
        BenchmarkIndex.SP500 to listOf("VUAA:GER:EUR", "SPYL:GER:EUR"),
        BenchmarkIndex.WORLD to listOf("VWCE:GER:EUR", "SPPW:GER:EUR"),
      )
  }

  @Transactional(readOnly = true)
  fun getSeries(
    range: TimeRange,
    index: BenchmarkIndex,
  ): List<BenchmarkPointDto> {
    val instrument =
      BENCHMARK_SYMBOLS.getValue(index).firstNotNullOfOrNull { instrumentRepository.findBySymbol(it).orElse(null) }
        ?: return emptyList()
    val start = range.startDate(LocalDate.now(clock)) ?: LocalDate.EPOCH
    return dailyPriceRepository
      .findPricePointsByInstrumentAndEntryDateGreaterThanEqual(instrument, start)
      .distinctBy { it.entryDate }
      .map { BenchmarkPointDto(it.entryDate, it.closePrice) }
  }
}
