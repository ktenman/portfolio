package ee.tenman.portfolio.service.diversification

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toHaveSize
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.dto.AllocationDto
import ee.tenman.portfolio.dto.ConcentrationDto
import ee.tenman.portfolio.dto.DiversificationCalculatorRequestDto
import ee.tenman.portfolio.dto.DiversificationCalculatorResponseDto
import ee.tenman.portfolio.dto.DiversificationHoldingDto
import ee.tenman.portfolio.dto.DiversificationHoldingSearchRequestDto
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class DiversificationHoldingSearchServiceTest {
  @Test
  fun `should include every holding for a blank query in descending weight order`() {
    val service = service(listOf(holding("Nestlé", "2"), holding("NVIDIA", "10"), holding("Tiny", "0")))
    val result = service.search(request(query = "  \t "))
    expect(result.content.map { it.name }).toContainExactly("NVIDIA", "Nestlé", "Tiny")
    expect(result.totalElements).toEqual(3L)
  }

  @Test
  fun `should match names without case or accents`() {
    val service = service(listOf(holding("Nestlé S.A.", "2"), holding("NVIDIA", "10")))
    val result = service.search(request(query = " NESTLE "))
    expect(result.content.map { it.name }).toContainExactly("Nestlé S.A.")
  }

  @Test
  fun `should collapse repeated spaces in the name and query`() {
    val service = service(listOf(holding("Crédit   Agricole S.A.", "2")))
    val result = service.search(request(query = "credit  agricole"))
    expect(result.content.map { it.name }).toContainExactly("Crédit   Agricole S.A.")
  }

  @Test
  fun `should match part of the ticker ignoring case`() {
    val service = service(listOf(holding("NVIDIA", "10", "NVDA"), holding("Apple", "2", "AAPL")))
    val result = service.search(request(query = "vda"))
    expect(result.content.map { it.name }).toContainExactly("NVIDIA")
  }

  @Test
  fun `should match the name of a holding without a ticker`() {
    val service = service(listOf(holding("Société Générale", "2")))
    val result = service.search(request(query = "generale"))
    expect(result.content.map { it.name }).toContainExactly("Société Générale")
  }

  @Test
  fun `should return no matches when neither the name nor the ticker contains the query`() {
    val service = service(listOf(holding("Apple", "2", "AAPL")))
    val result = service.search(request(query = "VWCE"))
    expect(result.content).toHaveSize(0)
    expect(result.totalElements).toEqual(0L)
  }

  @Test
  fun `should return the first one hundred holdings and the total count`() {
    val service = service((1..205).map { holding("Company $it", it.toString()) })
    val result = service.search(request())
    expect(result.content).toHaveSize(100)
    expect(result.content.first().name).toEqual("Company 205")
    expect(result.content.last().name).toEqual("Company 106")
    expect(result.totalElements).toEqual(205L)
    expect(result.size).toEqual(100)
  }

  @Test
  fun `should return the second page without repeating the first page`() {
    val service = service((1..205).map { holding("Company $it", it.toString()) })
    val result = service.search(request(page = 1))
    expect(result.content).toHaveSize(100)
    expect(result.content.first().name).toEqual("Company 105")
    expect(result.content.last().name).toEqual("Company 6")
    expect(result.totalElements).toEqual(205L)
  }

  @Test
  fun `should count matches before paging them`() {
    val service = service((1..120).map { holding("Match $it", it.toString()) } + holding("Other", "999"))
    val result = service.search(request(query = "match", page = 1))
    expect(result.content).toHaveSize(20)
    expect(result.content.first().name).toEqual("Match 20")
    expect(result.totalElements).toEqual(120L)
  }

  @Test
  fun `should preserve the total count on a page past the end`() {
    val service = service(listOf(holding("Apple", "2")))
    val result = service.search(request(page = 3))
    expect(result.content).toHaveSize(0)
    expect(result.totalElements).toEqual(1L)
  }

  @Test
  fun `should return an empty page without overflowing the largest page number`() {
    val service = service(listOf(holding("Apple", "2")))
    val result = service.search(request(page = Int.MAX_VALUE))
    expect(result.content).toHaveSize(0)
    expect(result.totalElements).toEqual(1L)
  }

  @Test
  fun `should order equal weights by name across page boundaries`() {
    val holdings = (1..105).reversed().map { holding("Company ${it.toString().padStart(3, '0')}", "1") }
    val service = service(holdings)
    val first = service.search(request())
    val second = service.search(request(page = 1))
    expect(first.content.last().name).toEqual("Company 100")
    expect(second.content.map { it.name }).toContainExactly("Company 101", "Company 102", "Company 103", "Company 104", "Company 105")
  }

  private fun request(
    query: String = "",
    page: Int = 0,
  ) = DiversificationHoldingSearchRequestDto(listOf(AllocationDto(7L, BigDecimal("100"))), query, page)

  private fun holding(
    name: String,
    percentage: String,
    ticker: String? = null,
  ) = DiversificationHoldingDto(name, ticker, BigDecimal(percentage), "VWCE")

  private fun service(holdings: List<DiversificationHoldingDto>): DiversificationHoldingSearchService {
    val calculator = mockk<DiversificationCalculatorService>()
    every { calculator.calculate(DiversificationCalculatorRequestDto(request().allocations)) } returns
      DiversificationCalculatorResponseDto(
        weightedTer = BigDecimal.ZERO,
        weightedAnnualReturn = BigDecimal.ZERO,
        totalUniqueHoldings = holdings.size,
        holdings = holdings,
        sectors = emptyList(),
        industries = emptyList(),
        countries = emptyList(),
        concentration = ConcentrationDto(BigDecimal.ZERO, null),
      )
    return DiversificationHoldingSearchService(calculator)
  }
}
