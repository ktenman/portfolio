package ee.tenman.portfolio.service.split

import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.Instrument
import ee.tenman.portfolio.domain.InstrumentSplit
import ee.tenman.portfolio.domain.ProviderName
import ee.tenman.portfolio.openrouter.OpenRouterClient
import ee.tenman.portfolio.repository.InstrumentSplitRepository
import ee.tenman.portfolio.repository.PortfolioTransactionRepository
import ee.tenman.portfolio.service.calculation.HoldingsCalculationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

@Service
class SplitCheckService(
  private val portfolioTransactionRepository: PortfolioTransactionRepository,
  private val holdingsCalculationService: HoldingsCalculationService,
  private val instrumentSplitRepository: InstrumentSplitRepository,
  private val openRouterClient: OpenRouterClient,
  private val clock: Clock,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val prompt = checkNotNull(javaClass.classLoader.getResource(PROMPT)) { "Missing resource $PROMPT" }.readText().trimEnd()

  fun check() {
    val today = LocalDate.now(clock)
    val held = held().associateBy { it.symbol }
    val answer =
      checkNotNull(openRouterClient.search(AiModel.GEMINI_4_ARGON, question(held.values, today))) { "OpenRouter gave no split answer" }
    val pending = instrumentSplitRepository.findPending(today).map { it.instrument.symbol }.toSet()
    val fresh = parseSplits(answer, held.keys, pending, today)
    fresh.forEach { store(held.getValue(it.symbol), it) }
    log.info("Split check asked about ${held.size} instruments and stored ${fresh.size} new splits")
  }

  private fun held(): List<Instrument> =
    portfolioTransactionRepository
      .findAllWithInstruments()
      .groupBy { it.instrument.id }
      .values
      .filter { holdingsCalculationService.calculateNetQuantity(it) > BigDecimal.ZERO }
      .map { it.first().instrument }
      .filterNot { it.providerName == ProviderName.BINANCE || it.isCash() }
      .sortedBy { it.symbol }

  private fun question(
    instruments: Collection<Instrument>,
    today: LocalDate,
  ): String =
    prompt
      .replace("{today}", today.toString())
      .replace("{instruments}", instruments.joinToString("\n") { "${it.symbol} | ${it.name}" })

  private fun store(
    instrument: Instrument,
    split: AnnouncedSplit,
  ) {
    val source = resolve(split.sourceUrl)
    instrumentSplitRepository.save(InstrumentSplit(instrument, split.ratio, split.effectiveDate, source, split.publishedDate))
    log.info("Stored the ${split.ratio} split of ${instrument.symbol} taking effect on ${split.effectiveDate}, announced at $source")
  }

  private fun resolve(address: String): String {
    if (!GROUNDING_REDIRECT.matches(address)) return address
    return runCatching {
      val request =
        HttpRequest
          .newBuilder(URI.create(address))
          .timeout(TIMEOUT)
          .build()
      HttpClient
        .newBuilder()
        .connectTimeout(TIMEOUT)
        .build()
        .use { it.send(request, HttpResponse.BodyHandlers.discarding()) }
        .headers()
        .firstValue("location")
        .orElse(address)
    }.onFailure { log.warn("Keeping $address as the split source because resolving it failed: ${it.message}") }
      .getOrDefault(address)
  }

  companion object {
    private const val PROMPT = "split/check-prompt.txt"
    private val GROUNDING_REDIRECT = Regex("https?://[^/?#]+/grounding-api-redirect/\\S+")
    private val TIMEOUT = Duration.ofSeconds(15)
  }
}
