package ee.tenman.portfolio.tuleva

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import ee.tenman.portfolio.blackrock.BlackRockFund
import ee.tenman.portfolio.blackrock.BlackRockHoldingsService
import ee.tenman.portfolio.domain.FundAllocation
import ee.tenman.portfolio.dto.HoldingData
import ee.tenman.portfolio.repository.FundAllocationRepository
import ee.tenman.portfolio.service.etf.HoldingAggregationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import java.math.BigDecimal
import java.time.LocalDate

class TulevaHoldingsServiceTest {
  private val client = mockk<TulevaReportClient>()
  private val repository = mockk<FundAllocationRepository>()
  private val blackRock = mockk<BlackRockHoldingsService>()
  private val service = TulevaHoldingsService(client, repository, blackRock, HoldingAggregationService(), "https://tuleva.ee")

  @Test
  fun `should scale proxy holdings by fund weight and merge the same company across proxies`() {
    givenAllocations(allocation("IE00BFG1TM61", "60"), allocation("IE00BKPTWY98", "40"))
    every { blackRock.fetchHoldings(BlackRockFund.SAWD) } returns listOf(holding("Apple Inc", "50"), holding("Taiwan Semiconductor", "49"))
    every { blackRock.fetchHoldings(BlackRockFund.SAEM) } returns listOf(holding("TAIWAN  SEMICONDUCTOR", "99"))
    val holdings = service.lookThrough()
    expect(holdings.map { "${it.rank}. ${it.name}=${it.weight.stripTrailingZeros().toPlainString()}" })
      .toContainExactly("1. Taiwan Semiconductor=69", "2. Apple Inc=30")
  }

  @Test
  fun `should reject an allocation to a fund without a look-through proxy`() {
    givenAllocations(allocation("LU0000000000", "100"))
    expect { service.lookThrough() }.toThrow<IllegalArgumentException>().messageToContain("LU0000000000")
  }

  @Test
  fun `should reject a proxy whose equity weights do not cover the fund`() {
    givenAllocations(allocation("IE00BKPTWY98", "100"))
    every { blackRock.fetchHoldings(BlackRockFund.SAEM) } returns listOf(holding("Tencent Holdings", "12"))
    expect { service.lookThrough() }.toThrow<IllegalArgumentException>().messageToContain("SAEM")
  }

  @Test
  fun `should not download a report that is already imported`() {
    every { repository.findSourceUrls(TulevaHoldingsService.SYMBOL) } returns setOf(KNOWN)
    every { client.listMedia(1) } returns page(1, KNOWN)
    service.importReports()
    verify(exactly = 0) { client.download(any()) }
  }

  @Test
  fun `should reject a report hosted outside the Tuleva site`() {
    every { repository.findSourceUrls(TulevaHoldingsService.SYMBOL) } returns emptySet()
    every { client.listMedia(1) } returns page(1, "https://evil.example/aruanne.pdf")
    expect { service.importReports() }.toThrow<IllegalArgumentException>().messageToContain("evil.example")
  }

  @Test
  fun `should reject a report served over plain http`() {
    every { repository.findSourceUrls(TulevaHoldingsService.SYMBOL) } returns emptySet()
    every { client.listMedia(1) } returns page(1, "http://tuleva.ee/aruanne.pdf")
    expect { service.importReports() }.toThrow<IllegalArgumentException>().messageToContain("http://tuleva.ee/aruanne.pdf")
  }

  @Test
  fun `should not request a page past the last one when the listing fills a page exactly`() {
    val urls = (1..MEDIA_PAGE_SIZE).map { "https://tuleva.ee/aruanne-$it.pdf" }
    every { repository.findSourceUrls(TulevaHoldingsService.SYMBOL) } returns urls.toSet()
    every { client.listMedia(1) } returns page(1, *urls.toTypedArray())
    service.importReports()
    verify(exactly = 0) { client.listMedia(2) }
  }

  @Test
  fun `should import reports listed on later pages`() {
    every { repository.findSourceUrls(TulevaHoldingsService.SYMBOL) } returns setOf(KNOWN)
    every { client.listMedia(1) } returns page(2, KNOWN)
    every { client.listMedia(2) } returns page(2, "https://evil.example/aruanne.pdf")
    expect { service.importReports() }.toThrow<IllegalArgumentException>().messageToContain("evil.example")
  }

  private fun page(
    total: Int,
    vararg urls: String,
  ): ResponseEntity<List<TulevaMedia>> = ResponseEntity.ok().header(TOTAL_PAGES_HEADER, "$total").body(urls.map(::TulevaMedia))

  private fun givenAllocations(vararg allocations: FundAllocation) {
    every { repository.findFirstByFundIsinOrderByAsOfDateDescIdDesc(TulevaHoldingsService.SYMBOL) } returns allocations.first()
    every { repository.findBySourceUrl(KNOWN) } returns allocations.toList()
  }

  private fun allocation(
    isin: String,
    weight: String,
  ) = FundAllocation(
    fundIsin = TulevaHoldingsService.SYMBOL,
    asOfDate = LocalDate.of(2026, 8, 31),
    sourceUrl = KNOWN,
    reportedTotal = BigDecimal(100),
    underlyingIsin = isin,
    underlyingName = "Fond $isin",
    weight = BigDecimal(weight),
  )

  private fun holding(
    name: String,
    weight: String,
  ) = HoldingData(name = name, ticker = null, sector = null, weight = BigDecimal(weight), rank = 1)

  companion object {
    private const val KNOWN = "https://tuleva.ee/wp-content/uploads/2026/09/aruanne-2026-08.pdf"
  }
}
