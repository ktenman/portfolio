package ee.tenman.portfolio.service.etf

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.domain.EtfHolding
import org.junit.jupiter.api.Test

class HoldingSimilarityPrefilterServiceTest {
  private val prefilter = HoldingSimilarityPrefilterService()

  @Test
  fun `should return empty list for single holding`() {
    val result = prefilter.findCandidatePairs(listOf(holding(1L, "Apple Inc", "AAPL")))

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should return empty list for empty input`() {
    val result = prefilter.findCandidatePairs(emptyList())

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should match holdings with same ticker`() {
    val holdings =
      listOf(
        holding(1L, "Apple Inc", "AAPL"),
        holding(2L, "Apple Computer", "AAPL"),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(1)
    expect(result[0].source).toEqual(MatchSource.TICKER)
    expect(result[0].firstHoldingId).toEqual(1L)
    expect(result[0].secondHoldingId).toEqual(2L)
  }

  @Test
  fun `should match holdings by case-insensitive ticker`() {
    val holdings =
      listOf(
        holding(1L, "Apple Inc", "aapl"),
        holding(2L, "Apple Computer", "AAPL"),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(1)
    expect(result[0].source).toEqual(MatchSource.TICKER)
  }

  @Test
  fun `should not match holdings with blank tickers`() {
    val holdings =
      listOf(
        holding(1L, "Foo Corp", ""),
        holding(2L, "Bar Corp", ""),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should match prefix-substring name overlap longer than 10 chars`() {
    val holdings =
      listOf(
        holding(1L, "Taiwan Semiconductor Manufac", null),
        holding(2L, "Taiwan Semiconductor Manufacturing", null),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(1)
    expect(result[0].source).toEqual(MatchSource.NAME_SIMILARITY)
  }

  @Test
  fun `should not match short prefix under 10 chars`() {
    val holdings =
      listOf(
        holding(1L, "Apple", null),
        holding(2L, "Apple Inc Corporation", null),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should not match different share classes via Jaccard`() {
    val holdings =
      listOf(
        holding(1L, "Alphabet Class A", null),
        holding(2L, "Alphabet Class C", null),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(0)
  }

  @Test
  fun `should match holdings with high token Jaccard overlap`() {
    val holdings =
      listOf(
        holding(1L, "Microsoft Corporation Class A", null),
        holding(2L, "Microsoft Corporation Class A Inc", null),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(1)
    expect(result[0].source).toEqual(MatchSource.NAME_SIMILARITY)
  }

  @Test
  fun `should normalize first and second ids by ascending`() {
    val holdings =
      listOf(
        holding(5L, "Foo Corporation", "ZZZ"),
        holding(2L, "Foo Corporation Ltd", "ZZZ"),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(1)
    expect(result[0].firstHoldingId).toEqual(2L)
    expect(result[0].secondHoldingId).toEqual(5L)
  }

  @Test
  fun `should not duplicate pairs when ticker match overlaps name match`() {
    val holdings =
      listOf(
        holding(1L, "Taiwan Semiconductor Manufac", "TSM"),
        holding(2L, "Taiwan Semiconductor Manufacturing", "TSM"),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(1)
    expect(result[0].source).toEqual(MatchSource.TICKER)
  }

  @Test
  fun `should pair all combinations within ticker group of three`() {
    val holdings =
      listOf(
        holding(1L, "Apple One", "AAPL"),
        holding(2L, "Apple Two", "AAPL"),
        holding(3L, "Apple Three", "AAPL"),
      )

    val result = prefilter.findCandidatePairs(holdings)

    expect(result).toHaveSize(3)
  }

  @Test
  fun `should treat similarity as symmetric`() {
    val similar = prefilter.isSimilarName("Apple Inc Corporation", "Apple Inc Corporation Ltd")
    val reversed = prefilter.isSimilarName("Apple Inc Corporation Ltd", "Apple Inc Corporation")

    expect(similar).toEqual(reversed)
  }

  private fun holding(
    id: Long,
    name: String,
    ticker: String?,
  ): EtfHolding {
    val h = EtfHolding(name = name, ticker = ticker)
    h.id = id
    return h
  }
}
