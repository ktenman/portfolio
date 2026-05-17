package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.configuration.IndustryClassificationProperties
import ee.tenman.portfolio.domain.AiModel
import ee.tenman.portfolio.domain.EtfHolding
import ee.tenman.portfolio.openrouter.OpenRouterClassificationResult
import ee.tenman.portfolio.openrouter.OpenRouterClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HoldingDeduplicationServiceTest {
  private val openRouterClient = mockk<OpenRouterClient>()
  private val properties = mockk<IndustryClassificationProperties>()
  private lateinit var service: HoldingDeduplicationService

  @BeforeEach
  fun setup() {
    service = HoldingDeduplicationService(openRouterClient, properties)
  }

  @Test
  fun `should return empty when no candidates given`() {
    val result = service.confirmDuplicates(emptyList())

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should auto-confirm ticker candidates without calling LLM`() {
    val candidates = listOf(tickerPair(1L, 2L, "Foo", "Foo Inc"))

    val result = service.confirmDuplicates(candidates)

    expect(result).toHaveSize(1)
    expect(result[0].source).toEqual(MatchSource.TICKER)
    verify(exactly = 0) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should call LLM for name similarity candidates`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithCascadingFallback(any(), AiModel.primarySectorModel(), any(), any()) } returns
      OpenRouterClassificationResult(content = "1. YES", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    val candidates = listOf(namePair(1L, 2L, "Taiwan Semi Manufac", "Taiwan Semi Manufacturing"))

    val result = service.confirmDuplicates(candidates)

    expect(result).toHaveSize(1)
    verify(exactly = 1) { openRouterClient.classifyWithCascadingFallback(any(), AiModel.primarySectorModel(), any(), any()) }
  }

  @Test
  fun `should drop NO responses from LLM`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "1. NO\n2. YES", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    val candidates =
      listOf(
        namePair(1L, 2L, "Alphabet Class A", "Alphabet Class C"),
        namePair(3L, 4L, "Apple Inc Holdings", "Apple Inc Holdings Ltd"),
      )

    val result = service.confirmDuplicates(candidates)

    expect(result).toHaveSize(1)
    expect(result[0].firstHoldingId).toEqual(3L)
  }

  @Test
  fun `should not call LLM when classification disabled`() {
    every { properties.enabled } returns false
    val candidates = listOf(namePair(1L, 2L, "A long name here", "A long name there"))

    val result = service.confirmDuplicates(candidates)

    expect(result).toHaveSize(0)
    verify(exactly = 0) { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) }
  }

  @Test
  fun `should mix auto-confirmed ticker pairs with LLM-confirmed name pairs`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns
      OpenRouterClassificationResult(content = "1. YES", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    val candidates =
      listOf(
        tickerPair(1L, 2L, "Foo", "Foo Inc"),
        namePair(3L, 4L, "Bar Inc Corp Ltd", "Bar Inc Corp Ltd Plus"),
      )

    val result = service.confirmDuplicates(candidates)

    expect(result).toHaveSize(2)
  }

  @Test
  fun `should return empty when LLM response is null`() {
    every { properties.enabled } returns true
    every { openRouterClient.classifyWithCascadingFallback(any(), any(), any(), any()) } returns null
    val candidates = listOf(namePair(1L, 2L, "Apple Inc Holdings", "Apple Inc Holdings Ltd"))

    val result = service.confirmDuplicates(candidates)

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should pick longest name as canonical and link the rest`() {
    val confirmed = listOf(namePair(1L, 2L, "Apple", "Apple Incorporated"))
    val holdings = listOf(holding(1L, "Apple"), holding(2L, "Apple Incorporated"))

    val links = service.resolveCanonicalLinks(confirmed, holdings)

    expect(links.size).toEqual(1)
    expect(links[1L]).toEqual(2L)
  }

  @Test
  fun `should merge transitive clusters across pairs`() {
    val confirmed =
      listOf(
        namePair(1L, 2L, "Apple", "Apple Inc"),
        namePair(2L, 3L, "Apple Inc", "Apple Incorporated"),
      )
    val holdings =
      listOf(
        holding(1L, "Apple"),
        holding(2L, "Apple Inc"),
        holding(3L, "Apple Incorporated"),
      )

    val links = service.resolveCanonicalLinks(confirmed, holdings)

    expect(links.size).toEqual(2)
    expect(links[1L]).toEqual(3L)
    expect(links[2L]).toEqual(3L)
  }

  @Test
  fun `should break ties by lower id when names equal length`() {
    val confirmed = listOf(namePair(5L, 7L, "Same Name", "Same Name"))
    val holdings = listOf(holding(5L, "Same Name"), holding(7L, "Same Name"))

    val links = service.resolveCanonicalLinks(confirmed, holdings)

    expect(links.size).toEqual(1)
    expect(links[7L]).toEqual(5L)
  }

  @Test
  fun `should return empty when confirmed list is empty`() {
    val links = service.resolveCanonicalLinks(emptyList(), listOf(holding(1L, "Apple")))

    expect(links.size).toEqual(0)
  }

  @Test
  fun `should skip cluster members missing from holdings map`() {
    val confirmed = listOf(namePair(1L, 99L, "Apple", "Apple Inc"))
    val holdings = listOf(holding(1L, "Apple"))

    val links = service.resolveCanonicalLinks(confirmed, holdings)

    expect(links.size).toEqual(0)
  }

  @Test
  fun `should include share class warning in batch prompt`() {
    every { properties.enabled } returns true
    val promptCapture = slot<String>()
    every {
      openRouterClient.classifyWithCascadingFallback(capture(promptCapture), any(), any(), any())
    } returns OpenRouterClassificationResult(content = "1. NO", model = AiModel.GEMINI_3_FLASH_PREVIEW)
    val candidates = listOf(namePair(1L, 2L, "Alphabet Class A", "Alphabet Class C"))

    service.confirmDuplicates(candidates)

    expect(promptCapture.captured).toContain("share classes are NOT the same")
  }

  private fun namePair(
    firstId: Long,
    secondId: Long,
    firstName: String,
    secondName: String,
  ): HoldingMatchCandidate =
    HoldingMatchCandidate(
      firstHoldingId = firstId,
      firstName = firstName,
      firstTicker = null,
      secondHoldingId = secondId,
      secondName = secondName,
      secondTicker = null,
      source = MatchSource.NAME_SIMILARITY,
    )

  private fun tickerPair(
    firstId: Long,
    secondId: Long,
    firstName: String,
    secondName: String,
  ): HoldingMatchCandidate =
    HoldingMatchCandidate(
      firstHoldingId = firstId,
      firstName = firstName,
      firstTicker = "T",
      secondHoldingId = secondId,
      secondName = secondName,
      secondTicker = "T",
      source = MatchSource.TICKER,
    )

  private fun holding(
    id: Long,
    name: String,
  ): EtfHolding {
    val h = EtfHolding(name = name)
    h.id = id
    return h
  }
}
